"use strict";

const $ = (sel) => document.querySelector(sel);
const el = (id) => document.getElementById(id);

// ── Boot: decide whether we're paired ─────────────────────────────────────────
async function boot() {
  try {
    const res = await fetch("/api/systems", { credentials: "same-origin" });
    if (res.status === 401) return showGate();
    if (res.ok) return showApp(await res.json());
  } catch (e) { /* fall through to gate */ }
  showGate();
}

function showGate() {
  el("gate").classList.remove("hidden");
  el("app").classList.add("hidden");
  el("pin").focus();
}

function showApp(systemsData) {
  el("gate").classList.add("hidden");
  el("app").classList.remove("hidden");
  el("conn").textContent = location.host;
  populateSystems(systemsData);
  loadGames();
}

// ── Pairing ──────────────────────────────────────────────────────────────────
el("pinBtn").addEventListener("click", pair);
el("pin").addEventListener("keydown", (e) => { if (e.key === "Enter") pair(); });

async function pair() {
  const pin = el("pin").value.trim();
  el("pinErr").textContent = "";
  try {
    const res = await fetch("/api/pair", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pin }),
    });
    if (res.ok) return boot();
    const body = await res.json().catch(() => ({}));
    if (body.error === "locked") {
      el("pinErr").textContent = "Too many attempts. Wait a minute and try again.";
    } else {
      el("pinErr").textContent = "Incorrect PIN. Try again.";
    }
  } catch (e) {
    el("pinErr").textContent = "Could not reach the device.";
  }
}

// ── Tabs ───────────────────────────────────────────────────────────────────────
document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((t) => t.classList.remove("active"));
    document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
    tab.classList.add("active");
    $(`.panel[data-panel="${tab.dataset.tab}"]`).classList.add("active");
  });
});

// ── Games ──────────────────────────────────────────────────────────────────────
let SYSTEMS = [];

function populateSystems(data) {
  SYSTEMS = data.systems || [];
  const sel = el("sysSelect");
  sel.innerHTML = "";
  SYSTEMS.forEach((s) => {
    const opt = document.createElement("option");
    opt.value = s.id;
    const tag = s.existing && s.existing.length ? "  ✓ existing" : "";
    opt.textContent = s.name + tag;
    sel.appendChild(opt);
  });
  sel.addEventListener("change", updateDest);
  updateDest();
}

function updateDest() {
  const sys = SYSTEMS.find((s) => s.id === el("sysSelect").value);
  const dest = el("destSelect");
  const hint = el("destHint");
  dest.innerHTML = "";
  if (!sys) return;
  const opts = [];
  (sys.existing || []).forEach((f) => {
    // Each existing folder carries its on-device location (e.g. "SD card · /storage/…/ROMs/nes").
    const loc = f.location || f.path;
    opts.push({ value: f.path, label: loc + "  (existing)" });
  });
  // Always offer the canonical folder as a fallback ("" = let the device decide).
  const hasExisting = sys.existing && sys.existing.length;
  const canonLoc = sys.canonicalLocation || sys.canonicalFolder;
  opts.push({ value: "", label: canonLoc + (hasExisting ? "  (new)" : "  (will be created)") });
  opts.forEach((o) => {
    const opt = document.createElement("option");
    opt.value = o.value;
    opt.textContent = o.label;
    dest.appendChild(opt);
  });
  hint.textContent = (sys.existing && sys.existing.length)
    ? "Files go into your existing folder for this system."
    : "No folder for this system yet — one will be created.";
}

wireDrop("gamesDrop", "gamesFile", (files) => {
  const system = el("sysSelect").value;
  const dest = el("destSelect").value;
  [...files].forEach((f) => {
    let url = `/api/upload/rom?system=${encodeURIComponent(system)}&name=${encodeURIComponent(f.name)}`;
    if (dest) url += `&dest=${encodeURIComponent(dest)}`;
    upload("gamesQueue", "PUT", url, f, f.name);
  });
});

// ── BIOS ─────────────────────────────────────────────────────────────────────
wireDrop("biosDrop", "biosFile", (files) => {
  [...files].forEach((f) => {
    const url = `/api/upload/bios?name=${encodeURIComponent(f.name)}`;
    upload("biosQueue", "PUT", url, f, f.name);
  });
});

// ── Media ──────────────────────────────────────────────────────────────────────
let GAMES = [];

async function loadGames() {
  try {
    const res = await fetch("/api/games", { credentials: "same-origin" });
    if (!res.ok) return;
    const data = await res.json();
    GAMES = (data.games || []).slice().sort((a, b) => a.title.localeCompare(b.title));
    populateMediaSystems();
    renderGameOptions();
  } catch (e) { /* ignore */ }
}

// Friendly platform name from the systems list we already loaded (falls back to the id).
function platformName(id) {
  const s = SYSTEMS.find((x) => x.id === id);
  return s ? s.name : id;
}

// System filter for the media picker: only systems that actually have games, plus an "All" option.
function populateMediaSystems() {
  const sel = el("mediaSysSelect");
  const prev = sel.value;
  const ids = [...new Set(GAMES.map((g) => g.platformId))]
    .sort((a, b) => platformName(a).localeCompare(platformName(b)));
  sel.innerHTML = "";
  const all = document.createElement("option");
  all.value = "";
  all.textContent = `All systems (${GAMES.length})`;
  sel.appendChild(all);
  ids.forEach((id) => {
    const count = GAMES.filter((g) => g.platformId === id).length;
    const opt = document.createElement("option");
    opt.value = id;
    opt.textContent = `${platformName(id)} (${count})`;
    sel.appendChild(opt);
  });
  if (prev && ids.includes(prev)) sel.value = prev; // keep the selection across reloads
}

function renderGameOptions() {
  const sysId = el("mediaSysSelect").value;
  const sel = el("gameSelect");
  sel.innerHTML = "";
  GAMES.filter((g) => !sysId || g.platformId === sysId).forEach((g) => {
    const opt = document.createElement("option");
    opt.value = g.id;
    opt.textContent = `${g.title} (${g.platformId})`;
    sel.appendChild(opt);
  });
}

el("mediaSysSelect").addEventListener("change", renderGameOptions);

wireDrop("mediaDrop", "mediaFile", (files) => {
  const gameId = el("gameSelect").value;
  const type = el("mediaType").value;
  if (!gameId) { alert("Add some games first, then upload media for them."); return; }
  [...files].forEach((f) => {
    const url = `/api/upload/media?gameId=${encodeURIComponent(gameId)}&type=${encodeURIComponent(type)}`;
    upload("mediaQueue", "PUT", url, f, f.name);
  });
});

// ── Background ─────────────────────────────────────────────────────────────────
wireDrop("bgDrop", "bgFile", (files) => {
  const f = files[0];
  if (!f) return;
  upload("bgQueue", "POST", "/api/upload/background", f, f.name);
});

// ── Shared: drag/drop + upload with progress ───────────────────────────────────
function wireDrop(dropId, inputId, handler) {
  const drop = el(dropId);
  const input = el(inputId);
  drop.addEventListener("dragover", (e) => { e.preventDefault(); drop.classList.add("hover"); });
  drop.addEventListener("dragleave", () => drop.classList.remove("hover"));
  drop.addEventListener("drop", (e) => {
    e.preventDefault();
    drop.classList.remove("hover");
    if (e.dataTransfer.files.length) handler(e.dataTransfer.files);
  });
  input.addEventListener("change", () => { if (input.files.length) handler(input.files); input.value = ""; });
}

function upload(queueId, method, url, file, name) {
  const item = document.createElement("div");
  item.className = "item";
  item.innerHTML = `<div class="name"></div><div class="meta">Waiting…</div><div class="bar"><span></span></div>`;
  item.querySelector(".name").textContent = name;
  el(queueId).prepend(item);
  const bar = item.querySelector(".bar > span");
  const meta = item.querySelector(".meta");

  const xhr = new XMLHttpRequest();
  xhr.open(method, url, true);
  xhr.withCredentials = true;
  xhr.upload.onprogress = (e) => {
    if (e.lengthComputable) {
      const pct = Math.round((e.loaded / e.total) * 100);
      bar.style.width = pct + "%";
      meta.textContent = pct + "%  ·  " + human(e.loaded) + " / " + human(e.total);
    }
  };
  xhr.onload = () => {
    if (xhr.status >= 200 && xhr.status < 300) {
      item.classList.add("done");
      bar.style.width = "100%";
      meta.textContent = "Done";
      if (queueId === "games" || queueId === "mediaQueue") loadGames();
    } else {
      item.classList.add("error");
      let msg = xhr.responseText || ("Error " + xhr.status);
      if (xhr.status === 401) msg = "Session expired — refresh and pair again.";
      meta.textContent = msg;
    }
  };
  xhr.onerror = () => { item.classList.add("error"); meta.textContent = "Network error"; };
  xhr.send(file);
}

function human(bytes) {
  if (bytes >= 1e9) return (bytes / 1e9).toFixed(1) + " GB";
  if (bytes >= 1e6) return (bytes / 1e6).toFixed(1) + " MB";
  if (bytes >= 1e3) return (bytes / 1e3).toFixed(0) + " KB";
  return bytes + " B";
}

boot();
