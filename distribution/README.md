# Build and release guide

Requirements: Java 21, Python 3.10+, and Maven network access for the first build.
Each addon has an independent Gradle root and included wrapper. From the repo root:

```sh
(cd cobblemon-addon && bash ./gradlew clean build)
(cd tools-addon && bash ./gradlew clean build)
(cd builder-addon && bash ./gradlew clean build)
python3 -m unittest discover -s distribution -v
python3 distribution/release.py cobblemon-addon
python3 distribution/release.py tools-addon
python3 distribution/release.py builder-addon
```

Install only remapped binaries from `build/libs/`, never development JARs from
`build/devlibs/` or `*-sources.jar`. Verified packages appear in
`dist/<addon>/`: binary, sources and `SHA256SUMS`; Builder also includes
`LICENSE`, `NOTICE` and `THIRD_PARTY.md` in its checksum manifest. Packaging rejects missing or
corrupt archives, metadata mismatches, cross-addon references and bundled Meteor
classes. It does not launch Minecraft or verify server behavior.

`dependencies.json` records the external Meteor version and checksum. No local
Minecraft install, sibling repository or personal path is a build input.
Only KRU Cobblemon requires Cobblemon at runtime.

Builder packaging also checks Java source imports between addon namespaces and
rejects bundled Cobblemon classes. The binary/source package carries Builder's
license and provenance notices.

## Optional runtime ABI checks

Some tests inspect actual runtime bytecode without loading Minecraft. Supply JARs
from your supported instance through these environment variables:

- `COBBLEMON_RUNTIME_JAR`: native Cobblemon battle/spawn ABI.
- `KRU_COBBLEMON_RUNTIME_JAR`: legacy-named Cobblemon PC/daycare ABI checks.
- `COBBLENAV_RUNTIME_JAR`: optional Cobblenav spawn-map ABI.

Unset/unavailable runtime JARs cause those tests to skip. Public CI does not
download or redistribute Academy's runtime. Check skipped-test reports and run
live checks before declaring compatibility.

## GitHub Actions

PRs, branch pushes and manual dispatch build/test all three addons with read-only
repository permissions. Actions are pinned to commit hashes; Dependabot proposes
updates. Addon artifacts and test reports are retained for 14 days.

Versions are independent. Edit `mod_version` in the selected addon's
`gradle.properties`, update the changelog, commit and create an annotated tag:

```sh
git tag -a cobblemon-v0.3.3 -m 'KRU Cobblemon 0.3.3'
git push origin cobblemon-v0.3.3
# Tools: tools-v0.2.0; Builder: builder-v0.1.0-alpha.1
# Prereleases: tools-v0.3.0-rc.1
```

Tag/version mismatch or any failed addon build prevents release creation.
Only the tag job receives `contents: write`. It downloads artifacts from the same
run, rechecks metadata/checksums and creates a **draft** containing only the
selected addon's assets. Review notes and mark prereleases appropriately before
publishing. PRs and branch pushes never publish releases.

No PAT is required: Actions uses the repository-scoped `GITHUB_TOKEN`.
See [GitHub CLI release creation](https://cli.github.com/manual/gh_release_create)
for `--draft` and `--verify-tag`.

If creation failed after creating a draft, inspect that draft before retrying.
Existing releases are not silently overwritten.
