# Feed Check

Feed Check is an Android app that covers recognised Spotlight and Discover feeds in Snapchat, the Shorts player in YouTube, and Reels and Explore in Instagram. You choose which apps to filter. Feed Check temporarily mutes the phone’s media audio while it covers Spotlight, Shorts, or Reels.

## Download and set up

For APK installation or Accessibility permission help, see the [Android setup guide](docs/android-setup.md). It covers Family Link and the extra Auto Blocker step on Samsung Galaxy phones.

1. Download the signed Feed Check APK from [GitHub Releases](https://github.com/xevxx/feed-check-android/releases) and install it on Android 8.0 or newer.
2. In Feed Check, tap **Open Accessibility settings**. Open the installed or downloaded services list, choose **Feed Check**, and enable it.
3. To filter Snapchat, tap **Turn on feed blocking**. Open the official Snapchat app and check Spotlight, a Stories page with Discover cards, and a private chat. The block screen names the feed and offers **Go to Chat** when Snapchat’s Chat tab is available.
4. To filter YouTube, tap **Block YouTube Shorts** under **Other feeds**. Open the official YouTube app to check a Short and a regular video.
5. To filter Instagram, tap **Block Instagram Reels & Explore** under **Other feeds**. This covers the Reels tab and Explore recommendations. A video opened from Home can play. If you swipe to another Reel in that viewer, Feed Check covers it and mutes its audio. Use **Search Instagram** on the Explore block screen to open manual search.

Feed Check asks you to set a six-digit parent PIN the first time you enable a blocker.

Some Android versions require a separate approval before a sideloaded app can use Accessibility. [Android restricted settings](https://support.google.com/android/answer/12623953)

## Parent controls

- Pausing blocking for any app requires the parent PIN.
- **Protect Feed Check settings** adds a PIN guard to Feed Check’s own Accessibility details and App Info where supported. Android Settings and the Accessibility services list remain usable.
- **Allow settings for one minute** temporarily unlocks the guarded pages for maintenance.
- **Change parent PIN** appears after you set a PIN. Enter the current PIN and the new PIN twice.

## Compatibility

Feed Check recognises specific app layouts. A feed may appear briefly before it is covered, and app or Android updates can change which screens are recognised. The Settings guard was verified on a Huawei phone running Android 10 and in a stock Android emulator. Samsung Galaxy S21 Settings coverage still needs testing on the device.

For a Snapchat screen that goes uncovered, open **⋮ → Diagnostics** in Feed Check. The diagnostic shows the Snapchat screen identifiers it recognised during your check.

[Build and troubleshooting notes](companion/README.md) · [Earlier Snapchat APK audit](docs/snapchat-apk-audit.md) · [Changelog](CHANGELOG.md)
