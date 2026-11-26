#!/usr/bin/env python3
"""Verify an official input and exercise a Spotlight patch on isolated classes."""

import argparse
from copy import copy
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
           or Path.home() / "Android/Sdk")
LAB_ALIAS = "snapchat-local-test"


class LabError(Exception):
    pass


def stream_sha256(stream):
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def sha256(file):
    with file.open("rb") as stream:
        return stream_sha256(stream)


def tool(name):
    builds = [path for path in (SDK / "build-tools").glob("*")
              if re.fullmatch(r"\d+(\.\d+)*", path.name)]
    for path in sorted(builds, key=lambda item: tuple(map(int, item.name.split("."))), reverse=True):
        executable = path / name
        if executable.is_file() and os.access(executable, os.X_OK):
            return executable
    fallback = shutil.which(name)
    if fallback is None:
        raise LabError(f"Missing tool {name}. See scripts/dependencies.sh.")
    return fallback


def capture(*command):
    result = subprocess.run(command, text=True, capture_output=True, env={**os.environ, "LC_ALL": "C"})
    if result.returncode:
        raise LabError(f"{Path(command[0]).name} exited with {result.returncode}:\n"
                       f"{result.stderr or result.stdout}")
    return result.stdout


def extract(pattern, output):
    match = re.search(pattern, output, re.MULTILINE)
    if match is None:
        raise LabError(f"Missing metadata matching {pattern}")
    return match[1]


def verify_input(apk):
    digest = sha256(apk)
    versions = [json.loads(path.read_text()) for path in sorted((ROOT / "versions").glob("*.json"))]
    matches = [version for version in versions if version["sha256"] == digest]
    if len(matches) != 1:
        raise LabError(f"Unreviewed or ambiguous APK SHA-256 {digest}. No experiment was applied.")
    version = matches[0]

    badging = capture(tool("aapt"), "dump", "badging", apk)
    signer = capture(tool("apksigner"), "verify", "--verbose", "--print-certs", apk)
    with ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise LabError("APK contains duplicate archive entries")
        abis = sorted({name.split("/")[1] for name in names if name.startswith("lib/")
                       and name.endswith(".so")})
        dex_count = sum(bool(re.fullmatch(r"classes\d*\.dex", name)) for name in names)
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$", signer, re.MULTILINE)
    if signers != [version["signer_sha256"]]:
        raise LabError("Unexpected APK signing certificate")
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in signer:
        raise LabError("Expected Android APK Signature Scheme v2 verification")
    if re.search(r"^uses-split:", badging, re.MULTILINE):
        raise LabError("This workflow requires a standalone APK")
    actual = {
        "package": extract(r"^package: name='([^']+)'", badging),
        "version_name": extract(r"\bversionName='([^']+)'", badging),
        "version_code": int(extract(r"\bversionCode='(\d+)'", badging)),
        "min_sdk": int(extract(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", badging)),
        "target_sdk": int(extract(r"^targetSdkVersion:'(\d+)'", badging)),
        "abis": abis,
        "dex_count": dex_count,
        "sha256": digest,
        "signer_sha256": signers[0],
    }
    for key, value in actual.items():
        if value != version[key]:
            raise LabError(f"{key} differs from reviewed manifest: {value!r}")
    return version, actual


def preserve(apk, metadata):
    originals = ROOT / "originals"
    originals.mkdir(exist_ok=True)
    backup = originals / f"snapchat-{metadata['version_code']}-{metadata['sha256'][:12]}.apk"
    if not backup.exists():
        with tempfile.TemporaryDirectory(dir=originals) as scratch:
            staged = Path(scratch) / "original.apk"
            shutil.copyfile(apk, staged)
            if sha256(staged) != metadata["sha256"]:
                raise LabError("Input changed during the backup")
            staged.chmod(0o444)
            try:
                os.link(staged, backup)
            except FileExistsError:
                pass
    if sha256(backup) != metadata["sha256"]:
        raise LabError(f"Backup checksum mismatch: {backup}")
    return backup


def signature_entry(name):
    upper = name.upper()
    return (name == "stamp-cert-sha256" or upper == "META-INF/MANIFEST.MF"
            or re.fullmatch(r"META-INF/([^/]+\.(SF|RSA|DSA|EC)|SIG-[^/]+)", upper) is not None)


def lab_signing_key(workspace):
    location = workspace / "local-signing"
    if not location.exists():
        with tempfile.TemporaryDirectory(prefix="key-", dir=workspace) as temporary:
            staged = Path(temporary) / "local-signing"
            staged.mkdir(mode=0o700)
            password = staged / "password"
            password.touch(mode=0o600, exist_ok=False)
            password.write_text(secrets.token_urlsafe(32) + "\n")
            key = staged / "lab.p12"
            capture(tool("keytool"), "-genkeypair", "-noprompt", "-storetype", "PKCS12",
                    "-keystore", key, "-storepass:file", password, "-alias", LAB_ALIAS,
                    "-dname", "CN=Local Snapchat Lab", "-keyalg", "RSA", "-keysize", "3072",
                    "-validity", "3650")
            key.chmod(0o600)
            try:
                staged.rename(location)
            except OSError as exc:
                if exc.errno not in (errno.EEXIST, errno.ENOTEMPTY):
                    raise
    key = location / "lab.p12"
    password = location / "password"
    if not key.is_file() or not password.is_file():
        raise LabError(f"Incomplete signing key directory: {location}")
    return key, password


def verify_identical_payloads(original, signed):
    with ZipFile(original) as source, ZipFile(signed) as destination:
        original_entries = [info for info in source.infolist() if not signature_entry(info.filename)]
        signed_names = [info.filename for info in destination.infolist() if not signature_entry(info.filename)]
        if sorted(info.filename for info in original_entries) != sorted(signed_names):
            raise LabError("The re-signed copy has a different payload entry list")
        for info in original_entries:
            replacement = destination.getinfo(info.filename)
            if (info.file_size, info.compress_type) != (replacement.file_size, replacement.compress_type):
                raise LabError(f"APK entry metadata changed: {info.filename}")
            with source.open(info) as before, destination.open(replacement) as after:
                if stream_sha256(before) != stream_sha256(after):
                    raise LabError(f"APK entry content changed: {info.filename}")
        return len(original_entries)


def sign_baseline(original, metadata, workspace, output):
    if output.suffix.lower() != ".apk" or output.exists():
        raise LabError(f"Choose a new .apk output path: {output}")
    key, password = lab_signing_key(workspace)
    with tempfile.TemporaryDirectory(prefix="sign-", dir=output.parent) as temporary:
        directory = Path(temporary)
        unsigned = directory / "unsigned.apk"
        aligned = directory / "aligned.apk"
        signed = directory / "signed.apk"
        with ZipFile(original) as source, ZipFile(unsigned, "w") as destination:
            for info in source.infolist():
                if not signature_entry(info.filename):
                    with source.open(info) as before, destination.open(copy(info), "w") as after:
                        shutil.copyfileobj(before, after, 1024 * 1024)

        zipalign = tool("zipalign")
        apksigner = tool("apksigner")
        capture(zipalign, "-P", "16", "4", unsigned, aligned)
        capture(apksigner, "sign", "--ks", key, "--ks-key-alias", LAB_ALIAS,
                "--ks-pass", f"file:{password}", "--v4-signing-enabled", "false",
                "--out", signed, aligned)
        capture(zipalign, "-c", "-P", "16", "4", signed)
        verification = capture(apksigner, "verify", "--verbose", "--print-certs", signed)
        for scheme in (1, 2, 3):
            if not re.search(rf"^Verified using v{scheme} scheme .*: true$", verification, re.MULTILINE):
                raise LabError(f"Lab APK failed v{scheme} signature verification")
        fingerprint = extract(r"^Signer #1 certificate SHA-256 digest: ([0-9a-f]+)$", verification)
        if fingerprint == metadata["signer_sha256"]:
            raise LabError("The lab APK unexpectedly has the original APK signer")
        count = verify_identical_payloads(original, signed)
        digest = sha256(signed)
        os.link(signed, output)
    print(f"Verified {count} unchanged payload entries, 16 KiB native library alignment and v1/v2/v3 signatures.")
    print(f"Locally signed test APK: {output}")
    print(f"APK SHA-256: {digest}")
    print(f"Local signer SHA-256: {fingerprint}")


def decode(backup, metadata, folder):
    subprocess.run([tool("apktool"), "d", "-r", "-p", folder / "framework", "-o", folder / "decoded",
                    backup], check=True)
    decoded = folder / "decoded"
    roots = [path for path in decoded.glob("smali*") if path.is_dir()]
    if len(roots) != metadata["dex_count"] or not all(next(path.rglob("*.smali"), None) for path in roots):
        raise LabError("APKTool output does not contain all reviewed DEX files")
    return decoded


def find_playback_methods(decoded, signature):
    interface = signature["interface"]
    method = signature["method"]
    rx = re.compile(r"(^\.method [^\n]* " + re.escape(method) + r"\n)(.*?)(^\.end method$)",
                    re.MULTILINE | re.DOTALL)
    files = capture(tool("rg"), "--files-with-matches", "--fixed-strings", "--line-regexp",
                    "--glob", "*.smali", f".implements {interface}", decoded).splitlines()
    found = {}
    for filename in sorted(files):
        path = Path(filename)
        source = path.read_text()
        methods = list(rx.finditer(source))
        if len(methods) != 1:
            raise LabError(f"Unexpected playback method count in {path.relative_to(decoded)}")
        body = methods[0].group(2)
        labels = [label for label, anchors in signature["implementations"].items()
                  if all(anchor in body for anchor in anchors)]
        if len(labels) != 1 or labels[0] in found:
            raise LabError(f"Missing or ambiguous structural signature in {path.relative_to(decoded)}")
        found[labels[0]] = path, source, methods[0]
    if found.keys() != signature["implementations"].keys():
        raise LabError("The reviewed set of Spotlight playback implementations changed")
    return found


def exercise_patch(decoded, version, folder):
    signature = version["playback_experiment"]
    implementations = find_playback_methods(decoded, signature)
    inputs = folder / "experiment-inputs"
    inputs.mkdir()
    descriptors = []
    for label, (path, source, match) in sorted(implementations.items()):
        descriptor = extract(r"^\.class [^\n]* (L[^\n]+;)$", source)
        destination = inputs / (descriptor[1:-1] + ".smali")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(source[:match.start(2)] + "    .locals 0\n\n    return-void\n"
                               + source[match.end(2):])
        descriptors.append(descriptor)
        print(f"{label}: {path.relative_to(decoded)}")

    # Exercise only the two classes in a temporary DEX. No repackaging or signing.
    classpath = os.environ.get("SMALI_CLASSPATH", "/usr/share/apktool/*")
    subprocess.run([tool("java"), "-cp", classpath, ROOT / "scripts/DexRoundTrip.java", inputs,
                    folder / "playback-lab.dex", folder / "roundtrip", str(version["min_sdk"]),
                    signature["method"], *descriptors], check=True)
    print("Verified two assembled playback stubs and a Baksmali round trip. No APK was built.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("inspect", "prepare", "experiment", "sign-baseline", "release-check"))
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, help="new APK path for sign-baseline")
    args = parser.parse_args()
    if (args.command == "sign-baseline") != (args.output is not None):
        parser.error("--output is required for sign-baseline and only allowed with that command")
    try:
        apk = args.apk.resolve(strict=True)
        version, metadata = verify_input(apk)
        if args.command == "release-check":
            print("Release blocked:\n" + "\n".join(f"- {item}" for item in version["release_blockers"]),
                  file=sys.stderr)
            return 3
        if args.command == "inspect":
            print(json.dumps(metadata, indent=2))
            return 0

        backup = preserve(apk, metadata)
        workspace = ROOT / "work"
        workspace.mkdir(exist_ok=True)
        if args.command == "sign-baseline":
            sign_baseline(backup, metadata, workspace, args.output.resolve())
            return 0
        target = workspace / f"decoded-{version['version_code']}"
        if args.command == "prepare" and target.exists():
            raise LabError(f"Output already exists: {target}")
        with tempfile.TemporaryDirectory(prefix="decode-", dir=workspace) as temp:
            decoded = decode(backup, metadata, Path(temp))
            if args.command == "experiment":
                exercise_patch(decoded, version, Path(temp))
            else:
                decoded.rename(target)
                print(f"Decoded {metadata['dex_count']} DEX files to {target}")
        print(f"Verified original backup: {backup}")
        return 0
    except (BadZipFile, LabError, OSError, subprocess.CalledProcessError) as exc:
        print(f"Stopped: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
