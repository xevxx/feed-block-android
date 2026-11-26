# Snapchat parental content controls

This project audits Snapchat 14.33.1.0 on Android and contains an isolated experiment for opening shared Spotlight content from chat. The requested three-mode parental control is still incomplete. The release check blocks the parental-control build.

The APK supplied in the parent folder is byte-for-byte identical to the [APKMirror standalone download](https://www.apkmirror.com/apk/snap-inc/snapchat/snapchat-14-33-1-0-release/snapchat-14-33-1-0-2-android-apk-download/). Its read-only backup is `originals/snapchat-329222-a96c3e3aa446.apk`.

## Run the audit from WSL

Run these commands from this project directory with Bash. For a different input, set `apk` to its exact pathname.

```bash
apk='../com.snapchat.android_14.33.1.0-329222_minAPI23(arm64-v8a,armeabi-v7a)(nodpi)_apkmirror.com.apk'
bash scripts/dependencies.sh --check
python3 scripts/apk-lab.py inspect "$apk"
python3 scripts/apk-lab.py experiment "$apk"
python3 scripts/apk-lab.py release-check "$apk"
```

`inspect` checks the APK hash, Android signing certificate, version, SDK levels, ABIs and DEX count. `prepare` takes the same APK argument, preserves a verified backup and disassembles the 11 DEX files under `work/decoded-329222`. This directory already exists in the current workspace. `experiment` uses a fresh temporary decode, assembles two scoped edits, verifies both method bodies and disassembles the result with Baksmali. It removes its intermediate DEX after verification. `release-check` exits with status 3 for this known, incomplete version. An unknown input exits with status 2 before decoding.

To test the effect of re-signing on its own, use the original, verified input and a new output path.

```bash
python3 scripts/apk-lab.py sign-baseline "$apk" --output ../snapchat-14.33.1.0-local-signature-test.apk
```

The command verifies the signed APK against the original entry by entry. It keeps the same app payload and package name, so all original content remains available. The local signing key is kept in the ignored `work/local-signing` directory. Keep that directory private. See the installation guide for the package signature restriction.

On Ubuntu use `bash scripts/dependencies.sh --install-ubuntu` for system packages and `bash scripts/dependencies.sh --install-jadx` for the checksum-pinned optional decompiler. The tools prioritize an SDK at `ANDROID_SDK_ROOT`, `ANDROID_HOME` or `~/Android/Sdk`. The experiment uses Ubuntu's APKTool Smali libraries by default. `SMALI_CLASSPATH` can point to an equivalent set of compatible JARs.

- [Technical and compatibility report](docs/compatibility-329222.md)
- [Installation and official app controls](docs/installation.md)
- [Feed Check Accessibility diagnostic](companion/README.md)
- [Changelog](CHANGELOG.md)
