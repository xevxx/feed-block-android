package local.feedcheck.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final String PIN = "parent_pin";
    private static final String SHOW_DIAGNOSTICS = "show_diagnostics";
    private SharedPreferences state;
    private View accessibilitySetup;
    private final Map<FeedProfile, Switch> feedSwitches = new LinkedHashMap<>();
    private TextView settingsGuardDetail;
    private Button settingsGuardButton;
    private Button temporaryAccess;
    private View changePinButton;
    private AlertDialog diagnostics;
    private TextView diagnosticInstructions;
    private TextView diagnosticResult;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        state = FeedCheckService.state(this);

        setContentView(R.layout.home);
        View column = findViewById(R.id.home_content);
        int padding = column.getPaddingTop();
        column.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int right = insets.getSystemWindowInsetRight();
            int extra = Math.max(0, dp(getResources().getConfiguration().screenWidthDp - 520) - left - right) / 2;
            view.setPadding(padding + left + extra, padding + insets.getSystemWindowInsetTop(),
                    padding + right + extra, padding + insets.getSystemWindowInsetBottom());
            return insets;
        });

        findViewById(R.id.more_options).setOnClickListener(this::showMenu);
        accessibilitySetup = findViewById(R.id.accessibility_setup);
        findViewById(R.id.accessibility_setup_action).setOnClickListener(view -> openAccessibilitySettings());
        LinearLayout feedControls = findViewById(R.id.feed_controls);
        for (FeedProfile profile : FeedProfile.SUPPORTED) {
            View row = getLayoutInflater().inflate(R.layout.feed_profile_row, feedControls, false);
            Switch toggle = row.findViewById(R.id.feed_toggle);
            toggle.setText(profile.appName);
            TextView coverage = row.findViewById(R.id.feed_coverage);
            coverage.setText(profile.coverage);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (checked != profile.enabled(state)) {
                    changeBlocking(profile);
                }
            });
            feedControls.addView(row);
            feedSwitches.put(profile, toggle);
        }
        settingsGuardDetail = findViewById(R.id.settings_guard_detail);
        settingsGuardButton = findViewById(R.id.settings_guard_action);
        settingsGuardButton.setOnClickListener(view -> changeSettingsGuard());
        temporaryAccess = findViewById(R.id.temporary_access);
        temporaryAccess.setOnClickListener(view -> showPinPrompt(
                getString(R.string.allow_settings_title), "Allow", null, edit -> {
                    ProtectedSettings.allowTemporarily();
                    Toast.makeText(this, R.string.settings_unlocked, Toast.LENGTH_SHORT).show();
                }));
        changePinButton = findViewById(R.id.change_parent_pin);
        changePinButton.setOnClickListener(view -> changeParentPin());
        if (savedState != null && savedState.getBoolean(SHOW_DIAGNOSTICS)) {
            showDiagnostics();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        new FeedAudio(this).restore();
        boolean finishedCheck = state.getBoolean(FeedCheckService.ACTIVE, false);
        if (finishedCheck) {
            state.edit().putBoolean(FeedCheckService.ACTIVE, false).apply();
        }
        refreshControls();
        if (finishedCheck && diagnostics == null) {
            showDiagnostics();
        } else if (diagnostics != null) {
            refreshDiagnostics();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_DIAGNOSTICS, diagnostics != null);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        menu.getMenu().add(R.string.diagnostics).setOnMenuItemClickListener(item -> {
            showDiagnostics();
            return true;
        });
        menu.getMenu().add(R.string.open_accessibility_settings).setOnMenuItemClickListener(item -> {
            openAccessibilitySettings();
            return true;
        });
        menu.getMenu().add(R.string.block_screen_icon).setOnMenuItemClickListener(item -> {
            showBlockScreenIconOptions();
            return true;
        });
        menu.show();
    }

    private void showBlockScreenIconOptions() {
        BlockScreenIcon[] icons = BlockScreenIcon.values();
        CharSequence[] labels = new CharSequence[icons.length];
        for (int index = 0; index < icons.length; index++) {
            labels[index] = getText(icons[index].label);
        }
        int previewSize = dp(40);
        ArrayAdapter<CharSequence> choices = new ArrayAdapter<>(this, R.layout.block_screen_icon_option, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, convertView, parent);
                Drawable preview = getContext().getDrawable(icons[position].drawable);
                preview.setBounds(0, 0, previewSize, previewSize);
                row.setCompoundDrawablesRelative(preview, null, null, null);
                return row;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.block_screen_icon)
                .setSingleChoiceItems(choices, BlockScreenIcon.selected(state).ordinal(), (dialog, index) -> {
                    icons[index].save(state);
                    dialog.dismiss();
                })
                .show();
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void showDiagnostics() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(8));
        scroll.addView(content);
        diagnosticInstructions = new TextView(this);
        diagnosticInstructions.setTextColor(getColor(R.color.ink_muted));
        diagnosticInstructions.setTextSize(15);
        content.addView(diagnosticInstructions);
        diagnosticResult = new TextView(this);
        diagnosticResult.setPadding(0, dp(18), 0, 0);
        diagnosticResult.setTextColor(getColor(R.color.ink));
        diagnosticResult.setTextSize(16);
        content.addView(diagnosticResult);

        diagnostics = new AlertDialog.Builder(this)
                .setTitle(R.string.diagnostics)
                .setView(scroll)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.start_check, null)
                .create();
        diagnostics.setOnDismissListener(dialog -> {
            diagnostics = null;
            diagnosticInstructions = null;
            diagnosticResult = null;
        });
        diagnostics.show();
        diagnostics.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> startCheck());
        refreshDiagnostics();
    }

    private void refreshDiagnostics() {
        boolean enabled = serviceEnabled();
        diagnosticInstructions.setText(enabled ? R.string.diagnostics_instructions : R.string.diagnostics_needs_accessibility);
        diagnostics.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
        int last = state.getInt(FeedCheckService.LAST, FeedCheckService.NO_WINDOW);
        diagnosticResult.setText(last == FeedCheckService.NO_WINDOW ? getString(R.string.diagnostics_empty)
                : getString(R.string.diagnostics_results, describe(last), describe(state.getInt(FeedCheckService.OBSERVED, 0))));
    }

    private void refreshControls() {
        boolean enabled = serviceEnabled();
        boolean settingsGuard = state.getBoolean(ProtectedSettings.ENABLED, false);
        accessibilitySetup.setVisibility(enabled ? View.GONE : View.VISIBLE);
        if (enabled) {
            settingsGuardDetail.setText(settingsGuard ? R.string.guard_on_detail : R.string.guard_off_detail);
        } else {
            settingsGuardDetail.setText(R.string.guard_needs_accessibility);
        }
        settingsGuardButton.setText(settingsGuard ? R.string.disable_guard : R.string.enable_guard);
        settingsGuardButton.setEnabled(enabled || settingsGuard);
        temporaryAccess.setVisibility(settingsGuard ? View.VISIBLE : View.GONE);
        temporaryAccess.setEnabled(enabled);
        changePinButton.setVisibility(state.contains(PIN) ? View.VISIBLE : View.GONE);
        for (Map.Entry<FeedProfile, Switch> entry : feedSwitches.entrySet()) {
            Switch toggle = entry.getValue();
            toggle.setEnabled(enabled);
            toggle.setChecked(entry.getKey().enabled(state));
        }
    }

    private void changeBlocking(FeedProfile profile) {
        boolean blocking = profile.enabled(state);
        String verifier = state.getString(PIN, null);
        if (!blocking && verifier != null) {
            state.edit().putBoolean(profile.preference, true).apply();
            refreshControls();
            return;
        }
        // Keep the saved switch position while the PIN prompt is open.
        refreshControls();
        String pauseTitle = getString(R.string.pause_named_feed, getString(profile.appName));
        showPinPrompt(blocking ? pauseTitle : "Create a parent PIN", blocking ? "Pause" : "Turn on",
                null, edit -> edit.putBoolean(profile.preference, !blocking));
    }

    private void changeSettingsGuard() {
        boolean enabled = state.getBoolean(ProtectedSettings.ENABLED, false);
        showPinPrompt(enabled ? getString(R.string.disable_guard) : getString(R.string.enable_guard),
                enabled ? "Turn off" : "Protect", enabled ? null
                        : getString(R.string.guard_confirmation),
                edit -> {
                    ProtectedSettings.endTemporaryAccess();
                    edit.putBoolean(ProtectedSettings.ENABLED, !enabled);
                });
    }

    private void showPinPrompt(String title, String action, String message,
            Consumer<SharedPreferences.Editor> applyChange) {
        String verifier = state.getString(PIN, null);
        boolean creating = verifier == null;
        if (creating && (state.getBoolean(ProtectedSettings.ENABLED, false)
                || FeedProfile.SUPPORTED.stream().anyMatch(profile -> profile.enabled(state)))) {
            Toast.makeText(this, "The parent PIN could not be loaded", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout inputs = pinInputs();
        EditText pin = pinField(inputs, "6-digit parent PIN");
        EditText repeated = creating ? pinField(inputs, "Repeat parent PIN") : null;
        AlertDialog dialog = showPinDialog(inputs, creating ? "Create a parent PIN" : title, action, message);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            char[] value = readPin(pin);
            char[] confirmation = repeated == null ? new char[0] : readPin(repeated);
            try {
                if (!Objects.equals(verifier, state.getString(PIN, null))) {
                    dialog.dismiss();
                    refreshControls();
                    Toast.makeText(this, R.string.pin_changed_elsewhere, Toast.LENGTH_LONG).show();
                    return;
                }
                if (!creating && !ParentPin.matches(value, verifier)) {
                    pin.setError(getString(R.string.incorrect_parent_pin));
                    return;
                }
                if (creating && (!ParentPin.valid(value) || !Arrays.equals(value, confirmation))) {
                    pin.setError(getString(R.string.pin_confirmation_error));
                    return;
                }
                SharedPreferences.Editor edit = state.edit();
                if (creating) {
                    edit.putString(PIN, ParentPin.create(value));
                }
                applyChange.accept(edit);
                edit.apply();
                dialog.dismiss();
                refreshControls();
            } catch (IllegalStateException error) {
                pin.setError(getString(R.string.pin_verification_unavailable));
            } finally {
                Arrays.fill(value, '\0');
                Arrays.fill(confirmation, '\0');
            }
        });
    }

    private void changeParentPin() {
        LinearLayout inputs = pinInputs();
        EditText currentPin = pinField(inputs, getString(R.string.current_parent_pin));
        EditText newPin = pinField(inputs, getString(R.string.new_parent_pin));
        EditText repeatedPin = pinField(inputs, getString(R.string.repeat_new_pin));
        AlertDialog dialog = showPinDialog(inputs, getString(R.string.change_parent_pin), getString(R.string.save_pin), null);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            char[] current = readPin(currentPin);
            char[] replacement = readPin(newPin);
            char[] repeated = readPin(repeatedPin);
            try {
                if (!ParentPin.matches(current, state.getString(PIN, null))) {
                    currentPin.setError(getString(R.string.incorrect_parent_pin));
                    currentPin.requestFocus();
                    return;
                }
                if (!ParentPin.valid(replacement) || !Arrays.equals(replacement, repeated)) {
                    newPin.setError(getString(R.string.pin_confirmation_error));
                    newPin.requestFocus();
                    return;
                }
                state.edit().putString(PIN, ParentPin.create(replacement)).apply();
                dialog.dismiss();
                Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show();
            } catch (IllegalStateException error) {
                currentPin.setError(getString(R.string.pin_verification_unavailable));
            } finally {
                Arrays.fill(current, '\0');
                Arrays.fill(replacement, '\0');
                Arrays.fill(repeated, '\0');
            }
        });
    }

    private LinearLayout pinInputs() {
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        inputs.setPadding(dp(24), 0, dp(24), 0);
        return inputs;
    }

    private AlertDialog showPinDialog(LinearLayout inputs, String title, String action, String message) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(inputs);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(action, null)
                .create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        return dialog;
    }

    private EditText pinField(LinearLayout parent, String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(ParentPin.LENGTH)});
        field.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setSaveEnabled(false);
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
        Intent launch = getPackageManager().getLaunchIntentForPackage(FeedProfile.SNAPCHAT.packageName);
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
        for (FeedProfile.SnapchatMarker marker : FeedProfile.SnapchatMarker.values()) {
            if ((markers & marker.bit) != 0) {
                if (description.length() > 0) {
                    description.append('\n');
                }
                description.append(marker.label);
            }
        }
        return description.length() == 0 ? "No known screen identifiers" : description.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
