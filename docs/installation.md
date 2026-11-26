# Installation and official app controls

## Android

1. Install or update [Snapchat from Google Play](https://play.google.com/store/apps/details?id=com.snapchat.android). If it is already installed, use the in-place Play update to preserve device data.
2. On the parent's Snapchat account, add the teen as a friend. Open Family Center and send the invitation. The teen accepts from their account. [Snapchat setup instructions](https://help.snapchat.com/hc/en-us/articles/8132784476820-How-do-I-add-my-teen-to-Family-Center)
3. In Family Center, open **Settings** and turn on **Hide Sensitive Content**. Snapchat describes this as filtering sensitive or suggestive material in Stories and Spotlight. The feeds stay available and some material can slip through. [Snapchat content filter](https://help.snapchat.com/hc/en-us/articles/13904198624276-How-do-I-restrict-content-for-my-teen-in-Family-Center)

Google Family Link can apply a time limit to Snapchat as a whole. That limit also applies to messaging and calling within Snapchat. [Google Family Link app limits](https://support.google.com/families/answer/15957417?hl=en-EN)

## Optional desktop access

Snapchat for Web supports chat, voice and video calls, and taking Snaps from a desktop browser. Snap documents Chrome, Edge and Safari. The rest of the Android messaging requirements, including mobile notifications and exact attachment behavior, remain unverified for this alternative. [Snapchat for Web](https://help.snapchat.com/hc/en-us/articles/7121575005332-How-to-Call-Snap-and-Chat-from-Snapchat-for-Web)

## Repository APK

The APK in `originals/` is the official signed backup for research and offline test installation. It has ordinary Snapchat functionality, including feeds. The repository currently has no installable build with parental content controls. See the [compatibility report](compatibility-329222.md) for release requirements and risks.

## Locally signed test APK

`snapchat-14.33.1.0-local-signature-test.apk` in the parent folder has the original app payload and a local certificate. Use a disposable Android test environment where `com.snapchat.android` is absent. Android rejects replacement of an existing Snapchat installation signed by Snap. [Android app update rules](https://developer.android.com/google/play/app-updates)

```bash
adb -s TEST_DEVICE install ../snapchat-14.33.1.0-local-signature-test.apk
```

The signed test build includes the original feeds. It installed and opened the **Log In** form. Snapchat then declined an online sign-in from the test emulator with a temporary-access message. The cause of that refusal remains undetermined. [Snapchat login errors](https://help.snapchat.com/hc/en-gb/articles/7012325477268-I-get-an-error-message-logging-in-to-Snapchat)
