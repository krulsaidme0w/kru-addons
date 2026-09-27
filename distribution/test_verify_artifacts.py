import io
import json
import unittest
import zipfile

from verify_artifacts import validate


class ArtifactBoundaryTest(unittest.TestCase):
    def artifact(self, mod_id="kru-tools", dependencies=None, classes=None):
        entrypoint = {"kru-tools": "dev.krul.tools.ToolsAddon", "kru-cobblemon": "dev.krul.cobblemon.CobblemonAddon",
                      "kru-builder": "dev.krul.builder.BuilderAddon"}.get(mod_id, "dev.krul.tools.ToolsAddon")
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({"id": mod_id,
                "depends": dependencies or {"minecraft": "1.21.1", "meteor-client": "0.5.8"},
                "authors": ["krul"], "entrypoints": {"meteor": [entrypoint]}}))
            default_class = entrypoint.replace(".", "/") + ".class"
            for path in classes or [default_class]:
                jar.writestr(path, b"fixture")
        stream.seek(0)
        return stream

    def test_independent_addon_is_accepted(self):
        self.assertEqual([], validate(self.artifact()))

    def test_bundled_meteor_classes_are_rejected(self):
        errors = validate(self.artifact(classes=["meteordevelopment/meteorclient/MeteorClient.class"]))
        self.assertTrue(any("Meteor class" in error for error in errors))

    def test_cross_addon_dependency_is_rejected(self):
        errors = validate(self.artifact(dependencies={"meteor-client": "0.5.8", "kru-cobblemon": "*"}))
        self.assertTrue(any("sibling" in error for error in errors))

    def test_missing_external_meteor_is_rejected(self):
        self.assertTrue(validate(self.artifact(dependencies={"minecraft": "1.21.1"})))

    def test_tools_cannot_bundle_cobblemon_feature_classes(self):
        errors = validate(self.artifact(classes=["dev/krul/cobblemon/Discovery.class"]))
        self.assertTrue(any("sibling" in error for error in errors))

    def test_tools_cannot_require_cobblemon(self):
        errors = validate(self.artifact(dependencies={"meteor-client": "0.5.8", "cobblemon": "*"}))
        self.assertTrue(any("Cobblemon" in error for error in errors))

    def test_entrypoint_must_exist_in_the_artifact(self):
        errors = validate(self.artifact(classes=["dev/krul/tools/NotTheEntrypoint.class"]))
        self.assertTrue(any("entrypoint class" in error for error in errors))

    def test_authors_required_by_meteor_loader(self):
        source = self.artifact()
        target = io.BytesIO()
        with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, "w") as modified:
            for name in original.namelist():
                data = original.read(name)
                if name == "fabric.mod.json":
                    meta = json.loads(data)
                    del meta["authors"]
                    data = json.dumps(meta)
                modified.writestr(name, data)
        target.seek(0)
        self.assertTrue(any("authors" in error for error in validate(target)))

    def test_nested_jars_are_not_allowed_in_these_independent_addons(self):
        stream = self.artifact()
        with zipfile.ZipFile(stream, "a") as jar:
            jar.writestr("META-INF/jars/meteor.jar", self.artifact().getvalue())
        stream.seek(0)
        self.assertTrue(any("Nested" in error for error in validate(stream)))

    def test_builder_artifact_is_an_independent_meteor_addon(self):
        stream = self.artifact(mod_id="kru-builder", dependencies={"minecraft": "1.21.1", "meteor-client": "0.5.8"},
            classes=["dev/krul/builder/BuilderAddon.class"])
        stream.seek(0)
        self.assertEqual([], validate(stream))

    def test_builder_cannot_bundle_meteor_or_cobblemon_classes(self):
        for forbidden in ("meteordevelopment/meteorclient/MeteorClient.class", "com/cobblemon/mod/common/Cobblemon.class"):
            with self.subTest(forbidden=forbidden):
                stream = self.artifact(mod_id="kru-builder", dependencies={"minecraft": "1.21.1", "meteor-client": "0.5.8"},
                    classes=["dev/krul/builder/BuilderAddon.class", forbidden])
                stream.seek(0)
                self.assertTrue(validate(stream))

    def test_builder_cannot_require_cobblemon(self):
        errors = validate(self.artifact(mod_id="kru-builder",
            dependencies={"meteor-client": "0.5.8", "cobblemon": "*"},
            classes=["dev/krul/builder/BuilderAddon.class"]))
        self.assertTrue(any("Cobblemon" in error for error in errors))

    def test_sources_cannot_import_another_addon_namespace(self):
        from verify_artifacts import validate_sources
        self.assertTrue(validate_sources({"src/main/java/dev/krul/builder/Bad.java":
            "import dev.krul.tools.ToolsAddon;"}, "builder-addon"))


if __name__ == "__main__":
    unittest.main()
