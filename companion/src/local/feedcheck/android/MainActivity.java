package local.feedcheck.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Arrays;

public final class MainActivity extends Activity {
    private static final String PIN = "parent_pin";
    private SharedPreferences state;
    private TextView status;
    private TextView result;
    private Button start;
    private Button blockingButton;

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
        text(column, "Cover recognised Spotlight and Discover content in Snapchat. "
                + "While Spotlight is covered, Android media audio is muted.", 16);
        status = text(column, "", 16);

        Button settings = button(column, "Open Accessibility settings");
        settings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        blockingButton = button(column, "Turn on feed blocking");
        blockingButton.setOnClickListener(view -> changeBlocking());
        start = button(column, "Start a check in Snapchat");
        start.setOnClickListener(view -> startCheck());
        text(column, "Open the screen you want to check. Keep it open for a few seconds, then return here.", 16);
        result = text(column, "", 17);
    }

    @Override
    protected void onResume() {
        super.onResume();
        new SpotlightAudio(this).restore();
        if (state.getBoolean(FeedCheckService.ACTIVE, false)) {
            state.edit().putBoolean(FeedCheckService.ACTIVE, false).apply();
        }
        refreshControls();
        int last = state.getInt(FeedCheckService.LAST, FeedCheckService.NO_WINDOW);
        if (last == FeedCheckService.NO_WINDOW) {
            result.setText("No Snapchat window sampled yet.");
        } else {
            result.setText("Last Snapchat screen\n" + describe(last) + "\n\nSeen during this check\n"
                    + describe(state.getInt(FeedCheckService.OBSERVED, 0)));
        }
    }

    private void refreshControls() {
        boolean enabled = serviceEnabled();
        boolean blocking = state.getBoolean(FeedCheckService.BLOCKING, false);
        if (enabled) {
            status.setText(blocking ? "Feed blocking is on" : "Feed blocking is off");
        } else {
            status.setText("Turn on Feed Check in Accessibility to start");
        }
        start.setEnabled(enabled);
        blockingButton.setEnabled(enabled);
        blockingButton.setText(blocking ? "Pause feed blocking" : "Turn on feed blocking");
    }

    private void changeBlocking() {
        boolean blocking = state.getBoolean(FeedCheckService.BLOCKING, false);
        String verifier = state.getString(PIN, null);
        if (!blocking && verifier != null) {
            state.edit().putBoolean(FeedCheckService.BLOCKING, true).apply();
            refreshControls();
            return;
        }

        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        inputs.setPadding(dp(24), 0, dp(24), 0);
        EditText pin = pinField(inputs, "6-digit parent PIN");
        EditText repeated = blocking ? null : pinField(inputs, "Repeat parent PIN");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(blocking ? "Pause feed blocking" : "Create a parent PIN")
                .setView(inputs)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(blocking ? "Pause" : "Turn on", null)
                .create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            char[] value = readPin(pin);
            char[] confirmation = repeated == null ? new char[0] : readPin(repeated);
            try {
                if (blocking && !ParentPin.matches(value, verifier)) {
                    pin.setError("Incorrect parent PIN");
                    return;
                }
                if (!blocking && (!ParentPin.valid(value) || !Arrays.equals(value, confirmation))) {
                    pin.setError("Enter the same six digits twice");
                    return;
                }
                SharedPreferences.Editor edit = state.edit().putBoolean(FeedCheckService.BLOCKING, !blocking);
                if (!blocking) {
                    edit.putString(PIN, ParentPin.create(value));
                }
                edit.apply();
                dialog.dismiss();
                refreshControls();
            } catch (IllegalStateException error) {
                pin.setError("PIN verification is unavailable on this device");
            } finally {
                Arrays.fill(value, '\0');
                Arrays.fill(confirmation, '\0');
            }
        });
    }

    private EditText pinField(LinearLayout parent, String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(ParentPin.LENGTH)});
        field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        parent.addView(field);
        return field;
    }

    private char[] readPin(EditText field) {
        Editable text = field.getText();
        char[] value = new char[text.length()];
        text.getChars(0, text.length(), value, 0);
        text.clear();
        return value;
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
