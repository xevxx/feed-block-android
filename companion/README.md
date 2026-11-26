# Feed Block

For a family setting up a phone, start with the [Android setup guide](../docs/android-setup.md). This page explains how to try the blockers, check a screen the app missed, and build the APK.

Feed Block uses Android Accessibility to show a block message when someone opens a feed your family chose to block. The block screen offers **Go back**, which follows the app’s navigation history, or **Leave** followed by the app name, which takes you to the Android home screen. If going back lands on another blocked feed, you’ll see the block screen again. Snapchat also offers **Go to Chat** when its Chat tab is available.

## Turn on blocking

1. Open **Feed Block**, then **Open Accessibility settings**. Find **Feed Block** under installed or downloaded services and turn it on.
2. Return to Feed Block and turn on the apps you want to filter under **Feed blocking**. Set a six-digit parent PIN when prompted. Turning off an enabled switch requires that PIN.
3. To test Snapchat, visit Spotlight and the Stories page that shows Discover cards. Try **Go to Chat** where available and verify that private chats stay usable.
4. To test Instagram, open Reels and Explore. Try **Search Instagram** from the Explore block screen, then check Home, Stories, and Direct messages.
5. To test YouTube, open a Short and then check the YouTube Home tab and a regular video.

If Android restricts access for the sideloaded app, the phone owner can review the Android setting under **App info → More → Allow restricted settings**. [Android restricted settings](https://support.google.com/android/answer/12623953)

For Snapchat, the service acts when a known feed marker is visible in the focused window and neither chat marker is visible. It checks the app’s screen twice before displaying the overlay. A direct friend Story without those markers is left alone. Feed Block does not determine friendship or prevent content downloads. The overlay can appear after a feed becomes visible, and app layout changes can affect coverage. Android Settings can disable the service independently of the parent PIN.

## Media sound

Feed Block temporarily mutes Android's media stream while the block screen is open in Spotlight, YouTube Shorts or Instagram Reels. Other apps on that media stream are also quiet during this interval. The app restores the prior mute state without setting a new volume level. Reopening Feed Block recovers a pending restore if Android stopped the service abruptly.

## Protect Feed Block settings

This optional guard starts off. In Feed Block, tap **Protect Feed Block settings** and enter the parent PIN. While the guard is on, recognised Feed Block **App Info** and **Accessibility service detail** pages show **Parent access required**. The Android Settings home, the Accessibility list, other service details, and other apps' App Info are available normally.

Use **Allow settings for one minute** with the PIN for maintenance. Return to Android Settings during that minute. **Turn off Settings guard** also requires the PIN. This setting is independent of feed blocking.

To replace an existing PIN, open **Parent controls → Change parent PIN**. Enter the current PIN and the new six-digit PIN twice, then select **Save PIN**. Use the new PIN the next time Feed Block asks for it.

Recognition uses exact screen layout identifiers and the Feed Block label. Unrecognised Settings layouts remain accessible. The guard requires the Accessibility service to be running, and Android can still provide other ways to disable it or uninstall the app.

Both pages were verified on a Huawei ELS-NX9 running Android 10 and on a stock Android 36.1 emulator. The Galaxy S21 runs One UI 7 on Android 15. On that phone, open **Settings → Accessibility → Installed apps → Feed Block** to reach its service switch. The two guarded pages still need verification on a physical S21. [Samsung One UI versions](https://www.samsung.com/us/support/answer/ANS10004612/) · [Samsung Accessibility settings](https://www.samsung.com/us/support/answer/ANS10001906/)

## Check screen identifiers

Open **More options → Diagnostics**, tap **Start a check in Snapchat**, and open the screen you want to check. Wait a few seconds, then return through Android's app switcher and read **Last Snapchat screen** in the Diagnostics dialog. If you need to inspect a feed with the overlay off, pause it with the parent PIN first. The service checks four exact IDs and stores only the matches during a diagnostic run. Diagnostics records which of those four controls appeared. It doesn’t save message text or take screenshots.

## Build

From the repository, run `bash companion/build.sh` with SDK platform 36, build tools 36.0.0, a JDK, and Python 3. The script signs the separate APK with a local key ignored by Git. It writes `work/feed-block.apk` by default and accepts a different new APK path as its first argument. The app supports Android API 26 and newer.

## Add support for another screen or app

The screen rules are in [FeedProfile.java](src/local/feedcheck/android/FeedProfile.java). Add a rule to the app’s existing profile, or register a new profile in `SUPPORTED` with the app’s Android package name, a unique preference key, and string resources for the app, feed, and block screen.

Each rule lists the resource IDs that must all be visible and any IDs that rule it out. It can also require an active selected control, such as a tab. Use the names after `:id/`. The profile checks the rules in order and the first match supplies the heading, decides whether the overlay mutes media, and can offer a shortcut to a native control. A profile can also require visible nodes to have nonempty screen bounds. Prefer IDs specific to the player or screen you want to block. Check that normal routes in the same app, such as Home, Search, and regular playback, stay accessible.

Each profile gets its own switch under **Feed blocking**. New profiles start off, and the service subscribes to their app’s Accessibility events when their blocking is enabled.

On the signed-in Huawei running Instagram 458.0.0.0.72, Android exposed `clips_viewer_view_pager` both in Reels and for videos opened from Home. A selected `clips_tab` identifies the dedicated feed. In the shared viewer, Feed Block uses page indexes reported by the pager’s scroll events to let the opened clip play and show the block message when another clip comes into view. Explore exposes `explore_action_bar`, and the shortcut activates `action_bar_search_edit_text` to open the separate search screen. The Stories viewer has its own layout.

Both inspected Snapchat APKs, 14.32.0.54 and 14.33.1.0, contain `spotlight_container`, `df_large_story`, `chat_message_list`, and `chat_input_text_field`. This proves that the resource names exist in the APKs. Whether Android reports each resource while its associated screen is open is the purpose of the on-device check. `df_large_story` covers a particular Discover card layout and provides no evidence for the whole feed.

The signed diagnostic APK installed and reached its home screen on the connected Android 10 phone with Snapchat 14.32.0.54. After enabling the service, the user opened Stories and then Spotlight in one run and reported **Spotlight** and **Discover card**. In a separate private chat check, the user reported **Chat message list** and **Chat input** only.

After opening Stories again, the user reported **Discover card** under **Last Snapchat screen** and both feed identifiers under **Seen during this check**. The latter accumulates matches across screens during a run. The last sample identifies a Discover card on the tested Stories screen. Stories without that card, direct Story playback, and other entry routes still need checks.

Version 0.2 installed as an update on the same phone and retained the existing Accessibility grant. Local checks covered the feed marker rules, chat exclusions, and PIN verification. That build did not display the overlay on the first phone test. Version 0.3 uses the active-window lookup from the diagnostic and retained the enabled blocking setting. The service reported a visible overlay over a Discover card, with both chat markers absent. The parent must set a PIN and explicitly turn on blocking on a fresh install or an upgrade from the diagnostic.

Version 0.4 installed on the same phone. With the update, the user reported silence in Spotlight. After leaving it, Android reported media unmuted at the same level as before the test. Isolated checks covered an existing mute, a failed mute request, and recovery across a service restart.
