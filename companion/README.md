# Feed Check

Feed Check uses Android Accessibility to cover recognised Spotlight and Discover content with **Feed blocked**, **Go back**, and **Leave Snapchat**. The user chooses when to go back or leave. Feed Check keeps the official Snapchat package installed.

## Turn on blocking

1. Open **Feed Check**, then **Open Accessibility settings**. Find **Feed Check** under installed or downloaded services and turn it on.
2. Return to Feed Check, tap **Turn on feed blocking**, and set a six-digit parent PIN on the phone. Blocking stays on until a parent pauses it inside Feed Check with that PIN or the Accessibility service is disabled in Android Settings.
3. Open Snapchat and visit Spotlight and the Stories page that shows Discover cards. Check that **Feed blocked** stays on screen until you use **Go back** or leave. Open a private chat separately to verify it stays usable.

If Android restricts access for the sideloaded app, the phone owner can review the Android setting under **App info → More → Allow restricted settings**. [Android restricted settings](https://support.google.com/android/answer/12623953)

The service acts only when a known feed marker is visible in the focused Snapchat window and neither chat marker is visible. Two samples are required before it displays the overlay. A direct friend Story without those markers is left alone. Feed Check does not determine friendship or prevent content downloads. The overlay can appear after a feed becomes visible, and Snapchat layout changes can affect coverage. Android Settings can disable the service independently of the parent PIN.

## Check screen identifiers

Use **Start a check in Snapchat** and open the screen you want to check. Wait a few seconds, then return through Android's app switcher and read **Last Snapchat screen**. If you need to inspect a feed with the overlay off, pause it with the parent PIN first. The service checks four exact IDs and stores only the matches during a diagnostic run. It requests Snapchat events, has no network permission, and accesses neither message text nor screenshots.

## Build

From the repository, run `bash companion/build.sh` with SDK platform 36, build tools 36.0.0, a JDK, and Python 3. The script signs the separate APK with a local key ignored by Git. It writes `work/snapchat-feed-control.apk` by default and accepts a different new APK path as its first argument. The app supports Android API 26 and newer.

Both inspected Snapchat APKs, 14.32.0.54 and 14.33.1.0, contain `spotlight_container`, `df_large_story`, `chat_message_list`, and `chat_input_text_field`. This proves that the resource names exist in the APKs. Whether Android reports each resource while its associated screen is open is the purpose of the on-device check. `df_large_story` covers a particular Discover card layout and provides no evidence for the whole feed.

The signed diagnostic APK installed and reached its home screen on the connected Android 10 phone with Snapchat 14.32.0.54. After enabling the service, the user opened Stories and then Spotlight in one run and reported **Spotlight** and **Discover card**. In a separate private chat check, the user reported **Chat message list** and **Chat input** only.

After opening Stories again, the user reported **Discover card** under **Last Snapchat screen** and both feed identifiers under **Seen during this check**. The latter accumulates matches across screens during a run. The last sample identifies a Discover card on the tested Stories screen. Stories without that card, direct Story playback, and other entry routes still need checks.

Version 0.2 installed as an update on the same phone and retained the existing Accessibility grant. Local checks covered the feed marker rules, chat exclusions, and PIN verification. The overlay starts off on this upgrade until the parent sets a PIN and turns it on.
