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
