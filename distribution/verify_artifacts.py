"""Check independently built addon JAR boundaries. Does not launch or install mods."""
import json
from pathlib import Path
import re
import sys
import zipfile

ADDONS = {
    "cobblemon-addon": {"id": "kru-cobblemon", "package": "dev/krul/cobblemon/", "other": ("dev/krul/tools/", "dev/krul/builder/")},
    "tools-addon": {"id": "kru-tools", "package": "dev/krul/tools/", "other": ("dev/krul/cobblemon/", "dev/krul/builder/")},
    "builder-addon": {"id": "kru-builder", "package": "dev/krul/builder/", "other": ("dev/krul/cobblemon/", "dev/krul/tools/")},
}
MOD_ADDONS = {value["id"]: value for value in ADDONS.values()}


def validate_sources(source, addon):
    if addon not in ADDONS:
        return ["Unknown addon source root"]
    if isinstance(source, dict):
        files = source.items()
    else:
        root = Path(source)
        files = ((str(path), path.read_text(encoding="utf-8")) for path in root.rglob("*.java"))
    errors = []
    for name, content in files:
        for namespace in ADDONS[addon]["other"]:
            package = namespace.rstrip("/").replace("/", ".")
            if re.search(r"\bimport\s+(?:static\s+)?" + re.escape(package) + r"(?:\.|\s*;)", content):
                errors.append(f"Cross-addon source import in {name}: {package}")
    return errors


def validate(path):
    errors = []
    with zipfile.ZipFile(path) as jar:
        meta = json.loads(jar.read("fabric.mod.json"))
        mod_id = meta.get("id")
        if mod_id not in MOD_ADDONS:
            errors.append("Unknown addon id")
        depends = meta.get("depends", {})
        if "meteor-client" not in depends:
            errors.append("Missing external Meteor dependency")
        if mod_id in ("kru-tools", "kru-builder") and "cobblemon" in depends:
            errors.append("Tools addon requires Cobblemon")
        addon = MOD_ADDONS.get(mod_id, {"other": ()})
        sibling_ids = {item["id"] for item in ADDONS.values() if item is not addon}
        if sibling_ids.intersection(depends):
            errors.append("Required sibling addon dependency")
        if not meta.get("entrypoints", {}).get("meteor"):
            errors.append("Missing Meteor addon entrypoint")
        if not meta.get("authors"):
            errors.append("Meteor requires addon authors")
        names = set(jar.namelist())
        for entrypoint in meta.get("entrypoints", {}).get("meteor", []):
            entrypoint_class = entrypoint if isinstance(entrypoint, str) else entrypoint.get("value", "")
            if entrypoint_class.replace(".", "/") + ".class" not in names:
                errors.append("Missing entrypoint class: " + entrypoint_class)
        # These addons need no embedded dependencies. Reject any nesting rather than
        # overlooking a copied Meteor/sibling dependency hidden in a jar-in-jar.
        if meta.get("jars") or any(name.lower().endswith(".jar") for name in names):
            errors.append("Nested JARs require explicit dependency review")
        other_packages = addon["other"]
        for name in jar.namelist():
            if name.startswith("meteordevelopment/meteorclient/") and name.endswith(".class"):
                errors.append("Bundled Meteor class: " + name)
            for sibling_package in other_packages:
                if name.startswith(sibling_package):
                    errors.append("Bundled sibling code: " + name)
                if name.endswith(".class") and sibling_package.encode() in jar.read(name):
                    errors.append("Reference to sibling code: " + name)
            if mod_id == "kru-builder" and (name.startswith("com/cobblemon/") or name.startswith("com/cobblemonmod/")):
                errors.append("Bundled Cobblemon class: " + name)
    return errors


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("Usage: python3 distribution/verify_artifacts.py <addon.jar> ...")
    failed = False
    for artifact in sys.argv[1:]:
        problems = validate(artifact)
        print(artifact + ": " + ("; ".join(problems) if problems else "independent addon OK"))
        failed |= bool(problems)
    raise SystemExit(1 if failed else 0)
