"""Package verified addon assets or validate a tag before drafting a release.

No network, installation, Git mutation or publication happens in this script.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile

from verify_artifacts import ADDONS as ARTIFACT_ADDONS, validate, validate_sources

ADDONS = {addon: details["id"] for addon, details in ARTIFACT_ADDONS.items()}
TAG_PREFIXES = {"cobblemon": "cobblemon-addon", "tools": "tools-addon", "builder": "builder-addon"}
VERSION = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?"


def parse_tag(tag):
    match = re.fullmatch(r"(cobblemon|tools|builder)-v(" + VERSION + r")", tag)
    if not match:
        raise ValueError("Expected cobblemon-vX.Y.Z, tools-vX.Y.Z or builder-vX.Y.Z (optional prerelease suffix)")
    return TAG_PREFIXES[match[1]], match[2]


def version(root, addon):
    if addon not in ADDONS:
        raise ValueError("Unknown addon")
    properties = (root / addon / "gradle.properties").read_text()
    match = re.search(r"^mod_version=(.+)$", properties, re.MULTILINE)
    if not match or not re.fullmatch(VERSION, match[1]):
        raise ValueError("Missing or invalid mod_version")
    return match[1]


def asset_names(addon, value):
    base = f"{ADDONS[addon]}-{value}"
    return [base + ".jar", base + "-sources.jar"]


def notice_names(addon):
    return ["LICENSE", "NOTICE", "THIRD_PARTY.md"] if addon == "builder-addon" else []


def checksums(folder, names):
    return "".join(f"{hashlib.sha256((folder / name).read_bytes()).hexdigest()}  {name}\n" for name in names)


def verify_assets(folder, addon, value):
    names = asset_names(addon, value)
    for name in names:
        path = folder / name
        if not path.is_file() or not zipfile.is_zipfile(path):
            raise ValueError(f"Missing or invalid asset: {name}")
        with zipfile.ZipFile(path) as jar:
            if jar.testzip() is not None:
                raise ValueError(f"Corrupt asset: {name}")
    binary = folder / names[0]
    errors = validate(binary)
    if errors:
        raise ValueError("; ".join(errors))
    with zipfile.ZipFile(binary) as jar:
        meta = json.loads(jar.read("fabric.mod.json"))
    if meta["id"] != ADDONS[addon] or meta.get("version") != value:
        raise ValueError("Binary addon/version does not match release")
    source_root = folder.parent.parent / "src/main/java" if folder.name == "libs" else None
    if source_root is not None:
        source_errors = validate_sources(source_root, addon)
        if source_errors:
            raise ValueError("; ".join(source_errors))
    return names


def package(root, addon):
    value = version(root, addon)
    source = root / addon / "build/libs"
    names = verify_assets(source, addon, value)
    notices = notice_names(addon)
    for name in notices:
        if not (root / addon / name).is_file():
            raise ValueError(f"Missing required notice: {name}")
    dest = root / "dist" / addon
    dest.mkdir(parents=True, exist_ok=True)
    if any(p.name not in names + notices + ["SHA256SUMS"] for p in dest.iterdir()):
        raise ValueError("Output contains stale assets; use a fresh dist directory")
    for name in names:
        shutil.copyfile(source / name, dest / name)
    for name in notices:
        shutil.copyfile(root / addon / name, dest / name)
    (dest / "SHA256SUMS").write_text(checksums(dest, names + notices))
    return dest


def check_release(root, tag):
    addon, tagged_version = parse_tag(tag)
    if tagged_version != version(root, addon):
        raise ValueError("Tag version differs from gradle.properties")
    dest = root / "dist" / addon
    names = verify_assets(dest, addon, tagged_version)
    source_errors = validate_sources(root / addon / "src/main/java", addon)
    if source_errors:
        raise ValueError("; ".join(source_errors))
    notices = notice_names(addon)
    if {p.name for p in dest.iterdir()} != set(names + notices + ["SHA256SUMS"]):
        raise ValueError("Unexpected release assets")
    if (dest / "SHA256SUMS").read_text() != checksums(dest, names + notices):
        raise ValueError("Release checksums do not match")
    return dest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", help="addon directory, or release tag with --check-tag")
    parser.add_argument("--check-tag", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        dest = check_release(root, args.target) if args.check_tag else package(root, args.target)
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Release validation failed: {error}\n")
    print(dest.relative_to(root).as_posix())
