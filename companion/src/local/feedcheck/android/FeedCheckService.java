package local.feedcheck.android;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private final Runnable confirm = this::confirmFeed;
    private final Runnable checkOverlay = this::checkOverlayWindow;
    private int pendingWindow = NO_WINDOW;
    private int coveredWindow = NO_WINDOW;
    private LinearLayout overlay;

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

        Screen(int window, int markers) {
            this.window = window;
            this.markers = markers;
        }
    }

    static SharedPreferences state(Context context) {
        return context.getSharedPreferences("feed_check", Context.MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!SNAPCHAT.equals(String.valueOf(event.getPackageName()))) {
            return;
        }
        SharedPreferences state = state(this);
        boolean blocking = state.getBoolean(BLOCKING, false);
        boolean checking = state.getBoolean(ACTIVE, false);
        if (!blocking && !checking) {
            return;
        }
        Screen screen = blocking ? focusedSnapchatScreen() : readScreen(getRootInActiveWindow());
        if (checking && screen != null && screen.markers != state.getInt(LAST, NO_WINDOW)) {
            int observed = state.getInt(OBSERVED, 0) | screen.markers;
            state.edit().putInt(LAST, screen.markers).putInt(OBSERVED, observed).apply();
        }
        if (blocking) {
            considerFeed(screen);
        }
    }

    private Screen focusedSnapchatScreen() {
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
            if (!SNAPCHAT.equals(String.valueOf(root.getPackageName()))) {
                return null;
            }
            int markers = 0;
            for (Marker marker : Marker.values()) {
                if (visibleId(root, marker.viewId)) {
                    markers |= marker.bit;
                }
            }
            return new Screen(root.getWindowId(), markers);
        } finally {
            root.recycle();
        }
    }

    private void considerFeed(Screen screen) {
        int feedWindow = screen != null && Marker.identifiesFeed(screen.markers) ? screen.window : NO_WINDOW;
        if (coveredWindow != feedWindow) {
            hideOverlay();
        }
        if (pendingWindow != feedWindow) {
            handler.removeCallbacks(confirm);
            pendingWindow = NO_WINDOW;
        }
        if (feedWindow != NO_WINDOW && coveredWindow == NO_WINDOW && pendingWindow == NO_WINDOW) {
            pendingWindow = feedWindow;
            handler.postDelayed(confirm, CONFIRM_DELAY);
        }
    }

    private boolean isCurrentFeed(int window) {
        if (window == NO_WINDOW || !state(this).getBoolean(BLOCKING, false)) {
            return false;
        }
        Screen current = focusedSnapchatScreen();
        return current != null && current.window == window && Marker.identifiesFeed(current.markers);
    }

    private void confirmFeed() {
        int expected = pendingWindow;
        pendingWindow = NO_WINDOW;
        if (isCurrentFeed(expected)) {
            showOverlay(expected);
        }
    }

    private void checkOverlayWindow() {
        if (isCurrentFeed(coveredWindow)) {
            handler.postDelayed(checkOverlay, OVERLAY_CHECK_DELAY);
        } else {
            hideOverlay();
        }
    }

    private void showOverlay(int window) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.rgb(244, 247, 245));
        panel.setClickable(true);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);

        TextView heading = new TextView(this);
        heading.setText("Feed blocked");
        heading.setTextSize(28);
        heading.setTextColor(Color.rgb(25, 43, 34));
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading);
        addExitButton(panel, "Go back", GLOBAL_ACTION_BACK);
        addExitButton(panel, "Leave Snapchat", GLOBAL_ACTION_HOME);

        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
        layout.setTitle("Feed Check");
        try {
            getSystemService(WindowManager.class).addView(panel, layout);
            overlay = panel;
            coveredWindow = window;
            handler.postDelayed(checkOverlay, OVERLAY_CHECK_DELAY);
        } catch (WindowManager.BadTokenException ignored) {
            // Android may revoke the service between confirmation and adding the window.
        }
    }

    private void addExitButton(LinearLayout panel, String label, int action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> {
            if (isCurrentFeed(coveredWindow)) {
                performGlobalAction(action);
            } else {
                hideOverlay();
            }
        });
        panel.addView(button);
    }

    private void hideOverlay() {
        handler.removeCallbacks(checkOverlay);
        coveredWindow = NO_WINDOW;
        if (overlay != null) {
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
    public void onInterrupt() {
        handler.removeCallbacks(confirm);
        pendingWindow = NO_WINDOW;
        hideOverlay();
    }

    @Override
    public void onDestroy() {
        onInterrupt();
        super.onDestroy();
    }
}
