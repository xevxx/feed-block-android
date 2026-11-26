# Feed Check

Feed Check uses Android Accessibility to cover recognised Spotlight and Discover content with **Spotlight blocked** or **Discover blocked**. **Go to Chat** selects Snapchat’s Chat tab when it is available on that screen. **Go back** follows Android’s back navigation and can land on another blocked feed. **Leave Snapchat** goes to the Android home screen.

## Turn on blocking

1. Open **Feed Check**, then **Open Accessibility settings**. Find **Feed Check** under installed or downloaded services and turn it on.
2. Return to Feed Check, tap **Turn on feed blocking**, and set a six-digit parent PIN on the phone. Blocking stays on until a parent pauses it inside Feed Check with that PIN or the Accessibility service is disabled in Android Settings.
3. Open Snapchat and visit Spotlight and the Stories page that shows Discover cards. Check that the block screen names the feed. Try **Go to Chat** where available and verify that private chats stay usable.

If Android restricts access for the sideloaded app, the phone owner can review the Android setting under **App info → More → Allow restricted settings**. [Android restricted settings](https://support.google.com/android/answer/12623953)

The service acts only when a known feed marker is visible in the focused Snapchat window and neither chat marker is visible. Two samples are required before it displays the overlay. A direct friend Story without those markers is left alone. Feed Check does not determine friendship or prevent content downloads. The overlay can appear after a feed becomes visible, and Snapchat layout changes can affect coverage. Android Settings can disable the service independently of the parent PIN.

## Spotlight sound

Feed Check temporarily mutes Android's media stream while a recognised Spotlight screen is covered. Other apps on that media stream are also quiet during this interval. The app restores the prior mute state without setting a new volume level. Reopening Feed Check recovers a pending restore if Android stopped the service abruptly.

## Protect Feed Check settings

This optional guard starts off. In Feed Check, tap **Protect Feed Check settings** and enter the parent PIN. While the guard is on, recognised Feed Check **App Info** and **Accessibility service detail** pages show **Parent access required**. The Android Settings home, the Accessibility list, other service details, and other apps' App Info are available normally.

Use **Allow settings for one minute** with the PIN for maintenance. Return to Android Settings during that minute. **Turn off Settings guard** also requires the PIN. This setting is independent of feed blocking.

To replace an existing PIN, open **Parent controls → Change parent PIN**. Enter the current PIN and the new six-digit PIN twice, then select **Save PIN**. Use the new PIN the next time Feed Check asks for it.

Recognition uses exact screen layout identifiers and the Feed Check label. Unrecognised Settings layouts remain accessible. The guard requires the Accessibility service to be running, and Android can still provide other ways to disable it or uninstall the app.

Both pages were verified on a Huawei ELS-NX9 running Android 10 and on a stock Android 36.1 emulator. The Galaxy S21 runs One UI 7 on Android 15. On that phone, open **Settings → Accessibility → Installed apps → Feed Check** to reach its service switch. The two guarded pages still need verification on a physical S21. [Samsung One UI versions](https://www.samsung.com/us/support/answer/ANS10004612/) · [Samsung Accessibility settings](https://www.samsung.com/us/support/answer/ANS10001906/)

## Check screen identifiers

Open **More options → Diagnostics**, tap **Start a check in Snapchat**, and open the screen you want to check. Wait a few seconds, then return through Android's app switcher and read **Last Snapchat screen** in the Diagnostics dialog. If you need to inspect a feed with the overlay off, pause it with the parent PIN first. The service checks four exact IDs and stores only the matches during a diagnostic run. It requests Snapchat events, has no network permission, and accesses neither message text nor screenshots.

## Build

From the repository, run `bash companion/build.sh` with SDK platform 36, build tools 36.0.0, a JDK, and Python 3. The script signs the separate APK with a local key ignored by Git. It writes `work/snapchat-feed-control.apk` by default and accepts a different new APK path as its first argument. The app supports Android API 26 and newer.

Both inspected Snapchat APKs, 14.32.0.54 and 14.33.1.0, contain `spotlight_container`, `df_large_story`, `chat_message_list`, and `chat_input_text_field`. This proves that the resource names exist in the APKs. Whether Android reports each resource while its associated screen is open is the purpose of the on-device check. `df_large_story` covers a particular Discover card layout and provides no evidence for the whole feed.

The signed diagnostic APK installed and reached its home screen on the connected Android 10 phone with Snapchat 14.32.0.54. After enabling the service, the user opened Stories and then Spotlight in one run and reported **Spotlight** and **Discover card**. In a separate private chat check, the user reported **Chat message list** and **Chat input** only.

After opening Stories again, the user reported **Discover card** under **Last Snapchat screen** and both feed identifiers under **Seen during this check**. The latter accumulates matches across screens during a run. The last sample identifies a Discover card on the tested Stories screen. Stories without that card, direct Story playback, and other entry routes still need checks.

Version 0.2 installed as an update on the same phone and retained the existing Accessibility grant. Local checks covered the feed marker rules, chat exclusions, and PIN verification. That build did not display the overlay on the first phone test. Version 0.3 uses the active-window lookup from the diagnostic and retained the enabled blocking setting. The service reported a visible overlay over a Discover card, with both chat markers absent. The parent must set a PIN and explicitly turn on blocking on a fresh install or an upgrade from the diagnostic.

Version 0.4 installed on the same phone. With the update, the user reported silence in Spotlight. After leaving it, Android reported media unmuted at the same level as before the test. Isolated checks covered an existing mute, a failed mute request, and recovery across a service restart.
