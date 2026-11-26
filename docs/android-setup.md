# Set up Feed Check from an APK on Android

Follow these steps on the Android phone where Feed Check will run. Which approvals you see depends on the phone, the Android version, and how you install the APK.

## Before installing

- **Family Link**. If the phone is supervised, open **Family Link** on the parent’s device and select the child. Go to **Controls → Signed-in devices** (or **Supervised devices**), choose the child’s Android phone, and turn on **Apps from unknown sources** for the installation. [Google Family Link device controls](https://support.google.com/families/answer/10436839) · [Google’s unknown-source setting](https://support.google.com/families/answer/18753591)
- **Samsung Galaxy**. If **Auto Blocker** prevents installation, go to **Settings → Security and privacy → Auto Blocker** on the Galaxy and turn it off temporarily. [Samsung Auto Blocker guide](https://www.samsung.com/uk/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/)

## Install the APK

1. Download the signed Feed Check APK from [GitHub Releases](https://github.com/xevxx/snapchatfeedcontrol/releases) and open the downloaded file.
2. If Android asks for installation permission, open **Settings** from the prompt and turn on **Allow from this source** for the app opening the APK, such as your browser or file manager. You can also search Android Settings for **Install unknown apps**. Return to the APK and tap **Install**. [Google’s APK installation guide](https://support.google.com/pixelphone/answer/7391672) · [Samsung install permissions guide](https://www.samsung.com/us/support/troubleshoot/TSG10001913/)

After Feed Check is installed, restore any installation settings you changed. Turn Auto Blocker back on if you turned it off, and restore the Family Link and **Install unknown apps** settings to their previous values.

## Allow Accessibility for Feed Check

1. In Feed Check, tap **Open Accessibility settings**. Find **Feed Check** under installed or downloaded services and try to enable it. On Samsung, the list is **Settings → Accessibility → Installed apps**. [Samsung Accessibility guide](https://www.samsung.com/us/support/answer/ANS10001906/)
2. On Android 13 or later, if you see **Restricted setting** or the switch is greyed out, open **Settings → Apps → Feed Check**. Tap **See all apps** first if needed. On Feed Check’s **App info** page, tap the **three-dot menu (⋮)**, choose **Allow restricted settings**, and follow Android’s confirmation prompt. [Google Android restricted settings guide](https://support.google.com/android/answer/12623953)
3. Return to **Accessibility**, select **Feed Check**, turn on the service, and confirm the Accessibility prompt.
4. Open **Feed Check**, tap **Turn on feed blocking**, and set a six-digit parent PIN. Open Snapchat to check Spotlight, Discover, and a private chat.
