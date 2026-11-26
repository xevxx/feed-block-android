#!/usr/bin/env bash
set -euo pipefail

project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
tools_dir="$project/work/tools"
jadx_version=1.5.7
jadx_sha256=1755e4242ade71a077438723689f404079592ec7764a3a0572f98fef94ebcbf3

case "${1:---check}" in
    --install-ubuntu)
        sudo apt-get update
        sudo apt-get install -- apktool default-jdk-headless python3 ripgrep aapt apksigner zipalign adb curl unzip git
        ;;
    --install-jadx)
        mkdir -p -- "$tools_dir"
        jadx_dir="$tools_dir/jadx-$jadx_version"
        if [[ ! -x "$jadx_dir/bin/jadx" ]]; then
            scratch=$(mktemp -d "$tools_dir/.jadx.XXXXXX")
            trap 'rm -rf -- "$scratch"' EXIT
            release="https://github.com/skylot/jadx/releases/download/v$jadx_version"
            curl --fail --silent --show-error --location --proto '=https' --max-time 180 \
                "$release/jadx-$jadx_version.zip" --output "$scratch/jadx.zip"
            printf '%s  %s\n' "$jadx_sha256" "$scratch/jadx.zip" | sha256sum --check --status
            unzip -q "$scratch/jadx.zip" -d "$scratch/jadx"
            mv -T -- "$scratch/jadx" "$jadx_dir"
        fi
        "$jadx_dir/bin/jadx" --version
        ;;
    --check)
        sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
        status=0
        for name in python3 java apktool rg git; do
            if ! command -v "$name"; then
                printf 'Missing %s\n' "$name" >&2
                status=1
            fi
        done
        for name in aapt apksigner zipalign; do
            paths=("$sdk"/build-tools/*/"$name")
            if ! [[ -x "${paths[-1]}" ]] && ! command -v "$name"; then
                printf 'Missing %s\n' "$name" >&2
                status=1
            elif [[ -x "${paths[-1]}" ]]; then
                printf '%s\n' "${paths[-1]}"
            fi
        done
        if [[ ! -f /usr/share/apktool/smali.jar && -z ${SMALI_CLASSPATH:-} ]]; then
            printf 'Missing Apktool Smali jars. Set SMALI_CLASSPATH if installed separately.\n' >&2
            status=1
        fi
        if [[ -x "$tools_dir/jadx-$jadx_version/bin/jadx" ]]; then
            printf '%s\n' "$tools_dir/jadx-$jadx_version/bin/jadx"
        else
            command -v jadx || printf 'Optional tool JADX is available via --install-jadx.\n'
        fi
        exit "$status"
        ;;
    *)
        printf 'Usage: %s [--check | --install-ubuntu | --install-jadx]\n' "$0" >&2
        exit 2
        ;;
esac
