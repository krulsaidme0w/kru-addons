# Task 6 report

- Implemented: bounded PNG attachments (4 items, 1280 px edge, 8 MiB combined), immutable imports, removable drafts, request leases, capture lifetime and bounded stale-store cleanup; pre-HUD world-only framebuffer capture with background PNG encoding; pure full-state blueprint diff; validated textured ghosts and distinct replace/removal outlines.
- Tests: focused `AttachmentPolicyTest` and `BlueprintDiffTest` passed; full `builder-addon/./gradlew test` passed. `git diff --check` clean.
- Changed paths: `builder-addon/src/main/java/dev/krul/builder/{visual/AttachmentPolicy.java,blueprint/BlueprintDiff.java,blueprint/BlueprintRenderer.java,minecraft/WorldCapture.java,minecraft/BuilderModule.java}`, `builder-addon/src/test/java/dev/krul/builder/{visual/AttachmentPolicyTest.java,blueprint/BlueprintDiffTest.java}`, `builder-addon/THIRD_PARTY.md`.
- Limits: capture event subscription/caller wiring remains for Task 7. Live framebuffer timing, GL/resource-pack/shader appearance and actual capture output need a separate Minecraft client; not verified here. Framebuffer input is bounded to 16,777,216 pixels.
- Legacy provenance: selectively adapted `BlueprintGhostRenderer.java` from CobbHelper commit `623562be05f2aca40846ae71a101266fc7be52e1`; source had no header or repository NOTICE; root GPLv3 retained under Builder GPL-3.0-or-later.
