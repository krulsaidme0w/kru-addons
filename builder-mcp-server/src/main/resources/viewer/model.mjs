export function stageOperations(contract, stageId) {
  const stages = contract?.stages ?? [];
  let end = stages.length - 1;
  if (stageId && stageId !== "final" && stageId !== "all") end = stages.findIndex((stage) => stage.id === stageId);
  if (end < 0) return new Map();
  const result = new Map();
  for (let index = 0; index <= end; index++) {
    for (const operation of stages[index].operations ?? []) {
      const { x, y, z } = operation.position;
      result.set(`${x},${y},${z}`, { ...operation, stageId: stages[index].id });
    }
  }
  return result;
}

export function projectVoxel(x, y, z, angle, unit, originX, originY) {
  const cosine = Math.cos(angle), sine = Math.sin(angle);
  const rotatedX = x * cosine - z * sine;
  const rotatedZ = x * sine + z * cosine;
  return {
    x: originX + (rotatedX - rotatedZ) * unit * 0.5,
    y: originY - (rotatedX + rotatedZ) * unit * 0.25 - y * unit * 0.62,
    depth: rotatedX + rotatedZ + y * 0.5,
  };
}

export function stateLabel(state) {
  if (!state) return "air";
  const entries = Object.entries(state.properties ?? {}).sort(([a], [b]) => a.localeCompare(b));
  return state.id + (entries.length ? `[${entries.map(([key, value]) => `${key}=${value}`).join(",")}]` : "");
}

export function isAir(id) { return id === "minecraft:air" || id === "minecraft:cave_air" || id === "minecraft:void_air"; }

export function blockColor(id) {
  let hash = 2166136261;
  for (let index = 0; index < id.length; index++) hash = Math.imul(hash ^ id.charCodeAt(index), 16777619);
  const hue = (hash >>> 0) % 360;
  return `hsl(${hue} 22% 49%)`;
}
