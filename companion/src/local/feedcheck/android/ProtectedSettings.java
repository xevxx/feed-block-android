package local.feedcheck.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

final class ProtectedSettings {
    static final String PACKAGE = "com.android.settings";
    static final String ENABLED = "protect_feed_check_settings";
    private static final String ID = PACKAGE + ":id/";
    private static final String SPA_ACTIVITY = PACKAGE + ".spa.SpaActivity";
    private static final long ACCESS_DURATION = 60_000L;
    private static final int APP_INFO_TITLE = 1;
    private static final int APP_LABEL = 2;
    private static final int UNINSTALL_ACTION = 4;
    private static final int FORCE_STOP_ACTION = 8;
    private static final int SPA_MATCH = APP_INFO_TITLE | APP_LABEL | UNINSTALL_ACTION | FORCE_STOP_ACTION;
    private static long allowedUntil;

    enum Page {
        ACCESSIBILITY(R.string.protected_accessibility),
        APP_INFO(R.string.protected_app_info);

        final int label;

        Page(int label) {
            this.label = label;
        }
    }

    private final String appLabel;
    private final String primarySwitchLabel;
    private final String masterSwitchLabel;
    private final String appInfoTitle;
    private final String forceStopLabel;
    private final String uninstallLabel;
    private int spaWindow = -1;

    ProtectedSettings(Context context) {
        PackageManager packages = context.getPackageManager();
        appLabel = context.getApplicationInfo().loadLabel(packages).toString();
        Resources settings = null;
        try {
            settings = packages.getResourcesForApplication(PACKAGE);
        } catch (PackageManager.NameNotFoundException ignored) {
            // Unknown Settings builds have no protected Accessibility signature.
        }
        primarySwitchLabel = setting(settings, "accessibility_service_primary_switch_title", appLabel);
        masterSwitchLabel = setting(settings, "accessibility_service_master_switch_title", appLabel);
        appInfoTitle = setting(settings, "application_info_label");
        forceStopLabel = setting(settings, "force_stop");
        uninstallLabel = setting(settings, "uninstall_text");
    }

    static void allowTemporarily() {
        allowedUntil = SystemClock.elapsedRealtime() + ACCESS_DURATION;
    }

    static void endTemporaryAccess() {
        allowedUntil = 0;
    }

    static long accessRemaining() {
        return Math.max(0, allowedUntil - SystemClock.elapsedRealtime());
    }

    void observe(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && SPA_ACTIVITY.equals(String.valueOf(event.getClassName()))) {
            spaWindow = event.getWindowId();
        }
    }

    Page match(AccessibilityNodeInfo root) {
        if (!PACKAGE.equals(String.valueOf(root.getPackageName()))) {
            return null;
        }
        if (has(root, ID + "entity_header_title", appLabel)
                && ((has(root, ID + "button2", null) && has(root, ID + "button3", null))
                || (has(root, ID + "control_buttons_panel", null) && has(root, ID + "right_button", null)))) {
            return Page.APP_INFO;
        }
        if (hasInAppSnippet(root)
                && has(root, ID + "control_buttons_panel", null)
                && has(root, ID + "left_button", null)
                && has(root, ID + "right_button", null)) {
            return Page.APP_INFO;
        }
        if (spaWindow == root.getWindowId() && appInfoTitle != null && forceStopLabel != null
                && uninstallLabel != null && spaEvidence(root, 0, new int[] {160}) == SPA_MATCH) {
            return Page.APP_INFO;
        }
        return hasServiceSwitch(root, primarySwitchLabel) || hasServiceSwitch(root, masterSwitchLabel)
                || (has(root, "android:id/action_bar_title", appLabel) && hasNamedSwitchRow(root))
                ? Page.ACCESSIBILITY : null;
    }

    private boolean hasInAppSnippet(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> snippets = root.findAccessibilityNodeInfosByViewId(ID + "app_snippet");
        boolean match = false;
        for (AccessibilityNodeInfo snippet : snippets) {
            if (snippet.isVisibleToUser() && has(snippet, "android:id/title", appLabel)) {
                match = true;
            }
            snippet.recycle();
        }
        return match;
    }

    private boolean hasNamedSwitchRow(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> titles = root.findAccessibilityNodeInfosByViewId("android:id/title");
        boolean match = false;
        for (AccessibilityNodeInfo title : titles) {
            if (title.isVisibleToUser() && appLabel.equals(String.valueOf(title.getText()))) {
                match |= hasNearbySwitch(title.getParent(), 3);
            }
            title.recycle();
        }
        return match;
    }

    private static boolean hasNearbySwitch(AccessibilityNodeInfo parent, int remaining) {
        if (parent == null) {
            return false;
        }
        try {
            if (parent.isScrollable() || !parent.isVisibleToUser()) {
                return false;
            }
            return has(parent, "android:id/switch_widget", null)
                    || (remaining > 1 && hasNearbySwitch(parent.getParent(), remaining - 1));
        } finally {
            parent.recycle();
        }
    }

    private int spaEvidence(AccessibilityNodeInfo node, int matches, int[] remaining) {
        if (remaining[0]-- <= 0 || !node.isVisibleToUser()) {
            return matches;
        }
        if ("android.widget.TextView".equals(String.valueOf(node.getClassName()))) {
            String label = String.valueOf(node.getText());
            if (appInfoTitle.equals(label)) {
                matches |= APP_INFO_TITLE;
            } else if (appLabel.equals(label)) {
                matches |= APP_LABEL;
            } else if (uninstallLabel.equals(label) && isAction(node)) {
                matches |= UNINSTALL_ACTION;
            } else if (forceStopLabel.equals(label) && isAction(node)) {
                matches |= FORCE_STOP_ACTION;
            }
        }
        for (int i = 0; i < node.getChildCount() && matches != SPA_MATCH && remaining[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    matches = spaEvidence(child, matches, remaining);
                } finally {
                    child.recycle();
                }
            }
        }
        return matches;
    }

    private static boolean isAction(AccessibilityNodeInfo node) {
        if (node.isClickable()) {
            return true;
        }
        AccessibilityNodeInfo parent = node.getParent();
        if (parent == null) {
            return false;
        }
        try {
            return parent.isClickable();
        } finally {
            parent.recycle();
        }
    }

    private boolean hasServiceSwitch(AccessibilityNodeInfo root, String label) {
        if (label == null || appLabel.equals(label) || !label.contains(appLabel)) {
            return false;
        }
        return has(root, ID + "switch_text", label)
                || has(root, ID + "settingslib_switch_text", label)
                || (has(root, "android:id/title", label) && has(root, "android:id/switch_widget", null));
    }

    private static boolean has(AccessibilityNodeInfo root, String id, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        boolean match = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isVisibleToUser() && (text == null || text.equals(String.valueOf(node.getText())))) {
                match = true;
            }
            node.recycle();
        }
        return match;
    }

    private static String setting(Resources settings, String name, Object... arguments) {
        if (settings == null) {
            return null;
        }
        int id = settings.getIdentifier(name, "string", PACKAGE);
        if (id == 0) {
            return null;
        }
        return arguments.length == 0 ? settings.getString(id) : settings.getString(id, arguments);
    }
}
