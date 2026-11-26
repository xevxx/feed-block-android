# Feed Check

Feed Check tests whether Snapchat exposes particular screen identifiers through Android Accessibility. It keeps the official Snapchat package installed and leaves navigation to the person using the phone. It reports matches and has no playback controls.

## On the phone

1. Open **Feed Check**, then **Open Accessibility settings**. Find **Feed Check** under installed or downloaded services and turn it on.
2. Return to Feed Check and tap **Start a check in Snapchat**. Open Spotlight, leave it open for a few seconds, and return to Feed Check using Android's app switcher. Record **Last Snapchat screen**.
3. Start another check and open a private chat. Leave it open for a few seconds, then return to Feed Check. The useful result is **Spotlight** on the feed and only chat markers in a private chat. Repeat separately for other surfaces.

If Android restricts access for the sideloaded app, the phone owner can review the Android setting under **App info → More → Allow restricted settings**. [Android restricted settings](https://support.google.com/android/answer/12623953)

The Accessibility service requests Snapchat events and checks four exact resource IDs while a test is active. It stores which IDs matched. The app has no network permission and its code accesses neither message text nor screenshots. The service remains available in Android Settings after a test ends. Turn it off there when finished.

## Build

From the repository, run `bash companion/build.sh` with SDK platform 36, build tools 36.0.0, a JDK, and Python 3. The script signs the separate APK with a local key ignored by Git. It writes `work/snapchat-feed-check.apk` by default and accepts a different new APK path as its first argument. The app supports Android API 26 and newer.

Both inspected Snapchat APKs, 14.32.0.54 and 14.33.1.0, contain `spotlight_container`, `df_large_story`, `chat_message_list`, and `chat_input_text_field`. This proves that the resource names exist in the APKs. Whether Android reports each resource while its associated screen is open is the purpose of the on-device check. `df_large_story` covers a particular Discover card layout and provides no evidence for the whole feed.

The signed diagnostic APK installed and reached its home screen on the connected Android 10 phone with Snapchat 14.32.0.54. After enabling the service, the user opened Stories and then Spotlight in one run. Feed Check reported **Spotlight** and **Discover card**. This confirms that the phone exposes both identifiers in that sequence. A separate chat check is still needed to validate that neither feed identifier appears in chat, and a Stories-only check is needed to assess Stories coverage.
