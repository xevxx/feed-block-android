# Changelog

## 2026-11-26

- Verified and archived the supplied Snapchat 14.33.1.0 standalone APK.
- Disassembled all 11 DEX files and mapped navigation, settings, friend relationship, notification and content paths.
- Added an isolated Smali experiment for the two implementations of the chat Spotlight playback interface and verified the emitted DEX with Baksmali.
- Installed and launched the original APK offline on a disposable Android 36.1 emulator.
- Added a version profile, a release gate, and documentation for using the official app with Family Center controls.
- Added `sign-baseline` to verify, align, and re-sign the original APK for a clean-install test. It checks every non-signature entry against the source APK.
- Installed the re-signed copy offline on a fresh emulator and opened the empty login form.
- Recorded the failed online sign-in with the locally signed test APK. Snapchat reported temporarily disabled access due to unusual activity.
- Installed the separate Feed Check diagnostic on an Android 10 phone and confirmed that its Accessibility service reported Spotlight and a Discover card in Snapchat 14.32.0.54.
- Verified the private chat control case on the same phone. The user reported only the two chat identifiers.
- Recorded that the tested Stories screen exposed a Discover card as its last sample.
- Added an optional feed overlay with user-controlled navigation and a parent PIN to pause blocking.
- Switched overlay detection to the active Snapchat window and observed a visible overlay over a Discover card on the connected phone.
- Temporarily muted Android media while the Spotlight overlay is visible. The user reported silence in Spotlight and the phone reported its original audio level after leaving.
- Recorded the later account-status clarification for the earlier emulator login test.
- Added an opt-in parent-PIN guard for Feed Check’s App Info and Accessibility details, with a one-minute unlock for maintenance.
- Added a Feed Check launcher icon, refreshed the home and block screens, and moved Snapchat checks into the Diagnostics menu.
- Added a parent PIN change option that verifies the current PIN before saving a new one.
