package local.feedcheck.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private SharedPreferences state;
    private TextView status;
    private TextView result;
    private Button start;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        state = FeedCheckService.state(this);

        ScrollView screen = new ScrollView(this);
        screen.setBackgroundColor(Color.rgb(244, 247, 245));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        screen.addView(column);
        int padding = dp(24);
        column.setPadding(padding, padding, padding, padding);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            column.setPadding(padding + insets.getSystemWindowInsetLeft(),
                    padding + insets.getSystemWindowInsetTop(), padding + insets.getSystemWindowInsetRight(),
                    padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(screen);

        text(column, "Feed Check", 30);
        text(column, "Check whether Android can identify Spotlight, Discover cards and chat in Snapchat. "
                + "Results stay on this phone. This test leaves Snapchat navigation alone.", 16);
        status = text(column, "", 16);

        Button settings = button(column, "Open Accessibility settings");
        settings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        start = button(column, "Start a check in Snapchat");
        start.setOnClickListener(view -> startCheck());
        text(column, "Open the screen you want to check. Keep it open for a few seconds, then return here.", 16);
        result = text(column, "", 17);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state.getBoolean(FeedCheckService.ACTIVE, false)) {
            state.edit().putBoolean(FeedCheckService.ACTIVE, false).apply();
        }
        boolean enabled = serviceEnabled();
        status.setText(enabled ? "Accessibility is on" : "Turn on Feed Check in Accessibility to start");
        start.setEnabled(enabled);
        int last = state.getInt(FeedCheckService.LAST, FeedCheckService.NO_WINDOW);
        if (last == FeedCheckService.NO_WINDOW) {
            result.setText("No Snapchat window sampled yet.");
        } else {
            result.setText("Last Snapchat screen\n" + describe(last) + "\n\nSeen during this check\n"
                    + describe(state.getInt(FeedCheckService.OBSERVED, 0)));
        }
    }

    private boolean serviceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (getPackageName().equals(service.packageName) && FeedCheckService.class.getName().equals(service.name)) {
                return true;
            }
        }
        return false;
    }

    private void startCheck() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(FeedCheckService.SNAPCHAT);
        if (launch == null) {
            Toast.makeText(this, "Install Snapchat to start a check", Toast.LENGTH_LONG).show();
            return;
        }
        state.edit().putBoolean(FeedCheckService.ACTIVE, true)
                .remove(FeedCheckService.LAST).remove(FeedCheckService.OBSERVED).apply();
        startActivity(launch);
    }

    private String describe(int markers) {
        StringBuilder description = new StringBuilder();
        for (FeedCheckService.Marker marker : FeedCheckService.Marker.values()) {
            if ((markers & marker.bit) != 0) {
                if (description.length() > 0) {
                    description.append('\n');
                }
                description.append(marker.label);
            }
        }
        return description.length() == 0 ? "No known screen identifiers" : description.toString();
    }

    private Button button(LinearLayout column, String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        column.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private TextView text(LinearLayout column, String value, int size) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(Color.rgb(25, 43, 34));
        label.setTextSize(size);
        label.setPadding(0, dp(4), 0, dp(12));
        column.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
