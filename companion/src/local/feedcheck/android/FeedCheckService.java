package local.feedcheck.android;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public final class FeedCheckService extends AccessibilityService {
    static final String SNAPCHAT = "com.snapchat.android";
    static final String ACTIVE = "active";
    static final String LAST = "last";
    static final String OBSERVED = "observed";
    static final int NO_WINDOW = -1;

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
    }

    static SharedPreferences state(Context context) {
        return context.getSharedPreferences("feed_check", Context.MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences state = state(this);
        if (!state.getBoolean(ACTIVE, false) || !SNAPCHAT.equals(String.valueOf(event.getPackageName()))) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        try {
            if (!SNAPCHAT.equals(String.valueOf(root.getPackageName()))) {
                return;
            }
            int last = 0;
            for (Marker marker : Marker.values()) {
                if (visibleId(root, marker.viewId)) {
                    last |= marker.bit;
                }
            }
            if (last != state.getInt(LAST, NO_WINDOW)) {
                int observed = state.getInt(OBSERVED, 0) | last;
                state.edit().putInt(LAST, last).putInt(OBSERVED, observed).apply();
            }
        } finally {
            root.recycle();
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
    }
}
