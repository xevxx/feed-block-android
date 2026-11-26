package local.feedcheck.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.TextView;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

public final class FeedCheckService extends AccessibilityService {
    static final String SNAPCHAT = "com.snapchat.android";
    static final String ACTIVE = "active";
    static final String LAST = "last";
    static final String OBSERVED = "observed";
    static final String BLOCKING = "blocking";
    static final int NO_WINDOW = -1;
    private static final long CONFIRM_DELAY = 180;
    private static final long OVERLAY_CHECK_DELAY = 250;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable confirm = this::confirmScreen;
    private final Runnable checkOverlay = this::checkOverlayWindow;
    private final Runnable settingsAccessExpired = this::recheckSettings;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsPreference = (prefs, key) -> {
        if (ProtectedSettings.ENABLED.equals(key)) {
            updateEventPackages(prefs.getBoolean(key, false));
        }
    };
    private Screen pending;
    private Screen covered;
    private View overlay;
    private int receivedEvents;
    private int lastSample = NO_WINDOW;
    private int shownOverlays;
    private boolean overlayRejected;
    private SpotlightAudio spotlightAudio;
    private ProtectedSettings protectedSettings;

    enum Marker {
        SPOTLIGHT("Spotlight", "spotlight_container"),
        DISCOVER_CARD("Discover card", "df_large_story"),
        CHAT_LIST("Chat message list", "chat_message_list"),
        CHAT_INPUT("Chat input", "chat_input_text_field");

        final String label;
        final String viewId;
        final int bit;

        Marker(String label, String viewId) {
            this.label = label;
            this.viewId = SNAPCHAT + ":id/" + viewId;
            this.bit = 1 << ordinal();
        }

        static boolean identifiesFeed(int markers) {
            int feed = SPOTLIGHT.bit | DISCOVER_CARD.bit;
            int chat = CHAT_LIST.bit | CHAT_INPUT.bit;
            return (markers & feed) != 0 && (markers & chat) == 0;
        }
    }

    private static final class Screen {
        final int window;
        final int markers;
        final ProtectedSettings.Page settingsPage;

        Screen(int window, int markers, ProtectedSettings.Page settingsPage) {
            this.window = window;
            this.markers = markers;
            this.settingsPage = settingsPage;
        }

        boolean samePage(Screen other) {
            return other != null && window == other.window && settingsPage == other.settingsPage;
        }
    }

    static SharedPreferences state(Context context) {
        return context.getSharedPreferences("feed_check", Context.MODE_PRIVATE);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        spotlightAudio = new SpotlightAudio(this);
        spotlightAudio.restore();
        protectedSettings = new ProtectedSettings(this);
        SharedPreferences preferences = state(this);
        preferences.registerOnSharedPreferenceChangeListener(settingsPreference);
        updateEventPackages(preferences.getBoolean(ProtectedSettings.ENABLED, false));
    }

    private void updateEventPackages(boolean watchSettings) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.packageNames = watchSettings ? new String[] {SNAPCHAT, ProtectedSettings.PACKAGE}
                    : new String[] {SNAPCHAT};
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String source = String.valueOf(event.getPackageName());
        SharedPreferences state = state(this);
        if (ProtectedSettings.PACKAGE.equals(source)) {
            if (protectedSettings != null && state.getBoolean(ProtectedSettings.ENABLED, false)) {
                protectedSettings.observe(event);
                long remaining = ProtectedSettings.accessRemaining();
                handler.removeCallbacks(settingsAccessExpired);
                if (remaining > 0) {
                    handler.postDelayed(settingsAccessExpired, remaining);
                }
                considerScreen(currentScreen());
            }
            return;
        }
        if (!SNAPCHAT.equals(source)) {
            return;
        }
        receivedEvents++;
        boolean blocking = state.getBoolean(BLOCKING, false);
        boolean checking = state.getBoolean(ACTIVE, false);
        if (!blocking && !checking) {
            return;
        }
        Screen screen = currentScreen();
        if (screen != null && screen.settingsPage != null) {
            return;
        }
        lastSample = screen == null ? NO_WINDOW : screen.markers;
        if (checking && screen != null && screen.markers != state.getInt(LAST, NO_WINDOW)) {
            int observed = state.getInt(OBSERVED, 0) | screen.markers;
            state.edit().putInt(LAST, screen.markers).putInt(OBSERVED, observed).apply();
        }
        if (blocking) {
            considerScreen(screen);
        }
    }

    private void recheckSettings() {
        Screen screen = currentScreen();
        if (screen != null && screen.settingsPage != null) {
            considerScreen(screen);
        }
    }

    private Screen currentScreen() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            String owner = String.valueOf(active.getPackageName());
            if (SNAPCHAT.equals(owner) || ProtectedSettings.PACKAGE.equals(owner)) {
                return readScreen(active);
            }
            active.recycle();
            if (!getPackageName().equals(owner)) {
                return null;
            }
        }
        // A tap on the overlay can make it the active window without taking input focus.
        return overlay == null ? null : focusedScreen();
    }

    private Screen focusedScreen() {
        List<AccessibilityWindowInfo> windows = getWindows();
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.isFocused() && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    return readScreen(window.getRoot());
                }
            }
            return null;
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                window.recycle();
            }
        }
    }

    private Screen readScreen(AccessibilityNodeInfo root) {
        if (root == null) {
            return null;
        }
        try {
            String owner = String.valueOf(root.getPackageName());
            if (ProtectedSettings.PACKAGE.equals(owner)) {
                if (protectedSettings == null || !state(this).getBoolean(ProtectedSettings.ENABLED, false)
                        || ProtectedSettings.accessRemaining() > 0) {
                    return null;
                }
                ProtectedSettings.Page page = protectedSettings.match(root);
                return page == null ? null : new Screen(root.getWindowId(), 0, page);
            }
            if (!SNAPCHAT.equals(owner)) {
                return null;
            }
            int markers = 0;
            for (Marker marker : Marker.values()) {
                if (visibleId(root, marker.viewId)) {
                    markers |= marker.bit;
                }
            }
            return new Screen(root.getWindowId(), markers, null);
        } finally {
            root.recycle();
        }
    }

    private boolean restricted(Screen screen) {
        return screen != null && (screen.settingsPage != null
                || (state(this).getBoolean(BLOCKING, false) && Marker.identifiesFeed(screen.markers)));
    }

    private void considerScreen(Screen screen) {
        Screen target = restricted(screen) ? screen : null;
        if (covered != null && !covered.samePage(target)) {
            hideOverlay();
        }
        if (pending != null && !pending.samePage(target)) {
            handler.removeCallbacks(confirm);
            pending = null;
        }
        if (target != null && covered == null && pending == null) {
            pending = target;
            handler.postDelayed(confirm, CONFIRM_DELAY);
        }
    }

    private Screen confirmCurrentPage(Screen expected) {
        if (expected == null) {
            return null;
        }
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            return null;
        }
        Screen current = currentScreen();
        return expected.samePage(current) && restricted(current) ? current : null;
    }

    private void confirmScreen() {
        Screen target = confirmCurrentPage(pending);
        pending = null;
        if (target != null) {
            showOverlay(target);
        }
    }

    private void checkOverlayWindow() {
        Screen target = confirmCurrentPage(covered);
        if (target != null) {
            spotlightAudio.update((target.markers & Marker.SPOTLIGHT.bit) != 0);
            handler.postDelayed(checkOverlay, OVERLAY_CHECK_DELAY);
        } else {
            hideOverlay();
        }
    }

    private void showOverlay(Screen target) {
        Context themed = new ContextThemeWrapper(this, R.style.FeedCheckTheme);
        View panel = LayoutInflater.from(themed).inflate(R.layout.block_screen, null);
        TextView heading = panel.findViewById(R.id.block_screen_title);
        heading.setText(target.settingsPage == null ? R.string.feed_blocked : R.string.parent_access_required);
        if (target.settingsPage != null) {
            TextView detail = panel.findViewById(R.id.block_screen_detail);
            detail.setText(target.settingsPage.label);
            detail.setVisibility(View.VISIBLE);
        }
        setExitAction(panel.findViewById(R.id.block_screen_back), () -> performGlobalAction(GLOBAL_ACTION_BACK));
        Button exit = panel.findViewById(R.id.block_screen_exit);
        if (target.settingsPage == null) {
            exit.setText(R.string.leave_snapchat);
            setExitAction(exit, () -> performGlobalAction(GLOBAL_ACTION_HOME));
        } else {
            exit.setText(R.string.open_feed_check);
            setExitAction(exit, () -> startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }

        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
        layout.setTitle("Feed Check");
        try {
            getSystemService(WindowManager.class).addView(panel, layout);
            overlay = panel;
            covered = target;
            shownOverlays++;
            overlayRejected = false;
            spotlightAudio.update((target.markers & Marker.SPOTLIGHT.bit) != 0);
            handler.postDelayed(checkOverlay, OVERLAY_CHECK_DELAY);
        } catch (WindowManager.BadTokenException ignored) {
            // Android may revoke the service between confirmation and adding the window.
            overlayRejected = true;
        }
    }

    private void setExitAction(Button button, Runnable action) {
        button.setOnClickListener(view -> {
            if (confirmCurrentPage(covered) != null) {
                action.run();
            } else {
                hideOverlay();
            }
        });
    }

    private void hideOverlay() {
        handler.removeCallbacks(checkOverlay);
        covered = null;
        if (overlay != null) {
            spotlightAudio.restore();
            getSystemService(WindowManager.class).removeViewImmediate(overlay);
            overlay = null;
        }
    }

    private boolean visibleId(AccessibilityNodeInfo root, String viewId) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        boolean visible = false;
        for (AccessibilityNodeInfo node : nodes) {
            visible |= node.isVisibleToUser();
            node.recycle();
        }
        return visible;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("Feed Check blocking: " + state(this).getBoolean(BLOCKING, false));
        writer.println("Feed Check Snapchat events: " + receivedEvents);
        writer.println("Feed Check markers sampled: " + (lastSample != NO_WINDOW));
        if (lastSample != NO_WINDOW) {
            for (Marker marker : Marker.values()) {
                writer.println("Feed Check marker " + marker.name() + ": " + ((lastSample & marker.bit) != 0));
            }
        }
        writer.println("Feed Check overlays shown: " + shownOverlays);
        writer.println("Feed Check overlay visible: " + (overlay != null));
        writer.println("Feed Check overlay rejected: " + overlayRejected);
        writer.println("Feed Check settings guard: " + state(this).getBoolean(ProtectedSettings.ENABLED, false));
        writer.println("Feed Check protected Settings page: "
                + (covered != null && covered.settingsPage != null ? covered.settingsPage.name() : "none"));
        if (spotlightAudio != null) {
            writer.println("Feed Check media muted: " + spotlightAudio.isMuted());
            writer.println("Feed Check owns media mute: " + spotlightAudio.ownsMute());
        }
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(confirm);
        handler.removeCallbacks(settingsAccessExpired);
        pending = null;
        hideOverlay();
    }

    @Override
    public void onDestroy() {
        state(this).unregisterOnSharedPreferenceChangeListener(settingsPreference);
        onInterrupt();
        super.onDestroy();
    }
}
