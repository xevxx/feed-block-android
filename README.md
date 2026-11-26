# Feed Check

Feed Check is an Android companion for Snapchat. It covers recognised Spotlight and Discover feeds so you can keep using the official Snapchat app for messaging. While Spotlight is covered, Feed Check temporarily mutes the phone’s media audio.

## Download and set up

For APK installation or Accessibility permission help, see the [Android setup guide](docs/android-setup.md). It covers Family Link and the extra Auto Blocker step on Samsung Galaxy phones.

1. Download the signed Feed Check APK from [GitHub Releases](https://github.com/xevxx/snapchatfeedcontrol/releases) and install it on Android 8.0 or newer. Keep the official Snapchat app installed alongside it.
2. In Feed Check, tap **Open Accessibility settings**. Open the installed or downloaded services list, choose **Feed Check**, and enable it.
3. Return to Feed Check, tap **Turn on feed blocking**, and set a six-digit parent PIN.
4. Open Snapchat and check Spotlight, a Stories page with Discover cards, and a private chat on your phone. The block screen names the feed and offers **Go to Chat** when Snapchat’s Chat tab is available, plus **Go back** and **Leave Snapchat**.

Some Android versions require a separate approval before a sideloaded app can use Accessibility. [Android restricted settings](https://support.google.com/android/answer/12623953)

## Parent controls

- **Pause feed blocking** requires the parent PIN.
- **Protect Feed Check settings** adds a PIN guard to Feed Check’s own Accessibility details and App Info where supported. Android Settings and the Accessibility services list remain usable.
- **Allow settings for one minute** temporarily unlocks the guarded pages for maintenance.
- **Change parent PIN** appears after you set a PIN. Enter the current PIN and the new PIN twice.

## Compatibility

Feed Check recognises specific Snapchat layouts. A feed may appear briefly before it is covered, and Snapchat or Android updates can change which screens are recognised. The Settings guard was verified on a Huawei phone running Android 10 and in a stock Android emulator. Samsung Galaxy S21 Settings coverage still needs testing on the device.

For a screen that goes uncovered, open **⋮ → Diagnostics** in Feed Check. The diagnostic shows the screen identifiers it recognised during your check.

[Build and troubleshooting notes](companion/README.md) · [Earlier Snapchat APK audit](docs/snapchat-apk-audit.md) · [Changelog](CHANGELOG.md)
