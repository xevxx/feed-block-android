# Snapchat 14.33.1.0 compatibility report

## Input and environment

| Field | Verified value |
| --- | --- |
| Android package | `com.snapchat.android` |
| Version | `14.33.1.0` (329222) |
| APK variant | Standalone, nodpi, ARM64 and ARMv7 |
| Android API | Minimum 23, target 36 |
| APK size | 208,502,615 bytes |
| DEX files | 11 |
| APK SHA-256 | `a96c3e3aa446df94bde33b78cd6c04dbdb83a2c480e268976d39a4cafa8a7f02` |
| Signer SHA-256 | `2f1caafca1ed30d0b4e38863eefabea0e815711fa4cf79b822519a8259d95a58` |

The byte hash and certificate match the exact APKMirror variant. Android SDK `apksigner` verified schemes v1, v2 and v3. `zipalign -c -P 16 4` passed on the original. The root APK and read-only backup matched byte for byte. [APKMirror variant](https://www.apkmirror.com/apk/snap-inc/snapchat/snapchat-14-33-1-0-release/snapchat-14-33-1-0-2-android-apk-download/)

Static work used Ubuntu 22.04 under WSL2, OpenJDK 25, Android SDK Build Tools 36.0.0, APKTool 2.5.0, its bundled Smali and Baksmali libraries, and JADX 1.5.7. APKTool disassembled 90,709 classes across 11 DEX files while preserving the binary manifest and resources. Targeted JADX output rendered `MainActivity`, with one decompilation error. The findings below were checked against Smali and Android's binary manifest dump.

## Findings from the APK

| Area | Evidence and consequence |
| --- | --- |
| Settings | `com.snap.modules.settings.RootSettingsComponent` points to `RootSettingsComponent@settings/src/RootSettingsScreen`. `NativeRowsFetcher.getRows()` bridges an observable into the Valdi settings module, a candidate for a native settings row. Two relevant packaged modules are `settings.valdimodule` and `settings_switchboard.valdimodule`. The parental row and persistent state are unimplemented. |
| Tabs and gestures | The DEX 1 enum `v700` contains `MAP`, `CHAT`, `CAMERA`, `DISCOVER` and `SPOTLIGHT`. `MainActivity`, `v680` and several peer classes reference it. This identifies a navigation model to trace through tab composition, swipe transitions and restored state. Other classes also use a `STORIES` label, so a patch needs to cover more than one view or enum. |
| External routes | The manifest accepts `snapchat:` URLs and HTTP(S) routes including `story.snapchat.com` and `link.snapchat.com`. `com.snap.mushroom.MainActivity` handles intents during startup and in `onNewIntent`. `NotificationTapTrampolineActivity` starts `MainActivity` with a constructed intent. The manifest also declares a separate `:pushproc` and an internal `SpotlightPipService`. An app-level content decision must work on cold and warm entry and preserve notification taps for chats and calls. |
| Shared Spotlight in chat | DEX 7 contains the `ISpotlightStoryLauncher.playStory` interface and two implementations. `o5y0` validates the argument and subscribes to an asynchronous lookup. `wu00` forwards the request to a Kotlin callback. These are the two targets of the isolated experiment. |
| Discover and Stories | `DiscoverHttpInterface.getUpNextResponse` declares `/df-mixer-prod/up_next`. `MixerStoriesBypassFsnHttpInterface` declares single and batch Story fetch and lookup methods. `StoriesHttpInterface` also contains grouped Story, privacy and blocked-user operations. Content gating needs to target retrieval and playback, leaving existing privacy and blocking operations usable. |
| Mutual friendship | `com.snapchat.client.messaging.FriendLink` has `MUTUAL_FRIEND`, `FOLLOWER`, `INCOMING`, `PENDING`, `DELETED`, `BLOCKED` and `UNKNOWN` states. An obfuscated mapping in DEX 5 constructs `FriendLinkData` from those values. The link from a rendered Story's author to an up-to-date `MUTUAL_FRIEND` decision has yet to be validated. Follow status is insufficient for the requested mode. |

## Reproduced checks

| Check | Result |
| --- | --- |
| Input provenance and preservation | Passed. APK SHA-256, signer, package, version, SDKs, ABIs and DEX count matched the version profile. A byte-for-byte read-only backup was created. |
| Structural match | Passed. The interface, method descriptor and instruction anchors selected exactly two implementations, including the callback adapter. |
| Smali assembly and Baksmali round trip | Passed for the two isolated classes. Each selected method consists of one `return-void` instruction. All other methods were copied unchanged into the experiment inputs. The lab DEX is temporary and is never merged into Snapchat. |
| Unknown input | Passed. Appending one byte to a disposable copy caused the experiment to stop before decoding with exit status 2. |
| Release gate | Blocked as expected with exit status 3. Feed and alternate-route coverage, PIN persistence, account compatibility and messaging regressions are open. |
| Original APK smoke test | Passed on a fresh Android 36.1 Windows emulator with ARM64 translation. Android installed the original APK and a cold launch reached `LoginSignupActivity` in airplane mode. The test used no account. All signed-in behavior remains unverified. |
| Local-signature baseline | Installation and launch passed on a separate fresh Android 36.1 emulator. The APK verified with v1, v2 and v3 signatures, passed 16 KiB library alignment, and matched all 11,572 non-signature app entries byte for byte. Android installed it, the welcome screen rendered in airplane mode, and **Log In** opened the username/email and password form. |
| Online sign-in with local signature | Failed. On a fresh Android 36.1 emulator with a validated network connection, the user entered their own credentials and reported Snapchat's message “Due to unusual activity, your access to Snapchat is temporarily disabled.” The message had no visible error code. The exact trigger remains unknown. Possible factors include the local signature, emulator, or an account restriction. [Snapchat login errors](https://help.snapchat.com/hc/en-gb/articles/7012325477268-I-get-an-error-message-logging-in-to-Snapchat) |
| In-place update across signers | Android returned `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when asked to install the original APK over the locally signed copy on the disposable emulator. |

## Signing and safety compatibility

A modified package would require a different certificate. Android enforces certificate compatibility for updates to an installed package, so a locally signed mod cannot replace the installed Snapchat package in place. Uninstalling the official package to install a different signer clears app data and disrupts the normal update path. [Android app update rules](https://developer.android.com/google/play/app-updates)

The DEX contains a `GooglePlayIntegrityManager` path and references to both standard and classic Play Integrity token operations. Google documents that mismatched package or certificate records can produce `UNRECOGNIZED_VERSION`. Which verdicts Snap requires for this release and how its backend would respond to a modified package remain unverified. Snap also explicitly warns that third-party tweaks can lead to login problems and account locks. [Google Play Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts), [Snapchat support on third-party tweaks](https://help.snapchat.com/hc/en-gb/articles/7012343686932-Can-I-use-third-party-apps-or-plug-ins-with-Snapchat)

## Release requirements

- Implement **Messaging only** as the fresh-install default, **Friends only** as mutual-friend Stories, and **All content** as the original behavior. Make parental settings require a PIN, and test failed PIN attempts, persistence after process death and reboot, and recovery to the restrictive state when stored policy is invalid.
- Test tab taps, swipe gestures, launch and resume deep links, notification taps, shared chat links, profiles, search, cached playback, and picture-in-picture. Verify feed requests are suppressed where a dedicated content boundary exists. Validate mutual friendship at playback and keep unknown or stale relationships restricted.
- On a compatible and authorized environment, verify text and group chats, Snaps, camera, attachments, voice messages, calls, friend management, chat notifications, blocking, reporting and existing Family Center behavior against the official baseline. Resolve account compatibility within the brief's integrity constraints before releasing a modified client.
- Review the new APK certificate and hash before creating a version profile, rediscover hooks by behavior and structural signatures, and rerun the route and messaging checks. The provided experiment refuses any unseen APK hash, any missing hook or any ambiguous match.

See [installation and official app controls](installation.md) for the practical alternative while those requirements are open. Android DNS controls cannot enforce friendship or distinguish requests by in-app destination. An accessibility overlay also cannot validate the loaded content before it renders and may expose private UI to the overlay service.
