"""Check independently built addon JAR boundaries. Does not launch or install mods."""
import json
import sys
import zipfile


def validate(path):
    errors = []
    with zipfile.ZipFile(path) as jar:
        meta = json.loads(jar.read("fabric.mod.json"))
        mod_id = meta.get("id")
        if mod_id not in ("kru-cobblemon", "kru-tools"):
            errors.append("Unknown addon id")
        depends = meta.get("depends", {})
        if "meteor-client" not in depends:
            errors.append("Missing external Meteor dependency")
        if mod_id == "kru-tools" and "cobblemon" in depends:
            errors.append("Tools addon requires Cobblemon")
        sibling = "kru-tools" if mod_id == "kru-cobblemon" else "kru-cobblemon"
        if sibling in depends:
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
        # Neither addon needs embedded dependencies. Reject any nesting rather than
        # overlooking a copied Meteor/sibling dependency hidden in a jar-in-jar.
        if meta.get("jars") or any(name.lower().endswith(".jar") for name in names):
            errors.append("Nested JARs require explicit dependency review")
        sibling_package = "dev/krul/tools/" if mod_id == "kru-cobblemon" else "dev/krul/cobblemon/"
        for name in jar.namelist():
            if name.startswith("meteordevelopment/meteorclient/") and name.endswith(".class"):
                errors.append("Bundled Meteor class: " + name)
            if name.startswith(sibling_package):
                errors.append("Bundled sibling code: " + name)
            if name.endswith(".class") and sibling_package.encode() in jar.read(name):
                errors.append("Reference to sibling code: " + name)
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
