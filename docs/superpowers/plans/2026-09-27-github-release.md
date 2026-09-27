# GitHub release preparation

Approved scope: prepare this existing KRU checkout for GitHub, retaining Academy
presets and PC layout defaults. Preserve existing feature work. The user later
authorized pushing to `krulsaidme0w/kru-addons`; no installation or release tag.

## Tasks

- [x] Remove personal absolute paths from tracked source/docs; use explicit runtime
  JAR environment variables for optional ABI tests. Ignore local logs/configs.
- [x] Add PR/push builds for both independent Java 21 Gradle roots, test reports,
  binary/source artifacts, and artifact boundary checks.
- [x] Add tag-triggered draft releases with strict addon/version validation and
  SHA256 checksums. Test malformed tags, version mismatches and missing assets.
- [x] Replace stale entry-point documentation with current installation,
  Academy-defaults, compatibility, release and verification instructions.
- [x] Verify both clean builds, distribution tests, workflows and privacy scan.

Implementation stays in the existing feature checkout because the requested
publication includes its uncommitted features. Keep a local development-history
branch; publish the sanitized tree on top of GitHub's initial commit without
merging private local ancestry. Keep historical technical evidence sanitized.

## Verification

- Both independent `clean build --offline` runs passed.
- Cobblemon: 576 passed, 17 optional ABI tests skipped; Tools: 9 passed.
- Distribution: 14 tests passed; packages and both matching tags validated.
- actionlint 1.7.12 passed; external review found no important issues.
- Removed only dormant Tools UI/Inspector branches and their exclusive helpers.
- GitHub execution must be checked after push; no tag/release was created locally.

Runtime refactors identified by the prior audit remain a separate pre-release
follow-up; this change must not alter gameplay behavior.
