# Feed Block

<p align="center">
  <img src="companion/res/drawable-xxxhdpi/block_screen_cat.png" alt="Feed Block cat mascot" width="96" height="96">
</p>

We made Feed Block because we wanted a little more choice over how our family uses these apps. With Feed Block, kids can chat with friends and watch videos they’ve picked, and families can choose which recommended feeds to leave switched off.

It works alongside Snapchat, Instagram and YouTube on Android. Each app has its own switch. If your child opens a blocked feed, they see a message such as **Spotlight blocked**. They can tap **Go back** or leave the app.

## What does it block?

| App | With blocking turned on |
| --- | --- |
| Snapchat | Opening Spotlight brings up the block message. You’ll see a **Discover blocked** message on the Stories screen when Feed Block recognises a Discover card. Chats stay available. A friend’s Story opened directly can still play. **Go to Chat** takes you straight back to conversations when Snapchat shows its Chat tab. |
| Instagram | Opening the Reels tab or the Explore grid brings up a block message. A video you open from Home can play. Swipe to the next Reel and the block screen appears. Home, Stories and DMs stay available, and **Search Instagram** opens search from the blocked Explore screen. |
| YouTube | Opening the Shorts player brings up the block screen. You can still search for videos and watch them in the regular player. |

While Spotlight, Reels or Shorts is blocked, Feed Block mutes the phone’s media audio so the clip doesn’t keep playing out loud. It restores the previous sound setting when you leave.

## Set it up

1. On the phone your child uses (Android 8 or later), download and install the signed APK from [GitHub Releases](https://github.com/xevxx/feed-block-android/releases).
2. Open Feed Block and tap **Open Accessibility settings**. Select **Feed Block** under installed or downloaded services and turn it on. This lets the app recognise a feed when it opens. The check runs on the phone. Feed Block has no internet permission.
3. Back in **Feed blocking**, turn on the apps you want. The first switch asks you to set a six-digit parent PIN. You’ll need it to pause blocking.
4. Try one blocked feed and one thing your child wants to keep using. For example, open Spotlight, then go back and send a chat.

Feed Block has worked on a Samsung Galaxy S21. If Family Link stops the install, Samsung shows an Auto Blocker warning, or the Accessibility switch is greyed out, follow the [Android setup guide](docs/android-setup.md).

## What can my child change?

They can choose the face on the block screen under **More options → Block screen icon**. The cat is the default, with a goose, frog and shield to choose from.

Pausing a feed requires the parent PIN. To also ask for the PIN when someone opens Feed Block’s own Accessibility controls or App Info, turn on **Protect Feed Block settings**. Other Android settings stay available. Use **Allow settings for one minute** when you need access, or **Change parent PIN** to set a new PIN using the current one.

The settings guard depends on the phone. Both pages have been tested on a Huawei running Android 10 and in a stock Android emulator. Android may offer other ways to disable or remove the app.

## If a feed slips through

Feed Block looks for familiar controls on the screen. You may see a feed briefly before the block message appears, and an app update can change the layout. If a Snapchat feed stays open, use **More options → Diagnostics → Start a check in Snapchat**, visit that screen, then return to Feed Block to see what it recognised.

[Build and troubleshooting notes](companion/README.md) · [Earlier Snapchat APK audit](docs/snapchat-apk-audit.md) · [Changelog](CHANGELOG.md)
