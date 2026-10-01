import test from "node:test";
import assert from "node:assert/strict";
import { blockColor, isAir, projectVoxel, stageOperations, stateLabel } from "../../src/main/resources/viewer/model.mjs";

test("stage projection composes transitions through the selected stage", () => {
  const plan = { stages: [
    { id: "base", operations: [{ op: "PLACE", position: { x: 1, y: 0, z: 2 }, target: "wood" }] },
    { id: "trim", operations: [{ op: "REPLACE", position: { x: 1, y: 0, z: 2 }, expected: { id: "minecraft:oak_planks", properties: {} }, target: "stone" }] },
  ] };
  assert.equal(stageOperations(plan, "base").get("1,0,2").op, "PLACE");
  assert.equal(stageOperations(plan, "trim").get("1,0,2").op, "REPLACE");
  assert.equal(stageOperations(plan, "final").get("1,0,2").target, "stone");
});

test("voxel projection rotates continuously while preserving vertical layer and depth", () => {
  const point = projectVoxel(2, 3, 0, 0, 10, 100, 100);
  const rotated = projectVoxel(2, 3, 0, Math.PI / 2, 10, 100, 100);
  assert.notEqual(point.x, rotated.x);
  assert.equal(point.y, 100 - (2 * 10 * 0.25) - (3 * 10 * 0.62));
  assert.equal(point.depth, 2 + 1.5);
});

test("state labels are exact and air is omitted from the physical scene", () => {
  assert.equal(stateLabel({ id: "minecraft:oak_log", properties: { axis: "x", waterlogged: "false" } }),
    "minecraft:oak_log[axis=x,waterlogged=false]");
  assert.equal(isAir("minecraft:cave_air"), true);
  assert.equal(isAir("mod:air_block"), false);
  assert.equal(blockColor("mod:block"), blockColor("mod:block"));
});
