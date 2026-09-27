import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import release


class ReleaseTest(unittest.TestCase):
    def test_tag_identifies_only_known_addons_and_semver(self):
        self.assertEqual(("cobblemon-addon", "0.3.3"), release.parse_tag("cobblemon-v0.3.3"))
        self.assertEqual(("tools-addon", "1.2.3-rc.1"), release.parse_tag("tools-v1.2.3-rc.1"))
        for tag in ("v1.2.3", "tools-v../x", "tools-v1.2", "tools-v01.2.3", "other-v1.2.3"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.parse_tag(tag)

    def fixture(self, root):
        project = root / "tools-addon"
        libs = project / "build/libs"
        libs.mkdir(parents=True)
        (project / "gradle.properties").write_text("mod_version=0.2.0\n")
        with zipfile.ZipFile(libs / "kru-tools-0.2.0.jar", "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({"id": "kru-tools", "version": "0.2.0",
                "authors": ["KRU"], "depends": {"meteor-client": "0.5.8"},
                "entrypoints": {"meteor": ["dev.krul.tools.ToolsAddon"]}}))
            jar.writestr("dev/krul/tools/ToolsAddon.class", b"example")
        with zipfile.ZipFile(libs / "kru-tools-0.2.0-sources.jar", "w") as jar:
            jar.writestr("Example.java", "class Example {}")
        return libs

    def test_package_contains_only_exact_binary_sources_and_checksums(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            libs = self.fixture(root)
            (libs / "stale.jar").write_bytes(b"not a release")
            out = release.package(root, "tools-addon")
            self.assertEqual({"kru-tools-0.2.0.jar", "kru-tools-0.2.0-sources.jar", "SHA256SUMS"},
                             {p.name for p in out.iterdir()})
            for line in (out / "SHA256SUMS").read_text().splitlines():
                digest, filename = line.split("  ")
                self.assertEqual(digest, hashlib.sha256((out / filename).read_bytes()).hexdigest())
            self.assertEqual(out, release.check_release(root, "tools-v0.2.0"))

    def test_mismatch_and_tampered_assets_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            out = release.package(root, "tools-addon")
            with self.assertRaises(ValueError):
                release.check_release(root, "tools-v0.2.1")
            (out / "kru-tools-0.2.0-sources.jar").write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                release.check_release(root, "tools-v0.2.0")

    def test_missing_sources_rejects_package(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            libs = self.fixture(root)
            (libs / "kru-tools-0.2.0-sources.jar").unlink()
            with self.assertRaises(ValueError):
                release.package(root, "tools-addon")

    def test_binary_metadata_version_must_match(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            libs = self.fixture(root)
            (root / "tools-addon/gradle.properties").write_text("mod_version=0.3.0\n")
            for p in libs.iterdir():
                p.rename(p.with_name(p.name.replace("0.2.0", "0.3.0")))
            with self.assertRaises(ValueError):
                release.package(root, "tools-addon")
