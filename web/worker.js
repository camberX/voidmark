const MAX_BYTES = 2 * 1024 * 1024;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export default {
	async fetch(request, env) {
		try {
			return await route(request, env);
		} catch (error) {
			console.error(error);
			return json(500, { error: "Server error" });
		}
	}
};

async function route(request, env) {
	const url = new URL(request.url);
	const path = url.pathname;

	if (request.method === "OPTIONS") {
		return new Response(null, { status: 204, headers: cors() });
	}
	if (request.method === "GET" && path === "/api/config") {
		return json(200, {
			paypal: env.PAYPAL || "your-paypal@email.com",
			price: env.PRICE || "$1",
			title: env.TITLE || "VOIDMARK Capes"
		});
	}
	if (request.method === "GET" && path.startsWith("/api/cape/")) {
		const id = normalizeUuid(path.slice("/api/cape/".length));
		if (!id) {
			return json(400, { error: "Bad UUID" });
		}
		const state = await loadState(env);
		const listed = state.whitelist.includes(id);
		const tag = tagFor(state, id);
		const head = await env.CAPES.head(capeKey(id));
		if (!head) {
			return json(200, { has: false, hash: "", allowed: listed, tag, bypass: hasBypass(state, id), retryIn: capeRetrySec(state, id) });
		}
		return json(200, { has: true, hash: head.customMetadata?.hash || "", allowed: listed, tag, bypass: hasBypass(state, id), retryIn: capeRetrySec(state, id) });
	}
	if (request.method === "GET" && path.startsWith("/capes/") && path.endsWith(".png")) {
		const id = normalizeUuid(path.slice("/capes/".length, -4));
		if (!id) {
			return new Response(null, { status: 400, headers: cors() });
		}
		const object = await env.CAPES.get(capeKey(id));
		if (!object) {
			return new Response(null, { status: 404, headers: cors() });
		}
		const hash = object.customMetadata?.hash || "";
		return new Response(object.body, {
			headers: {
				...cors(),
				"Content-Type": "image/png",
				"Cache-Control": "no-store",
				ETag: `"${hash}"`
			}
		});
	}
	if ((request.method === "POST" || request.method === "PUT") && path === "/api/cape") {
		return handlePublish(request, env);
	}
	if (request.method === "DELETE" && path === "/api/cape") {
		return handleDelete(request, env);
	}
	if ((request.method === "POST" || request.method === "PUT" || request.method === "DELETE") && path === "/api/whitelist") {
		return handleWhitelist(request, env);
	}
	if ((request.method === "PUT" || request.method === "DELETE") && path === "/api/tag") {
		return handleTag(request, env);
	}
	if (request.method === "PUT" && path === "/api/bypass") {
		return handleBypass(request, env);
	}
	if (request.method === "GET" && env.ASSETS) {
		const asset = await env.ASSETS.fetch(request);
		if (asset.status !== 404) {
			return asset;
		}
	}
	if (request.method === "GET" && path === "/manage.html") {
		return page(MANAGE_HTML);
	}
	if (request.method === "GET" && (path === "/" || path === "/index.html" || path === "/admin.html")) {
		return page(LOGIN_HTML);
	}
	return json(404, { error: "Not found" });
}

async function handlePublish(request, env) {
	const uuid = normalizeUuid(request.headers.get("x-uuid"));
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	const adminOk = adminHeaderOk(request, env);
	const locked = capeRetryMs(state, uuid);
	if (!adminOk && locked > 0) {
		return json(429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1000) });
	}
	const body = new Uint8Array(await request.arrayBuffer());
	if (!isPng(body)) {
		return json(400, { error: "Not a PNG" });
	}
	const hash = await hashBytes(body);
	await env.CAPES.put(capeKey(uuid), body, {
		httpMetadata: { contentType: "image/png" },
		customMetadata: { hash }
	});
	if (!adminOk) {
		touchCapeAt(state, uuid);
		await saveState(env, state);
	}
	return json(200, { ok: true, uuid });
}

async function handleDelete(request, env) {
	const uuid = normalizeUuid(request.headers.get("x-uuid"));
	if (!uuid) {
		return json(400, { error: "Need a UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	const adminOk = adminHeaderOk(request, env);
	const locked = capeRetryMs(state, uuid);
	if (!adminOk && locked > 0) {
		return json(429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1000) });
	}
	await env.CAPES.delete(capeKey(uuid));
	if (!adminOk) {
		touchCapeAt(state, uuid);
		await saveState(env, state);
	}
	return json(200, { ok: true });
}

async function handleWhitelist(request, env) {
	const admin = env.ADMIN || "";
	if (!admin) {
		return json(500, { error: "Admin key is not set on the Worker" });
	}
	let body;
	try {
		body = await request.json();
	} catch {
		body = {};
	}
	if ((body.admin || "") !== admin) {
		return json(403, { error: "Bad admin key" });
	}
	const state = await loadState(env);
	if (request.method === "POST" && !body.uuid && !body.name) {
		const players = await playersFor(env, state, true);
		await saveState(env, state);
		return json(200, { uuids: state.whitelist, players });
	}
	const resolved = await resolvePlayer(body.uuid || body.name);
	if (!resolved.uuid) {
		const raw = String(body.uuid || body.name || "").trim();
		if (!raw) {
			return json(400, { error: "Need a username or UUID" });
		}
		if (sanitizeUsername(raw)) {
			return json(404, { error: "Unknown player" });
		}
		return json(400, { error: "Need a username or UUID" });
	}
	const uuid = resolved.uuid;
	if (request.method === "DELETE") {
		state.whitelist = state.whitelist.filter((id) => id !== uuid);
		forgetPlayer(state, uuid);
		await env.CAPES.delete(capeKey(uuid));
	} else if (!state.whitelist.includes(uuid)) {
		state.whitelist.push(uuid);
	}
	if (request.method !== "DELETE" && resolved.name) {
		rememberName(state, uuid, resolved.name);
	}
	await saveState(env, state);
	const players = await playersFor(env, state, request.method !== "DELETE" && !resolved.name);
	if (request.method !== "DELETE" && resolved.name) {
		rememberName(state, uuid, resolved.name);
		await saveState(env, state);
	}
	return json(200, { ok: true, uuids: state.whitelist, players });
}

async function handleTag(request, env) {
	const admin = env.ADMIN || "";
	if (!admin) {
		return json(500, { error: "Admin key is not set on the Worker" });
	}
	let body;
	try {
		body = await request.json();
	} catch {
		body = {};
	}
	if ((body.admin || "") !== admin) {
		return json(403, { error: "Bad admin key" });
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.tags = state.tags && typeof state.tags === "object" && !Array.isArray(state.tags) ? state.tags : {};
	const tag = request.method === "DELETE" ? "" : sanitizeTag(body.tag);
	if (tag) {
		state.tags[uuid] = tag;
	} else {
		delete state.tags[uuid];
	}
	await saveState(env, state);
	return json(200, { ok: true, tag, players: await playersFor(env, state) });
}

async function handleBypass(request, env) {
	const admin = env.ADMIN || "";
	if (!admin) {
		return json(500, { error: "Admin key is not set on the Worker" });
	}
	let body;
	try {
		body = await request.json();
	} catch {
		body = {};
	}
	if ((body.admin || "") !== admin) {
		return json(403, { error: "Bad admin key" });
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.bypass = objectMap(state.bypass);
	if (body.bypass) {
		state.bypass[uuid] = true;
	} else {
		delete state.bypass[uuid];
	}
	await saveState(env, state);
	return json(200, { ok: true, bypass: Boolean(state.bypass[uuid]), players: await playersFor(env, state) });
}

function tagFor(state, uuid) {
	return sanitizeTag(objectMap(state.tags)[uuid]);
}

const MAX_TAG = 48;
const DAY_MS = 24 * 60 * 60 * 1000;

function sanitizeTag(value) {
	return String(value || "")
		.replace(/[\u0000-\u001f\\"]/g, "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, MAX_TAG);
}

function objectMap(value) {
	return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function hasBypass(state, uuid) {
	return Boolean(objectMap(state.bypass)[uuid]);
}

function capeRetryMs(state, uuid) {
	if (hasBypass(state, uuid)) {
		return 0;
	}
	const last = Number(objectMap(state.capeAt)[uuid]) || 0;
	if (!last) {
		return 0;
	}
	return Math.max(0, last + DAY_MS - Date.now());
}

function capeRetrySec(state, uuid) {
	return Math.ceil(capeRetryMs(state, uuid) / 1000);
}

function touchCapeAt(state, uuid) {
	state.capeAt = objectMap(state.capeAt);
	state.capeAt[uuid] = Date.now();
}

function adminHeaderOk(request, env) {
	const admin = env.ADMIN || "";
	return Boolean(admin) && (request.headers.get("x-admin") || "") === admin;
}

async function playersFor(env, state, forceNames) {
	return Promise.all(state.whitelist.map(async (uuid) => {
		const head = await env.CAPES.head(capeKey(uuid));
		return {
			uuid,
			name: await mojangName(uuid, state, forceNames),
			cape: Boolean(head),
			hash: head?.customMetadata?.hash || "",
			tag: tagFor(state, uuid),
			bypass: hasBypass(state, uuid),
			retryIn: capeRetrySec(state, uuid)
		};
	}));
}

const NAME_TTL_MS = 10 * 60 * 1000;

function rememberName(state, uuid, name) {
	const clean = String(name || "").trim();
	if (!uuid || !clean) {
		return;
	}
	state.names = objectMap(state.names);
	state.namesAt = objectMap(state.namesAt);
	state.names[uuid] = clean;
	state.namesAt[uuid] = Date.now();
}

function forgetPlayer(state, uuid) {
	if (state.names) {
		delete state.names[uuid];
	}
	if (state.namesAt) {
		delete state.namesAt[uuid];
	}
	if (state.tags) {
		delete state.tags[uuid];
	}
	if (state.bypass) {
		delete state.bypass[uuid];
	}
	if (state.capeAt) {
		delete state.capeAt[uuid];
	}
}

function sanitizeUsername(value) {
	const name = String(value || "").trim();
	return /^[A-Za-z0-9_]{1,16}$/.test(name) ? name : "";
}

async function resolvePlayer(raw) {
	const uuid = normalizeUuid(raw);
	if (uuid) {
		return { uuid, name: "" };
	}
	const name = sanitizeUsername(raw);
	if (!name) {
		return { uuid: "", name: "" };
	}
	const found = await lookupUuid(name);
	return { uuid: found, name: found ? name : "" };
}

async function mojangName(uuid, state, force) {
	state.names = objectMap(state.names);
	state.namesAt = objectMap(state.namesAt);
	const cached = typeof state.names[uuid] === "string" ? state.names[uuid] : "";
	const at = Number(state.namesAt[uuid]) || 0;
	if (!force && cached && Date.now() - at < NAME_TTL_MS) {
		return cached;
	}
	const name = await lookupName(uuid);
	if (name) {
		rememberName(state, uuid, name);
		return name;
	}
	return cached;
}

async function lookupName(uuid) {
	const id = String(uuid || "").replaceAll("-", "");
	if (id.length !== 32) {
		return "";
	}
	const dashed = `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
	const attempts = [
		["https://sessionserver.mojang.com/session/minecraft/profile/" + id, (data) => data.name],
		["https://crafthead.net/profile/" + id, (data) => data.name],
		["https://mowojang.matdoes.dev/" + dashed, (data) => data.name],
		["https://playerdb.co/api/player/minecraft/" + dashed, (data) => data?.data?.player?.username],
		["https://api.ashcon.app/mojang/v2/user/" + dashed, (data) => data.username || data.name]
	];
	return firstString(attempts);
}

async function lookupUuid(name) {
	const encoded = encodeURIComponent(name);
	const attempts = [
		["https://api.mojang.com/users/profiles/minecraft/" + encoded, (data) => data.id],
		["https://mowojang.matdoes.dev/" + encoded, (data) => data.id],
		["https://crafthead.net/profile/" + encoded, (data) => data.id],
		["https://playerdb.co/api/player/minecraft/" + encoded, (data) => data?.data?.player?.id || data?.data?.player?.raw_id],
		["https://api.ashcon.app/mojang/v2/user/" + encoded, (data) => data.uuid]
	];
	for (const pair of attempts) {
		const data = await fetchJson(pair[0]);
		if (!data) {
			continue;
		}
		const uuid = normalizeUuid(pair[1](data));
		if (uuid) {
			return uuid;
		}
	}
	return "";
}

async function firstString(attempts) {
	for (const pair of attempts) {
		const data = await fetchJson(pair[0]);
		if (!data) {
			continue;
		}
		const value = String(pair[1](data) || "").trim();
		if (value) {
			return value;
		}
	}
	return "";
}

async function fetchJson(url) {
	try {
		const response = await fetch(url, {
			headers: { "User-Agent": "Voidmark" },
			signal: AbortSignal.timeout(5000)
		});
		if (!response.ok) {
			return null;
		}
		return await response.json();
	} catch {
		return null;
	}
}

async function loadState(env) {
	const empty = { whitelist: [], names: {}, namesAt: {}, tags: {}, bypass: {}, capeAt: {} };
	const object = await env.CAPES.get("state.json");
	if (!object) {
		return empty;
	}
	try {
		const parsed = JSON.parse(await object.text());
		return {
			whitelist: Array.isArray(parsed.whitelist) ? parsed.whitelist : [],
			names: objectMap(parsed.names),
			namesAt: objectMap(parsed.namesAt),
			tags: objectMap(parsed.tags),
			bypass: objectMap(parsed.bypass),
			capeAt: objectMap(parsed.capeAt)
		};
	} catch {
		return empty;
	}
}

async function saveState(env, state) {
	await env.CAPES.put("state.json", JSON.stringify(state));
}

function capeKey(uuid) {
	return `capes/${uuid}.png`;
}

async function hashBytes(bytes) {
	const digest = await crypto.subtle.digest("SHA-256", bytes);
	return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}

function normalizeUuid(value) {
	const raw = (value || "").trim().toLowerCase().replace(/[^0-9a-f]/g, "");
	if (raw.length !== 32) {
		return "";
	}
	const dashed = `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
	return UUID_RE.test(dashed) ? dashed : "";
}

function isPng(bytes) {
	return bytes.length >= 24 && bytes.length <= MAX_BYTES && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
}

function json(status, body) {
	return new Response(JSON.stringify(body), {
		status,
		headers: {
			...cors(),
			"Content-Type": "application/json; charset=utf-8",
			"Cache-Control": "no-store"
		}
	});
}

function cors() {
	return {
		"Access-Control-Allow-Origin": "*",
		"Access-Control-Allow-Methods": "GET,PUT,POST,DELETE,OPTIONS",
		"Access-Control-Allow-Headers": "Content-Type, X-UUID, X-Key, X-Code, X-Token, X-Admin"
	};
}

function page(html) {
	return new Response(html, {
		headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
	});
}

const LOGIN_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>VOIDMARK Capes</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		:root { --bg:#05070d; --pane:#0b0e14; --card:#12151c; --line:#1c2230; --text:#e8edf5; --muted:#8b95a8; --accent:#2fb5ff; --warn:#e8b86d; }
		* { box-sizing: border-box; }
		body { margin: 0; min-height: 100vh; font-family: "Nunito Sans", sans-serif; background: radial-gradient(1200px 600px at 50% -10%, #12324a 0%, var(--bg) 55%); color: var(--text); }
		main { width: min(420px, calc(100% - 32px)); margin: 80px auto; background: color-mix(in srgb, var(--pane) 92%, transparent); border: 1px solid var(--line); border-radius: 16px; padding: 28px 26px 24px; box-shadow: 0 24px 80px #0008; }
		h1 { margin: 0 0 6px; font-size: 22px; letter-spacing: 0.18em; }
		.rule { width: 18px; height: 2px; background: var(--accent); border-radius: 2px; margin: 10px 0 18px; }
		p, label { color: var(--muted); font-size: 14px; line-height: 1.5; }
		label { display: block; margin: 0 0 6px; font-weight: 700; color: var(--text); font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; }
		input { width: 100%; background: var(--card); border: 1px solid var(--line); border-radius: 8px; color: var(--text); padding: 10px 12px; font: inherit; }
		button { margin-top: 16px; width: 100%; border: 0; border-radius: 8px; background: var(--accent); color: #041018; font-weight: 800; padding: 12px; cursor: pointer; }
		button:disabled { opacity: 0.5; }
		.status { min-height: 20px; margin-top: 14px; font-size: 13px; }
		.status.err { color: var(--warn); }
	</style>
</head>
<body>
	<main>
		<h1>VOIDMARK</h1>
		<div class="rule"></div>
		<p>Admin key, then the cape list.</p>
		<label for="admin">Admin key</label>
		<input id="admin" type="password" autocomplete="current-password" placeholder="Worker secret">
		<button type="button" id="go">Open list</button>
		<div class="status" id="status"></div>
	</main>
	<script>
		const admin = document.getElementById("admin");
		const status = document.getElementById("status");
		const go = document.getElementById("go");
		admin.value = sessionStorage.getItem("voidmark-admin") || "";
		async function enter() {
			const key = admin.value.trim();
			status.textContent = "Checking…";
			status.className = "status";
			go.disabled = true;
			try {
				const response = await fetch("/api/whitelist", {
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ admin: key })
				});
				const data = await response.json();
				if (!response.ok) throw new Error(data.error || "Bad admin key");
				sessionStorage.setItem("voidmark-admin", key);
				location.href = "/manage.html";
			} catch (error) {
				sessionStorage.removeItem("voidmark-admin");
				status.className = "status err";
				status.textContent = error.message;
			} finally {
				go.disabled = false;
			}
		}
		go.onclick = enter;
		admin.addEventListener("keydown", (event) => { if (event.key === "Enter") enter(); });
		if (admin.value) enter();
	</script>
</body>
</html>
`;

const MANAGE_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>VOIDMARK Cape list</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		:root { --bg:#05070d; --pane:#0b0e14; --card:#12151c; --line:#1c2230; --text:#e8edf5; --muted:#8b95a8; --accent:#2fb5ff; --warn:#e8b86d; }
		* { box-sizing: border-box; }
		body { margin: 0; min-height: 100vh; font-family: "Nunito Sans", sans-serif; background: radial-gradient(1400px 700px at 50% -10%, #12324a 0%, var(--bg) 50%); color: var(--text); }
		main { width: min(760px, calc(100% - 24px)); margin: 28px auto 48px; }
		header { background: color-mix(in srgb, var(--pane) 92%, transparent); border: 1px solid var(--line); border-radius: 16px; padding: 22px 22px 18px; box-shadow: 0 24px 80px #0008; }
		h1 { margin: 0 0 6px; font-size: 22px; letter-spacing: 0.18em; }
		.rule { width: 18px; height: 2px; background: var(--accent); border-radius: 2px; margin: 10px 0 14px; }
		p, label { color: var(--muted); font-size: 14px; line-height: 1.5; }
		label { display: block; margin: 0 0 6px; font-weight: 700; color: var(--text); font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; }
		.add { display: flex; gap: 8px; flex-wrap: wrap; }
		.add input, .sheet input { flex: 1; min-width: 180px; background: var(--card); border: 1px solid var(--line); border-radius: 8px; color: var(--text); padding: 10px 12px; font: inherit; }
		button { border: 0; border-radius: 8px; background: var(--accent); color: #041018; font-weight: 800; padding: 10px 14px; cursor: pointer; }
		button.ghost { background: var(--card); color: var(--text); border: 1px solid var(--line); }
		button.warn { background: #3a2a1a; color: var(--warn); border: 1px solid #5a4430; }
		button:disabled { opacity: 0.5; }
		.top { display: flex; justify-content: space-between; gap: 8px; align-items: center; margin-bottom: 8px; }
		.status { min-height: 18px; margin-top: 12px; font-size: 13px; }
		.status.ok { color: var(--accent); }
		.status.err { color: var(--warn); }
		.empty { color: var(--muted); margin: 18px 4px; }
		.list { display: flex; flex-direction: column; gap: 10px; margin-top: 16px; }
		.player { display: grid; grid-template-columns: 52px minmax(0, 1fr) 56px auto; gap: 12px; align-items: center; background: color-mix(in srgb, var(--pane) 92%, transparent); border: 1px solid var(--line); border-radius: 14px; padding: 12px; box-shadow: 0 12px 40px #0005; }
		.head, .cape { width: 52px; height: 52px; border-radius: 8px; background: #000; object-fit: contain; image-rendering: pixelated; }
		.cape { height: 72px; width: 46px; justify-self: center; }
		.meta { min-width: 0; }
		.name { font-weight: 800; font-size: 16px; }
		.uuid { color: var(--muted); font-size: 12px; word-break: break-all; margin-top: 4px; }
		.tagline { margin-top: 8px; display: inline-flex; align-items: center; background: #0008; border: 1px solid var(--line); border-radius: 6px; padding: 3px 8px; font-size: 12px; min-height: 22px; }
		.bypass { display: flex; align-items: center; gap: 8px; margin-top: 10px; color: var(--text); font-size: 13px; font-weight: 700; letter-spacing: 0; text-transform: none; cursor: pointer; }
		.bypass input { width: 16px; height: 16px; accent-color: var(--accent); }
		.nocape { color: var(--muted); font-size: 12px; text-align: center; }
		.actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
		.overlay { position: fixed; inset: 0; background: #05070dcc; display: flex; align-items: center; justify-content: center; padding: 16px; z-index: 20; }
		.overlay[hidden] { display: none; }
		.sheet { width: min(460px, 100%); background: var(--pane); border: 1px solid var(--line); border-radius: 16px; padding: 22px; box-shadow: 0 24px 80px #000a; }
		.sheet h2 { margin: 0 0 6px; font-size: 16px; letter-spacing: 0.12em; }
		.sheet .who { color: var(--muted); font-size: 13px; margin: 0 0 14px; }
		.sheet input { width: 100%; margin: 0 0 10px; }
		.codes { display: flex; flex-wrap: wrap; gap: 6px; margin: 0 0 12px; }
		.chip { width: 28px; height: 28px; border-radius: 6px; border: 1px solid #ffffff33; padding: 0; color: #111; font-size: 11px; font-weight: 800; }
		.chip.fmt { background: var(--card); color: var(--text); width: auto; padding: 0 8px; }
		.preview-plate { background: #000a; border: 1px solid var(--line); border-radius: 8px; min-height: 40px; display: flex; align-items: center; justify-content: center; padding: 10px 12px; margin: 0 0 14px; font-family: "Minecraft", "Courier New", monospace; font-size: 16px; text-shadow: 1px 1px #000; }
		.preview-plate .empty { margin: 0; font-family: inherit; }
		.row { display: flex; gap: 8px; flex-wrap: wrap; }
		.row button { flex: 1; }
		@media (max-width: 700px) {
			.player { grid-template-columns: 52px minmax(0, 1fr) 46px; }
			.actions { grid-column: 1 / -1; justify-content: stretch; }
			.actions button { flex: 1; }
		}
	</style>
</head>
<body>
	<main>
		<header>
			<div class="top">
				<h1>VOIDMARK</h1>
				<button type="button" class="ghost" id="out">Log out</button>
			</div>
			<div class="rule"></div>
			<p>Whitelisted players. They can change their own cape once per 24 hours unless Upload bypass is checked. Head tag is the line above their nametag.</p>
			<label for="uuid">Add player</label>
			<div class="add">
				<input id="uuid" type="text" autocomplete="off" spellcheck="false" placeholder="Username or UUID">
				<button type="button" id="add">Add</button>
			</div>
			<div class="status" id="status"></div>
		</header>
		<p class="empty" id="empty">No players yet.</p>
		<div class="list" id="list"></div>
	</main>
	<div class="overlay" id="tagbox" hidden>
		<div class="sheet">
			<h2>HEAD TAG</h2>
			<p class="who" id="tagwho"></p>
			<label for="tagtext">Text</label>
			<input id="tagtext" type="text" maxlength="48" autocomplete="off" spellcheck="false" placeholder="&amp;bVIP  or  &amp;6&amp;lDonor">
			<div class="codes" id="codes"></div>
			<label>Preview</label>
			<div class="preview-plate" id="tagpreview"></div>
			<div class="row">
				<button type="button" id="tagsave">Save</button>
				<button type="button" class="ghost" id="tagclear">Clear</button>
				<button type="button" class="ghost" id="tagcancel">Cancel</button>
			</div>
		</div>
	</div>
	<script>
		const key = sessionStorage.getItem("voidmark-admin") || "";
		if (!key) location.replace("/");
		const status = document.getElementById("status");
		const list = document.getElementById("list");
		const empty = document.getElementById("empty");
		const uuid = document.getElementById("uuid");
		const tagbox = document.getElementById("tagbox");
		const tagtext = document.getElementById("tagtext");
		const tagpreview = document.getElementById("tagpreview");
		const tagwho = document.getElementById("tagwho");
		let tagTarget = "";
		const COLORS = [
			["0", "#000000"], ["1", "#0000aa"], ["2", "#00aa00"], ["3", "#00aaaa"],
			["4", "#aa0000"], ["5", "#aa00aa"], ["6", "#ffaa00"], ["7", "#aaaaaa"],
			["8", "#555555"], ["9", "#5555ff"], ["a", "#55ff55"], ["b", "#55ffff"],
			["c", "#ff5555"], ["d", "#ff55ff"], ["e", "#ffff55"], ["f", "#ffffff"]
		];
		const FORMATS = [["l", "Bold"], ["o", "Italic"], ["n", "Under"], ["m", "Strike"], ["r", "Reset"]];

		(function paintCodes() {
			const box = document.getElementById("codes");
			for (const pair of COLORS) {
				const chip = document.createElement("button");
				chip.type = "button";
				chip.className = "chip";
				chip.style.background = pair[1];
				chip.title = "&" + pair[0];
				chip.textContent = pair[0];
				chip.style.color = "01234589abcdef".indexOf(pair[0]) >= 0 && "018".indexOf(pair[0]) >= 0 ? "#fff" : "#111";
				chip.onclick = () => insertCode(pair[0]);
				box.append(chip);
			}
			for (const pair of FORMATS) {
				const chip = document.createElement("button");
				chip.type = "button";
				chip.className = "chip fmt";
				chip.textContent = pair[1];
				chip.onclick = () => insertCode(pair[0]);
				box.append(chip);
			}
		})();

		function insertCode(code) {
			const start = tagtext.selectionStart || tagtext.value.length;
			const end = tagtext.selectionEnd || start;
			tagtext.value = tagtext.value.slice(0, start) + "&" + code + tagtext.value.slice(end);
			tagtext.focus();
			const cursor = start + 2;
			tagtext.setSelectionRange(cursor, cursor);
			paintPreview();
		}

		function paintPreview() {
			tagpreview.innerHTML = "";
			const rendered = renderLegacy(tagtext.value);
			if (!rendered.childNodes.length) {
				const hint = document.createElement("span");
				hint.className = "empty";
				hint.textContent = "No tag";
				tagpreview.append(hint);
				return;
			}
			tagpreview.append(rendered);
		}

		function renderLegacy(raw) {
			const root = document.createElement("span");
			let color = "#ffffff";
			let bold = false;
			let italic = false;
			let strike = false;
			let under = false;
			let buffer = "";
			function flush() {
				if (!buffer) return;
				const span = document.createElement("span");
				span.textContent = buffer;
				span.style.color = color;
				if (bold) span.style.fontWeight = "800";
				if (italic) span.style.fontStyle = "italic";
				const deco = [];
				if (strike) deco.push("line-through");
				if (under) deco.push("underline");
				if (deco.length) span.style.textDecoration = deco.join(" ");
				root.append(span);
				buffer = "";
			}
			for (let i = 0; i < raw.length; i++) {
				const current = raw.charAt(i);
				if (current === "&" && i + 1 < raw.length) {
					const code = raw.charAt(i + 1).toLowerCase();
					const next = COLORS.find((pair) => pair[0] === code);
					if (next) {
						flush();
						color = next[1];
						i++;
						continue;
					}
					if (code === "l") { flush(); bold = true; i++; continue; }
					if (code === "o") { flush(); italic = true; i++; continue; }
					if (code === "n") { flush(); under = true; i++; continue; }
					if (code === "m") { flush(); strike = true; i++; continue; }
					if (code === "r") {
						flush();
						color = "#ffffff";
						bold = false;
						italic = false;
						strike = false;
						under = false;
						i++;
						continue;
					}
					if (raw.charAt(i + 1) === "&") {
						buffer += "&";
						i++;
						continue;
					}
				}
				buffer += current;
			}
			flush();
			return root;
		}

		function setStatus(ok, text) {
			status.className = "status " + (ok ? "ok" : "err");
			status.textContent = text;
		}

		async function lookupName(id) {
			const dashed = String(id || "");
			const undashed = dashed.replace(/-/g, "");
			const urls = [
				["https://crafthead.net/profile/" + undashed, function (data) { return data.name; }],
				["https://playerdb.co/api/player/minecraft/" + dashed, function (data) { return data && data.data && data.data.player ? data.data.player.username : ""; }],
				["https://api.ashcon.app/mojang/v2/user/" + dashed, function (data) { return data.username || data.name; }]
			];
			for (const pair of urls) {
				try {
					const response = await fetch(pair[0]);
					if (!response.ok) continue;
					const name = String(pair[1](await response.json()) || "").trim();
					if (name) return name;
				} catch (error) {
				}
			}
			return "";
		}

		function fillName(el, player) {
			el.textContent = player.name || "Looking up…";
			lookupName(player.uuid).then((name) => {
				if (name) el.textContent = name;
				else if (!player.name) el.textContent = "Unknown";
			});
		}

		function formatWait(seconds) {
			const total = Math.max(0, Number(seconds) || 0);
			const hours = Math.floor(total / 3600);
			const minutes = Math.floor((total % 3600) / 60);
			if (hours >= 1) {
				return hours + "h " + minutes + "m";
			}
			if (minutes >= 1) {
				return minutes + "m";
			}
			return total + "s";
		}

		async function setBypass(id, enabled, box) {
			try {
				const response = await fetch("/api/bypass", {
					method: "PUT",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ admin: key, uuid: id, bypass: enabled })
				});
				const data = await response.json();
				if (response.status === 403) {
					sessionStorage.removeItem("voidmark-admin");
					location.replace("/");
					return;
				}
				if (!response.ok) throw new Error(data.error || "Could not save bypass");
				draw(data.players || []);
				setStatus(true, enabled ? "Upload bypass on. They can change their cape anytime." : "Upload bypass off. They can change their cape once per 24 hours.");
			} catch (error) {
				if (box) box.checked = !enabled;
				setStatus(false, error.message);
			}
		}

		async function api(method, id) {
			const response = await fetch("/api/whitelist", {
				method,
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ admin: key, uuid: id || undefined })
			});
			const data = await response.json();
			if (response.status === 403) {
				sessionStorage.removeItem("voidmark-admin");
				location.replace("/");
				throw new Error("Bad admin key");
			}
			if (!response.ok) throw new Error(data.error || "Failed");
			return data;
		}

		function draw(players) {
			list.innerHTML = "";
			empty.style.display = players.length ? "none" : "block";
			for (const player of players) {
				const row = document.createElement("article");
				row.className = "player";
				const head = document.createElement("img");
				head.className = "head";
				head.width = 52;
				head.height = 52;
				head.alt = player.name || "Head";
				head.src = "https://crafthead.net/helm/" + player.uuid + "/64";
				head.onerror = () => {
					head.onerror = null;
					head.src = "https://mc-heads.net/avatar/" + player.uuid + "/64";
				};
				const meta = document.createElement("div");
				meta.className = "meta";
				const name = document.createElement("div");
				name.className = "name";
				fillName(name, player);
				const id = document.createElement("div");
				id.className = "uuid";
				id.textContent = player.uuid;
				meta.append(name, id);
				if (player.tag) {
					const plate = document.createElement("div");
					plate.className = "tagline";
					plate.append(renderLegacy(player.tag));
					meta.append(plate);
				}
				const bypass = document.createElement("label");
				bypass.className = "bypass";
				const box = document.createElement("input");
				box.type = "checkbox";
				box.checked = Boolean(player.bypass);
				box.onchange = () => setBypass(player.uuid, box.checked, box);
				const bypassText = document.createElement("span");
				bypassText.textContent = "Upload bypass";
				bypass.append(box, bypassText);
				meta.append(bypass);
				if (!player.bypass && player.retryIn > 0) {
					const wait = document.createElement("div");
					wait.className = "uuid";
					wait.textContent = "Next self-change in " + formatWait(player.retryIn);
					meta.append(wait);
				}
				let capeBox;
				if (player.cape) {
					capeBox = document.createElement("img");
					capeBox.className = "cape";
					capeBox.alt = "Cape";
					capeBox.src = "/capes/" + player.uuid + ".png?h=" + encodeURIComponent(player.hash || Date.now());
				} else {
					capeBox = document.createElement("div");
					capeBox.className = "nocape";
					capeBox.textContent = "No cape";
				}
				const actions = document.createElement("div");
				actions.className = "actions";
				const file = document.createElement("input");
				file.type = "file";
				file.accept = "image/png,.png";
				file.hidden = true;
				const change = document.createElement("button");
				change.type = "button";
				change.className = "ghost";
				change.textContent = "Change cape";
				change.onclick = () => file.click();
				file.onchange = () => {
					if (file.files[0]) uploadCape(player.uuid, file.files[0]);
				};
				const tagBtn = document.createElement("button");
				tagBtn.type = "button";
				tagBtn.className = "ghost";
				tagBtn.textContent = player.tag ? "Edit tag" : "Head tag";
				tagBtn.onclick = () => openTag(player);
				const remove = document.createElement("button");
				remove.type = "button";
				remove.className = "warn";
				remove.textContent = "Dewhitelist";
				remove.onclick = () => send("DELETE", player.uuid);
				actions.append(file, change, tagBtn, remove);
				row.append(head, meta, capeBox, actions);
				list.append(row);
			}
		}

		function openTag(player) {
			tagTarget = player.uuid;
			tagwho.textContent = (player.name || "Unknown") + "  " + player.uuid;
			tagtext.value = player.tag || "";
			tagbox.hidden = false;
			tagtext.focus();
			paintPreview();
		}

		function closeTag() {
			tagbox.hidden = true;
			tagTarget = "";
		}

		async function saveTag(clear) {
			if (!tagTarget) return;
			try {
				const response = await fetch("/api/tag", {
					method: clear ? "DELETE" : "PUT",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ admin: key, uuid: tagTarget, tag: tagtext.value })
				});
				const data = await response.json();
				if (response.status === 403) {
					sessionStorage.removeItem("voidmark-admin");
					location.replace("/");
					return;
				}
				if (!response.ok) throw new Error(data.error || "Could not save tag");
				draw(data.players || []);
				closeTag();
				setStatus(true, clear || !tagtext.value.trim() ? "Head tag cleared." : "Head tag saved. Others see it the next time they join a world.");
			} catch (error) {
				setStatus(false, error.message);
			}
		}

		async function send(method, id) {
			try {
				const data = await api(method, id);
				draw(data.players || []);
				setStatus(true, method === "DELETE" ? "Removed from the list." : (id ? "Whitelisted. They can set a cape in Voidmark." : "Loaded."));
				if (id && method !== "DELETE") uuid.value = "";
			} catch (error) {
				setStatus(false, error.message);
			}
		}

		async function uploadCape(id, file) {
			setStatus(true, "Uploading cape…");
			try {
				const response = await fetch("/api/cape", {
					method: "PUT",
					headers: { "X-UUID": id, "X-Admin": key },
					body: file
				});
				const data = await response.json().catch(() => ({}));
				if (!response.ok) throw new Error(data.error || "Upload failed");
				await send("POST");
				setStatus(true, "Cape updated. Others see it the next time they join a world.");
			} catch (error) {
				setStatus(false, error.message);
			}
		}

		document.getElementById("add").onclick = () => {
			if (!uuid.value.trim()) { setStatus(false, "Enter a username or UUID"); return; }
			send("PUT", uuid.value.trim());
		};
		uuid.addEventListener("keydown", (event) => {
			if (event.key === "Enter") document.getElementById("add").click();
		});
		document.getElementById("out").onclick = () => {
			sessionStorage.removeItem("voidmark-admin");
			location.replace("/");
		};
		tagtext.addEventListener("input", paintPreview);
		document.getElementById("tagsave").onclick = () => saveTag(false);
		document.getElementById("tagclear").onclick = () => saveTag(true);
		document.getElementById("tagcancel").onclick = closeTag;
		tagbox.addEventListener("click", (event) => {
			if (event.target === tagbox) closeTag();
		});
		send("POST");
	</script>
</body>
</html>
`;
