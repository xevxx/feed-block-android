package local.feedcheck.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FeedCheckService extends AccessibilityService {
    static final String ACTIVE = "active";
    static final String LAST = "last";
    static final String OBSERVED = "observed";
    static final String BLOCKING = FeedProfile.SNAPCHAT.preference;
    static final int NO_WINDOW = -1;
    private static final long CONFIRM_DELAY = 180;
    private static final long OVERLAY_CHECK_DELAY = 250;
    private static final int NO_REEL_INDEX = -1;
    private static final String INSTAGRAM_REELS_PAGER_ID =
            FeedProfile.INSTAGRAM.packageName + ":id/" + FeedProfile.INSTAGRAM_REELS_PAGER;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable confirm = this::confirmScreen;
    private final Runnable checkOverlay = this::checkOverlayWindow;
    private final Runnable settingsAccessExpired = this::recheckSettings;
    private final SharedPreferences.OnSharedPreferenceChangeListener eventPackagesPreference = (prefs, key) -> {
        if (ProtectedSettings.ENABLED.equals(key)) {
            updateEventPackages(prefs);
            return;
        }
        for (FeedProfile profile : FeedProfile.SUPPORTED) {
            if (profile != FeedProfile.SNAPCHAT && profile.preference.equals(key)) {
                if (profile == FeedProfile.INSTAGRAM) {
                    inlineReelsWindow = NO_WINDOW;
                }
                updateEventPackages(prefs);
                return;
            }
        }
    };
    private Screen pending;
    private Screen covered;
    private View overlay;
    private int receivedEvents;
    private int lastSample = NO_WINDOW;
    private int shownOverlays;
    private int inlineReelsWindow = NO_WINDOW;
    private int firstReelIndex = NO_REEL_INDEX;
    private boolean moreReelsBlocked;
    private boolean overlayRejected;
    private FeedAudio feedAudio;
    private ProtectedSettings protectedSettings;

    private static final class Screen {
        final int window;
        final FeedProfile profile;
        final FeedProfile.Rule feed;
        final int snapchatMarkers;
        final ProtectedSettings.Page settingsPage;
        final FeedProfile.Shortcut shortcut;

        Screen(int window, FeedProfile profile, FeedProfile.Rule feed, int snapchatMarkers,
                ProtectedSettings.Page settingsPage, FeedProfile.Shortcut shortcut) {
            this.window = window;
            this.profile = profile;
            this.feed = feed;
            this.snapchatMarkers = snapchatMarkers;
            this.settingsPage = settingsPage;
            this.shortcut = shortcut;
        }

        boolean samePage(Screen other) {
            return other != null && window == other.window && profile == other.profile
                    && settingsPage == other.settingsPage;
        }

        boolean muteMedia() {
            return feed != null && feed.muteMedia;
        }

        int heading() {
            if (settingsPage != null) {
                return R.string.parent_access_required;
            }
            return feed.heading;
        }
    }

    static SharedPreferences state(Context context) {
        return context.getSharedPreferences("feed_check", Context.MODE_PRIVATE);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        feedAudio = new FeedAudio(this);
        feedAudio.restore();
        protectedSettings = new ProtectedSettings(this);
        SharedPreferences preferences = state(this);
        preferences.registerOnSharedPreferenceChangeListener(eventPackagesPreference);
        updateEventPackages(preferences);
    }

    private void updateEventPackages(SharedPreferences preferences) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            List<String> packages = new ArrayList<>();
            for (FeedProfile profile : FeedProfile.SUPPORTED) {
                if (profile == FeedProfile.SNAPCHAT || profile.enabled(preferences)) {
                    packages.add(profile.packageName);
                }
            }
            if (preferences.getBoolean(ProtectedSettings.ENABLED, false)) {
                packages.add(ProtectedSettings.PACKAGE);
            }
            info.packageNames = packages.toArray(new String[0]);
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
        FeedProfile profile = FeedProfile.forPackage(source);
        if (profile == null) {
            return;
        }
        boolean snapchat = profile == FeedProfile.SNAPCHAT;
        if (snapchat) {
            receivedEvents++;
        }
        boolean blocking = profile.enabled(state);
        boolean checking = snapchat && state.getBoolean(ACTIVE, false);
        if (!blocking && !checking) {
            return;
        }
        Screen screen = readScreen(currentRoot(), event);
        if (screen != null && screen.settingsPage != null) {
            return;
        }
        if (snapchat) {
            lastSample = screen != null && screen.profile == profile ? screen.snapchatMarkers : NO_WINDOW;
            if (checking && lastSample != NO_WINDOW && lastSample != state.getInt(LAST, NO_WINDOW)) {
                int observed = state.getInt(OBSERVED, 0) | lastSample;
                state.edit().putInt(LAST, lastSample).putInt(OBSERVED, observed).apply();
            }
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
        return readScreen(currentRoot(), null);
    }

    private AccessibilityNodeInfo currentRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            String owner = String.valueOf(active.getPackageName());
            if (FeedProfile.forPackage(owner) != null || ProtectedSettings.PACKAGE.equals(owner)) {
                return active;
            }
            active.recycle();
            if (!getPackageName().equals(owner)) {
                return null;
            }
        }
        // A tap on the overlay can make it the active window without taking input focus.
        return overlay == null ? null : focusedRoot();
    }

    private AccessibilityNodeInfo focusedRoot() {
        List<AccessibilityWindowInfo> windows = getWindows();
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (window.isFocused() && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    return window.getRoot();
                }
            }
            return null;
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                window.recycle();
            }
        }
    }

    private Screen readScreen(AccessibilityNodeInfo root, AccessibilityEvent event) {
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
                return page == null ? null : new Screen(root.getWindowId(), null, null, 0, page, null);
            }
            FeedProfile profile = FeedProfile.forPackage(owner);
            if (profile == null || (profile != FeedProfile.SNAPCHAT && !profile.enabled(state(this)))) {
                return null;
            }
            Set<String> views = profile.sample(viewId -> visibleId(root, viewId, profile.requireViewBounds, false));
            FeedProfile.Rule feed = profile.match(views,
                    viewId -> visibleId(root, viewId, profile.requireViewBounds, true));
            if (profile == FeedProfile.INSTAGRAM) {
                feed = matchInstagramReels(root.getWindowId(), views, feed, event);
            }
            FeedProfile.Shortcut shortcut = null;
            if (feed != null && feed.shortcut != null) {
                AccessibilityNodeInfo control = findShortcut(root, profile, feed.shortcut);
                if (control != null) {
                    shortcut = feed.shortcut;
                    control.recycle();
                }
            }
            int snapchatMarkers = profile == FeedProfile.SNAPCHAT ? FeedProfile.SnapchatMarker.mask(views) : 0;
            return new Screen(root.getWindowId(), profile, feed, snapchatMarkers, null, shortcut);
        } finally {
            root.recycle();
        }
    }

    private FeedProfile.Rule matchInstagramReels(int window, Set<String> views, FeedProfile.Rule feed,
            AccessibilityEvent event) {
        if (feed != null || !FeedProfile.INSTAGRAM_MORE_REELS.matches(views)) {
            inlineReelsWindow = NO_WINDOW;
            return feed;
        }
        if (inlineReelsWindow != window) {
            inlineReelsWindow = window;
            firstReelIndex = NO_REEL_INDEX;
            moreReelsBlocked = false;
        }
        if (!moreReelsBlocked && event != null && event.getWindowId() == window
                && event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && event.getFromIndex() >= 0 && event.getFromIndex() == event.getToIndex()) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                try {
                    if (INSTAGRAM_REELS_PAGER_ID.equals(source.getViewIdResourceName())) {
                        int index = event.getFromIndex();
                        if (firstReelIndex == NO_REEL_INDEX) {
                            firstReelIndex = index;
                        } else if (firstReelIndex != index) {
                            moreReelsBlocked = true;
                        }
                    }
                } finally {
                    source.recycle();
                }
            }
        }
        return moreReelsBlocked ? FeedProfile.INSTAGRAM_MORE_REELS : null;
    }

    private boolean restricted(Screen screen) {
        return screen != null && (screen.settingsPage != null
                || (screen.feed != null && screen.profile.enabled(state(this))));
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
            if (target.heading() != covered.heading() || target.shortcut != covered.shortcut) {
                updateOverlayContent(overlay, target);
            }
            covered = target;
            feedAudio.update(target.muteMedia());
            handler.postDelayed(checkOverlay, OVERLAY_CHECK_DELAY);
        } else {
            hideOverlay();
        }
    }

    private void showOverlay(Screen target) {
        Context themed = new ContextThemeWrapper(this, R.style.FeedCheckTheme);
        View panel = LayoutInflater.from(themed).inflate(R.layout.block_screen, null);
        if (target.settingsPage == null) {
            ImageView artwork = panel.findViewById(R.id.block_screen_artwork);
            artwork.setImageResource(BlockScreenIcon.selected(state(this)).drawable);
        }
        updateOverlayContent(panel, target);
        if (target.settingsPage != null) {
            TextView detail = panel.findViewById(R.id.block_screen_detail);
            detail.setText(target.settingsPage.label);
            detail.setVisibility(View.VISIBLE);
        }
        setExitAction(panel.findViewById(R.id.block_screen_back), () -> performGlobalAction(GLOBAL_ACTION_BACK));
        setExitAction(panel.findViewById(R.id.block_screen_shortcut), this::openShortcut);
        Button exit = panel.findViewById(R.id.block_screen_exit);
        if (target.settingsPage == null) {
            exit.setText(getString(R.string.leave_app, getString(target.profile.appName)));
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
        layout.setTitle(getString(R.string.app_name));
        try {
            getSystemService(WindowManager.class).addView(panel, layout);
            overlay = panel;
            covered = target;
            shownOverlays++;
            overlayRejected = false;
            feedAudio.update(target.muteMedia());
            handler.postDelayed(checkOverlay, OVERLAY_CHECK_DELAY);
        } catch (WindowManager.BadTokenException ignored) {
            // Android may revoke the service between confirmation and adding the window.
            overlayRejected = true;
        }
    }

    private void updateOverlayContent(View panel, Screen screen) {
        TextView heading = panel.findViewById(R.id.block_screen_title);
        heading.setText(screen.heading());
        Button shortcutButton = panel.findViewById(R.id.block_screen_shortcut);
        shortcutButton.setVisibility(screen.shortcut != null ? View.VISIBLE : View.GONE);
        if (screen.shortcut != null) {
            shortcutButton.setText(screen.shortcut.label);
        }
        panel.findViewById(R.id.block_screen_back).setBackgroundResource(
                screen.shortcut != null ? R.drawable.button_secondary : R.drawable.button_primary);
    }

    private void openShortcut() {
        Screen target = covered;
        if (target == null || target.shortcut == null) {
            return;
        }
        AccessibilityNodeInfo root = currentRoot();
        if (root != null) {
            try {
                if (root.getWindowId() == target.window
                        && target.profile.packageName.equals(String.valueOf(root.getPackageName()))) {
                    AccessibilityNodeInfo control = findShortcut(root, target.profile, target.shortcut);
                    if (control != null) {
                        try {
                            if (control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                return;
                            }
                        } finally {
                            control.recycle();
                        }
                    }
                }
            } finally {
                root.recycle();
            }
        }
        Toast.makeText(this, target.shortcut.unavailable, Toast.LENGTH_SHORT).show();
    }

    private static AccessibilityNodeInfo findShortcut(AccessibilityNodeInfo root, FeedProfile profile,
            FeedProfile.Shortcut shortcut) {
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId(
                profile.packageName + ":id/" + shortcut.view);
        if (matches.size() == 1) {
            AccessibilityNodeInfo control = matches.get(0);
            if (control.isVisibleToUser() && control.isEnabled() && control.isClickable() && !control.isSelected()
                    && profile.packageName.equals(String.valueOf(control.getPackageName()))) {
                return control;
            }
        }
        for (AccessibilityNodeInfo match : matches) {
            match.recycle();
        }
        return null;
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
            feedAudio.restore();
            getSystemService(WindowManager.class).removeViewImmediate(overlay);
            overlay = null;
        }
    }

    private static boolean visibleId(AccessibilityNodeInfo root, String viewId, boolean requireViewBounds,
            boolean requireSelection) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        Rect bounds = requireViewBounds ? new Rect() : null;
        boolean visible = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (!visible && node.isVisibleToUser() && (!requireSelection || node.isSelected())) {
                if (bounds != null) {
                    node.getBoundsInScreen(bounds);
                }
                visible = bounds == null || !bounds.isEmpty();
            }
            node.recycle();
        }
        return visible;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("Feed Block blocking: " + state(this).getBoolean(BLOCKING, false));
        writer.println("Feed Block Snapchat events: " + receivedEvents);
        writer.println("Feed Block markers sampled: " + (lastSample != NO_WINDOW));
        if (lastSample != NO_WINDOW) {
            for (FeedProfile.SnapchatMarker marker : FeedProfile.SnapchatMarker.values()) {
                writer.println("Feed Block marker " + marker.name() + ": " + ((lastSample & marker.bit) != 0));
            }
        }
        writer.println("Feed Block overlays shown: " + shownOverlays);
        writer.println("Feed Block overlay visible: " + (overlay != null));
        writer.println("Feed Block covered app: " + (covered != null && covered.profile != null
                ? covered.profile.packageName : "none"));
        writer.println("Feed Block overlay rejected: " + overlayRejected);
        writer.println("Feed Block settings guard: " + state(this).getBoolean(ProtectedSettings.ENABLED, false));
        writer.println("Feed Block protected Settings page: "
                + (covered != null && covered.settingsPage != null ? covered.settingsPage.name() : "none"));
        if (feedAudio != null) {
            writer.println("Feed Block media muted: " + feedAudio.isMuted());
            writer.println("Feed Block owns media mute: " + feedAudio.ownsMute());
        }
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(confirm);
        handler.removeCallbacks(settingsAccessExpired);
        pending = null;
        inlineReelsWindow = NO_WINDOW;
        hideOverlay();
    }

    @Override
    public void onDestroy() {
        state(this).unregisterOnSharedPreferenceChangeListener(eventPackagesPreference);
        onInterrupt();
        super.onDestroy();
    }
}
