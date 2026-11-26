#!/usr/bin/env bash
set -euo pipefail

source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project=$(dirname -- "$source_dir")
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
tools_dir=${FEED_CHECK_BUILD_TOOLS:-$sdk/build-tools/36.0.0}
platform="$sdk/platforms/android-36/android.jar"
output=$(realpath -m -- "${1:-$project/work/feed-block.apk}")
if [[ "$output" != *.apk || -e "$output" ]]; then
    printf 'Choose a new .apk output path.\n' >&2
    exit 2
fi

workspace="$project/work"
key_dir="$workspace/feed-check-key"
mkdir -p -- "$workspace" "$(dirname -- "$output")"
scratch=$(mktemp -d "$workspace/.feed-check.XXXXXX")
trap 'rm -rf -- "$scratch"' EXIT

if [[ ! -d "$key_dir" ]]; then
    mkdir -m 700 -- "$scratch/key"
    (umask 077
     python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$scratch/key/password")
    keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$scratch/key/companion.p12" \
        -storepass:file "$scratch/key/password" -alias feed-check -dname 'CN=Local Feed Check' \
        -keyalg RSA -keysize 3072 -validity 3650
    chmod 600 -- "$scratch/key/companion.p12"
    mv -T -- "$scratch/key" "$key_dir"
fi

mkdir -- "$scratch/classes" "$scratch/dex" "$scratch/generated"
"$tools_dir/aapt2" compile --dir "$source_dir/res" -o "$scratch/resources.zip"
"$tools_dir/aapt2" link -I "$platform" --manifest "$source_dir/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 36 --java "$scratch/generated" \
    -o "$scratch/unsigned.apk" "$scratch/resources.zip"
javac --release 11 -cp "$platform" -d "$scratch/classes" \
    "$scratch/generated/local/feedcheck/android/R.java" "$source_dir"/src/local/feedcheck/android/*.java
jar --create --file "$scratch/classes.jar" -C "$scratch/classes" .
"$tools_dir/d8" --min-api 26 --lib "$platform" --output "$scratch/dex" "$scratch/classes.jar"
python3 - "$scratch" <<'PY'
from pathlib import Path
import sys
from zipfile import ZipFile, ZIP_DEFLATED

scratch = Path(sys.argv[1])
with ZipFile(scratch / "unsigned.apk", "a", compression=ZIP_DEFLATED) as apk:
    apk.write(scratch / "dex/classes.dex", "classes.dex")
PY
"$tools_dir/zipalign" 4 "$scratch/unsigned.apk" "$scratch/aligned.apk"
"$tools_dir/apksigner" sign --ks "$key_dir/companion.p12" --ks-key-alias feed-check \
    --ks-pass "file:$key_dir/password" --v4-signing-enabled false \
    --out "$scratch/signed.apk" "$scratch/aligned.apk"
"$tools_dir/apksigner" verify "$scratch/signed.apk"
"$tools_dir/zipalign" -c 4 "$scratch/signed.apk"
ln -- "$scratch/signed.apk" "$output"
printf 'Signed Feed Block APK %s\n' "$output"
