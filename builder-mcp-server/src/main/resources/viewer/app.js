import { blockColor, isAir, projectVoxel, stageOperations, stateLabel } from "./model.mjs";

const $ = (id) => document.getElementById(id);
const canvas = $("scene"), context = canvas.getContext("2d", { alpha: false });
const root = location.pathname.endsWith("/") ? location.pathname : `${location.pathname}/`;
const api = (path) => new URL(`${root}api/${path}`, location.origin);
const listElement = $("history-list");
const ui = {
  count: $("record-count"), connection: $("connection-label"), title: $("plan-title"), meta: $("plan-meta"),
  empty: $("scene-empty"), historyEmpty: $("history-empty"), historyError: $("history-error"),
  stage: $("stage-select"), layer: $("layer-range"), layerOutput: $("layer-output"), allLayers: $("all-layers"), zoom: $("zoom-range"),
  reset: $("reset-camera"), state: $("validation-state"), counts: $("plan-counts"), preview: $("plan-preview"), previewEmpty: $("preview-empty"),
  notes: $("validation-notes"), timeline: $("stage-timeline"), toast: $("toast"), inspector: $("cell-inspector"),
  architecture: $("architecture-review"), cameraButtons: [...document.querySelectorAll(".camera-button")],
};
let summaries = [], active = null, report = null, snapshot = null, contract = null, angle = Math.PI / 4;
let hovered = null, points = [], drag = null, frame = 0, toastTimer = 0;

async function requestJson(url) {
  const response = await fetch(url, { credentials: "same-origin", cache: "no-store" });
  if (!response.ok) throw new Error(response.status === 404 ? "Archive record not found." : `Local viewer returned HTTP ${response.status}.`);
  return response.json();
}

async function loadHistory() {
  ui.historyError.hidden = true;
  ui.connection.textContent = "Reading visual ideas";
  try {
    summaries = await requestJson(api("plans"));
    ui.count.textContent = String(summaries.length);
    ui.connection.textContent = "Loopback · visual only";
    listElement.replaceChildren();
    ui.historyEmpty.hidden = summaries.length !== 0;
    for (const summary of summaries) {
      const button = document.createElement("button");
      button.className = "history-item"; button.type = "button";
      button.setAttribute("aria-current", String(active?.id === summary.id));
      const title = document.createElement("span"); title.className = "history-name"; title.textContent = summary.title;
      const meta = document.createElement("span"); meta.className = "history-meta";
      const date = new Date(summary.createdAtEpochMillis);
      const when = document.createElement("span"); when.textContent = Number.isNaN(date.getTime()) ? "Unknown date" : date.toLocaleString();
      const revision = document.createElement("span"); revision.textContent = `r${summary.snapshotRevision}`;
      meta.append(when, revision); button.append(title, meta);
      button.addEventListener("click", () => loadPlan(summary.id)); listElement.append(button);
    }
    if (active) listElement.querySelectorAll(".history-item").forEach((button, index) =>
      button.setAttribute("aria-current", String(summaries[index]?.id === active.id)));
  } catch (error) {
    ui.connection.textContent = "Archive unavailable";
    ui.historyError.textContent = `${error.message} Refresh to retry.`; ui.historyError.hidden = false;
  }
}

async function loadPlan(id) {
  ui.connection.textContent = "Loading visual direction";
  try {
    active = await requestJson(api(`plans/${encodeURIComponent(id)}`));
    contract = JSON.parse(active.planJson);
    report = JSON.parse(active.validationJson || "{}");
    snapshot = active.snapshotJson ? JSON.parse(active.snapshotJson) : null;
    renderDetails(); renderTimeline(); updateLayerLimits();
    ui.connection.textContent = "Loopback · visual only"; ui.empty.hidden = Boolean(snapshot?.cells?.length);
    hovered = null; showNoHover(); scheduleDraw(); await loadHistory();
  } catch (error) {
    active = null; snapshot = null; contract = null; report = null;
    ui.title.textContent = "Could not load visual direction"; ui.meta.textContent = error.message;
    ui.connection.textContent = "Archive read failed"; ui.empty.hidden = false;
    ui.historyError.textContent = `${error.message} Refresh the archive and try again.`; ui.historyError.hidden = false;
    scheduleDraw();
  }
}

function renderDetails() {
  ui.title.textContent = active.title || contract.title || "Untitled plan";
  const date = new Date(active.createdAtEpochMillis);
  const region = snapshot ? `${snapshot.total ?? snapshot.cells?.length ?? 0} cells · snapshot r${active.snapshotRevision}` : "Region snapshot was not archived";
  ui.meta.textContent = `${region} · ${Number.isNaN(date.getTime()) ? "unknown time" : date.toLocaleString()} · ${active.worldIdentity}`;
  const valid = report.valid === true;
  ui.state.textContent = valid ? "Ready" : report.valid === false ? "Needs a rethink" : "No notes";
  ui.state.className = `state-tag ${valid ? "valid" : report.valid === false ? "invalid" : ""}`;
  const count = (key) => Number.isFinite(report[key]) ? report[key] : "—";
  const unknown = snapshot?.cells?.filter((cell) => cell.unknown).length ?? "—";
  const values = [count("places"), count("replacements"), count("removals"), unknown];
  ui.counts.querySelectorAll("dd").forEach((dd, index) => { dd.textContent = String(values[index]); });
  if (active.previewPngBase64) {
    ui.preview.src = `data:image/png;base64,${active.previewPngBase64}`; ui.preview.hidden = false; ui.previewEmpty.hidden = true;
  } else { ui.preview.removeAttribute("src"); ui.preview.hidden = true; ui.previewEmpty.hidden = false; }
  ui.notes.replaceChildren();
  const architecture = report.architectureReview;
  if (architecture && ui.architecture) {
    ui.architecture.replaceChildren();
    const score = document.createElement("strong"); score.textContent = architecture.publishable ? "Strong direction" : "Needs another visual pass";
    score.className = architecture.publishable ? "review-good" : "review-bad";
    ui.architecture.append(score);
    for (const note of [...(architecture.passed ?? []), ...(architecture.warnings ?? [])].slice(0, 6)) {
      const item = document.createElement("div"); item.className = "review-warning"; item.textContent = `• ${note}`; ui.architecture.append(item);
    }
  } else if (ui.architecture) ui.architecture.textContent = "No visual review saved.";
  const diagnostics = Array.isArray(report.diagnostics) ? report.diagnostics : [];
  if (!diagnostics.length) {
    const note = document.createElement("li"); note.className = "muted-note";
    note.textContent = valid ? "The visual direction is grounded in the current Minecraft snapshot." : "No visual notes were saved.";
    ui.notes.append(note);
  } else for (const diagnostic of diagnostics.slice(0, 100)) {
    const note = document.createElement("li");
    const position = diagnostic.position ? ` [${diagnostic.position.x}, ${diagnostic.position.y}, ${diagnostic.position.z}]` : "";
    note.textContent = `${diagnostic.code ?? "NOTE"}${position}: ${diagnostic.message ?? ""}`; ui.notes.append(note);
  }
  if (report.omittedDiagnostics) {
    const note = document.createElement("li"); note.textContent = `${report.omittedDiagnostics} additional diagnostics omitted.`; ui.notes.append(note);
  }
  ui.reset.disabled = false; ui.stage.disabled = false; ui.layer.disabled = false; ui.allLayers.disabled = false; ui.zoom.disabled = false;
  ui.cameraButtons.forEach((button) => { button.disabled = false; });
}

function renderTimeline() {
  ui.stage.replaceChildren(); ui.timeline.replaceChildren();
  const final = document.createElement("option"); final.value = "final"; final.textContent = "Final"; ui.stage.append(final);
  for (const stage of contract.stages ?? []) {
    const option = document.createElement("option"); option.value = stage.id; option.textContent = stage.title || stage.id; ui.stage.append(option);
    const button = document.createElement("button"); button.className = "stage-step"; button.type = "button";
    button.setAttribute("aria-current", "false");
    const name = document.createElement("span"); name.textContent = stage.title || stage.id;
    const info = document.createElement("small"); info.textContent = `${stage.operations?.length ?? 0} transitions`;
    button.append(name, info); button.addEventListener("click", () => { ui.stage.value = stage.id; updateTimelineSelection(); scheduleDraw(); });
    ui.timeline.append(button);
  }
  ui.stage.value = "final"; updateTimelineSelection();
}

function updateTimelineSelection() {
  const activeIndex = ui.stage.value === "final" ? (contract?.stages?.length ?? 1) - 1
    : (contract?.stages ?? []).findIndex((stage) => stage.id === ui.stage.value);
  ui.timeline.querySelectorAll(".stage-step").forEach((button, index) => button.setAttribute("aria-current", index <= activeIndex ? "step" : "false"));
}

function updateLayerLimits() {
  if (!snapshot) return;
  const minY = snapshot.min?.y ?? 0, maxY = snapshot.max?.y ?? minY;
  ui.layer.min = "0"; ui.layer.max = String(Math.max(0, maxY - minY)); ui.layer.value = "0";
  ui.allLayers.checked = true; ui.layer.disabled = false; ui.allLayers.disabled = false;
  updateLayerLabel();
}
function updateLayerLabel() {
  const absoluteY = (snapshot?.min?.y ?? 0) + Number(ui.layer.value);
  ui.layerOutput.textContent = ui.allLayers.checked ? "All" : String(absoluteY);
  ui.layer.disabled = !snapshot || ui.allLayers.checked;
}

function visibleVoxels() {
  if (!snapshot || !contract) return [];
  const operations = stageOperations(contract, ui.stage.value);
  const boundsX = (snapshot.max?.x ?? 0) - (snapshot.min?.x ?? 0) + 1;
  const boundsY = (snapshot.max?.y ?? 0) - (snapshot.min?.y ?? 0) + 1;
  const boundsZ = (snapshot.max?.z ?? 0) - (snapshot.min?.z ?? 0) + 1;
  const layer = ui.allLayers.checked ? null : Number(ui.layer.value);
  const palette = contract.palette ?? new Map(); const voxels = [];
  for (const cell of snapshot.cells ?? []) {
    const position = cell.relative;
    if (!position || position.x < 0 || position.y < 0 || position.z < 0 || position.x >= boundsX || position.y >= boundsY || position.z >= boundsZ) continue;
    if (layer !== null && position.y !== layer) continue;
    const operation = operations.get(`${position.x},${position.y},${position.z}`) ?? null;
    let state = cell.unknown ? null : { id: cell.id, properties: cell.properties ?? {} };
    if (operation && operation.op !== "REMOVE") state = palette[operation.target] ?? state;
    if (!cell.unknown && !operation && isAir(state?.id)) continue;
    voxels.push({ position, absolute: cell.absolute, state, unknown: cell.unknown === true, operation });
  }
  return voxels;
}

function scheduleDraw() { if (!frame) frame = requestAnimationFrame(draw); }
function draw() {
  frame = 0;
  const width = canvas.clientWidth, height = canvas.clientHeight;
  if (!width || !height) return;
  const ratio = Math.min(window.devicePixelRatio || 1, 2);
  if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
    canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
  }
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.fillStyle = "#d7d2bd"; context.fillRect(0, 0, width, height);
  const voxels = visibleVoxels(); points = [];
  if (!voxels.length) return;
  const minX = snapshot.min?.x ?? 0, minY = snapshot.min?.y ?? 0, minZ = snapshot.min?.z ?? 0;
  const maxX = snapshot.max?.x ?? minX, maxY = snapshot.max?.y ?? minY, maxZ = snapshot.max?.z ?? minZ;
  const dx = maxX - minX + 1, dy = maxY - minY + 1, dz = maxZ - minZ + 1;
  const scaleX = (width - 86) / Math.max(1, dx + dz);
  const scaleY = (height - 100) / Math.max(1, dy + (dx + dz) * .38);
  const unit = Math.max(.45, Math.min(25, scaleX, scaleY) * Number(ui.zoom.value) / 100);
  const originX = width / 2, originY = Math.min(height - 42, height * .67);
  const transformed = voxels.map((voxel) => {
    const point = projectVoxel(voxel.position.x, voxel.position.y, voxel.position.z, angle, unit, originX, originY);
    return { ...voxel, ...point };
  }).sort((a, b) => a.depth - b.depth || a.position.y - b.position.y);
  for (const voxel of transformed) {
    drawVoxel(voxel, unit);
    points.push(voxel);
  }
  if (unit < 1.3) {
    context.fillStyle = "#343831"; context.font = "11px ui-monospace, monospace";
    context.fillText("Dense selection — choose a Y layer or zoom in", 14, 21);
  }
  if (hovered) {
    const item = points.find((point) => key(point.position) === key(hovered.position));
    if (item) { context.strokeStyle = "#6b438d"; context.lineWidth = 2; context.beginPath(); context.arc(item.x, item.y, Math.max(6, unit * .42), 0, Math.PI * 2); context.stroke(); }
  }
}

function drawVoxel(voxel, unit) {
  const halfW = Math.max(.5, unit * .48), halfH = Math.max(.35, unit * .24), wall = Math.max(.35, unit * .56);
  const color = voxel.unknown ? "#8b9792" : blockColor(voxel.state?.id ?? "minecraft:air");
  const overlay = voxel.operation?.op;
  let topColor = color, edge = "#313d34", alpha = voxel.unknown ? .38 : 1;
  if (overlay === "PLACE") edge = "#247347";
  else if (overlay === "REPLACE") edge = "#a86a1b";
  else if (overlay === "REMOVE") { edge = "#a83e3b"; alpha = .52; }
  context.globalAlpha = alpha;
  poly([[voxel.x, voxel.y - halfH], [voxel.x + halfW, voxel.y], [voxel.x, voxel.y + halfH], [voxel.x - halfW, voxel.y]], shade(topColor, 8), edge);
  poly([[voxel.x, voxel.y + halfH], [voxel.x + halfW, voxel.y], [voxel.x + halfW, voxel.y + wall], [voxel.x, voxel.y + wall + halfH]], shade(topColor, -8), edge);
  poly([[voxel.x, voxel.y + halfH], [voxel.x - halfW, voxel.y], [voxel.x - halfW, voxel.y + wall], [voxel.x, voxel.y + wall + halfH]], shade(topColor, -16), edge);
  if (overlay === "REMOVE") {
    context.globalAlpha = 1; context.strokeStyle = "#a83e3b"; context.lineWidth = Math.max(1, unit * .07);
    context.beginPath(); context.moveTo(voxel.x - halfW * .55, voxel.y + halfH * .15); context.lineTo(voxel.x + halfW * .55, voxel.y - halfH * .15);
    context.moveTo(voxel.x + halfW * .55, voxel.y + halfH * .15); context.lineTo(voxel.x - halfW * .55, voxel.y - halfH * .15); context.stroke();
  }
  if (overlay) {
    context.globalAlpha = 1; context.lineWidth = 1.5; context.strokeStyle = overlay === "PLACE" ? "#247347" : overlay === "REPLACE" ? "#a86a1b" : "#a83e3b";
    context.beginPath(); context.moveTo(voxel.x, voxel.y - halfH); context.lineTo(voxel.x + halfW, voxel.y); context.lineTo(voxel.x, voxel.y + halfH); context.lineTo(voxel.x - halfW, voxel.y); context.closePath(); context.stroke();
  }
  context.globalAlpha = 1;
}
function poly(points, fill, stroke) {
  context.beginPath(); context.moveTo(points[0][0], points[0][1]);
  for (let index = 1; index < points.length; index++) context.lineTo(points[index][0], points[index][1]);
  context.closePath(); context.fillStyle = fill; context.fill(); context.strokeStyle = stroke; context.lineWidth = .6; context.stroke();
}
function shade(color, amount) {
  const hue = color.match(/hsl\((\d+)/)?.[1] ?? "0";
  return `hsl(${hue} 22% ${Math.max(15, Math.min(76, 49 + amount))}%)`;
}
function key(position) { return `${position.x},${position.y},${position.z}`; }

function onPointerMove(event) {
  if (drag) {
    angle += (event.clientX - drag.x) * .012; drag.x = event.clientX;
    scheduleDraw(); return;
  }
  const box = canvas.getBoundingClientRect(); const x = event.clientX - box.left, y = event.clientY - box.top;
  let best = null, score = 21 * 21;
  for (const point of points) {
    const distance = (point.x - x) ** 2 + ((point.y + 4) - y) ** 2;
    if (distance < score) { score = distance; best = point; }
  }
  if (key(best?.position ?? {}) !== key(hovered?.position ?? {})) {
    hovered = best; best ? showVoxel(best) : showNoHover(); scheduleDraw();
  }
}
function showVoxel(voxel) {
  ui.inspector.replaceChildren();
  const heading = document.createElement("p"), id = document.createElement("span"), position = document.createElement("span"), change = document.createElement("span");
  heading.textContent = voxel.unknown ? "Unknown / unloaded" : "Known block";
  id.className = "cell-id"; id.textContent = voxel.unknown ? "state unavailable" : stateLabel(voxel.state);
  position.className = "cell-property"; position.textContent = voxel.absolute ? `World ${voxel.absolute.x}, ${voxel.absolute.y}, ${voxel.absolute.z} · relative ${voxel.position.x}, ${voxel.position.y}, ${voxel.position.z}` : `Relative ${key(voxel.position)}`;
  change.className = "cell-property";
  const op = voxel.operation;
  if (op) {
    const expected = op.expected ? stateLabel(op.expected) : "air";
    const target = op.target ? stateLabel(contract.palette?.[op.target]) : "air";
    change.textContent = `${op.op}: ${expected} → ${target}`;
  } else change.textContent = "No change in this stage";
  ui.inspector.append(heading, id, position, change);
}
function showNoHover() {
  ui.inspector.replaceChildren(); const cross = document.createElement("span"), heading = document.createElement("p"), copy = document.createElement("span");
  cross.className = "crosshair"; cross.setAttribute("aria-hidden", "true"); heading.textContent = "Hover a voxel"; copy.textContent = "Exact block IDs and coordinates are shown here.";
  ui.inspector.append(cross, heading, copy);
}
function showToast(message) {
  ui.toast.textContent = message; ui.toast.classList.add("visible"); clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ui.toast.classList.remove("visible"), 2300);
}

canvas.addEventListener("pointerdown", (event) => { drag = { x: event.clientX }; canvas.setPointerCapture(event.pointerId); });
canvas.addEventListener("pointermove", onPointerMove);
canvas.addEventListener("pointerup", () => { drag = null; });
canvas.addEventListener("pointercancel", () => { drag = null; });
canvas.addEventListener("pointerleave", () => { if (!drag) { hovered = null; showNoHover(); scheduleDraw(); } });
canvas.addEventListener("wheel", (event) => {
  if (!snapshot) return; event.preventDefault();
  ui.zoom.value = String(Math.max(55, Math.min(180, Number(ui.zoom.value) + (event.deltaY < 0 ? 7 : -7)))); scheduleDraw();
}, { passive: false });
canvas.addEventListener("keydown", (event) => {
  if (["ArrowLeft", "ArrowRight"].includes(event.key)) { angle += event.key === "ArrowLeft" ? -.16 : .16; event.preventDefault(); scheduleDraw(); }
  if (["+", "="].includes(event.key)) { ui.zoom.value = String(Math.min(180, Number(ui.zoom.value) + 8)); scheduleDraw(); }
  if (event.key === "-") { ui.zoom.value = String(Math.max(55, Number(ui.zoom.value) - 8)); scheduleDraw(); }
});
ui.stage.addEventListener("change", () => { updateTimelineSelection(); hovered = null; showNoHover(); scheduleDraw(); });
ui.layer.addEventListener("input", () => { ui.allLayers.checked = false; updateLayerLabel(); hovered = null; scheduleDraw(); });
ui.allLayers.addEventListener("change", () => { updateLayerLabel(); hovered = null; scheduleDraw(); });
ui.zoom.addEventListener("input", scheduleDraw);
ui.reset.addEventListener("click", () => { angle = Math.PI / 4; ui.zoom.value = "100"; scheduleDraw(); showToast("Camera view reset"); });
ui.cameraButtons.forEach((button) => button.addEventListener("click", () => {
  angle = Number(button.dataset.angle); hovered = null; showNoHover(); scheduleDraw(); showToast(`${button.textContent} camera`);
}));
$("reload-history").addEventListener("click", loadHistory);
new ResizeObserver(scheduleDraw).observe($("scene-wrap"));
window.addEventListener("resize", scheduleDraw);
loadHistory();
