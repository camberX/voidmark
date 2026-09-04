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
	const path = canonicalizePath(url.pathname);

	if (request.method === "OPTIONS") {
		return new Response(null, { status: 204, headers: cors() });
	}
	if (request.method === "GET" && path === "/api/config") {
		const state = await loadState(env);
		return json(200, shopConfig(state, env));
	}
	if (request.method === "PUT" && path === "/api/config") {
		return handleShopConfig(request, env);
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
		const fake = liveBan(state, id);
		if (fake.dirty) {
			await saveState(env, state);
		}
		if (!head) {
			return json(200, { has: false, hash: "", allowed: listed, tag, bypass: hasBypass(state, id), retryIn: capeRetrySec(state, id), ban: Boolean(fake.id), banId: fake.id, banUntil: fake.until });
		}
		return json(200, { has: true, hash: head.customMetadata?.hash || "", allowed: listed, tag, bypass: hasBypass(state, id), retryIn: capeRetrySec(state, id), ban: Boolean(fake.id), banId: fake.id, banUntil: fake.until });
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
	if (request.method === "POST" && path === "/api/cape/import") {
		return handleImport(request, env);
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
	if ((request.method === "PUT" || request.method === "DELETE") && path === "/api/ban") {
		return handleBan(request, env);
	}
	if ((request.method === "PUT" || request.method === "DELETE") && path === "/api/note") {
		return handleNote(request, env);
	}
	if (request.method === "DELETE" && path === "/api/cooldown") {
		return handleCooldown(request, env);
	}
	if (request.method === "PUT" && path === "/api/bulk") {
		return handleBulk(request, env);
	}
	if (request.method === "POST" && path === "/api/logout") {
		return json(200, { ok: true }, { "Set-Cookie": deskCookieHeader(request, "", true) });
	}
	if (request.method === "GET" && (path === "/admin.html" || path === "/manage.html" || path === "/index.html")) {
		const pretty = path === "/admin.html" ? "/admin" : path === "/manage.html" ? "/manage" : "/";
		return new Response(null, { status: 302, headers: { Location: pretty, "Cache-Control": "no-store" } });
	}
	if (request.method === "GET" && isManagePath(path)) {
		return serveManage(request, env);
	}
	if (request.method === "GET" && path === "/admin") {
		return page(LOGIN_HTML);
	}
	if (request.method === "GET" && env.ASSETS) {
		const asset = await env.ASSETS.fetch(request);
		if (asset.status !== 404) {
			return asset;
		}
	}
	if (request.method === "GET" && path === "/cape-crop.js") {
		return new Response(CAPE_CROP_JS, {
			headers: { ...cors(), "Content-Type": "text/javascript; charset=utf-8", "Cache-Control": "no-store" }
		});
	}
	if (request.method === "GET" && (path === "/" || path === "/index.html")) {
		return page(STORE_HTML);
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
	const body = await readCapeBytes(request, adminOk);
	if (!body) {
		return json(400, { error: adminOk ? "Need a PNG or a cape URL" : "Not a PNG" });
	}
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

async function handleImport(request, env) {
	if (!adminHeaderOk(request, env)) {
		return json(403, { error: "Bad admin key" });
	}
	let payload;
	try {
		payload = await request.json();
	} catch {
		return json(400, { error: "Need a URL" });
	}
	const url = String(payload.url || "").trim();
	if (!/^https?:\/\//i.test(url)) {
		return json(400, { error: "Need http(s) URL" });
	}
	try {
		const response = await fetch(url, {
			headers: { "User-Agent": "Voidmark" },
			signal: AbortSignal.timeout(10000)
		});
		if (!response.ok) {
			return json(400, { error: "Fetch failed" });
		}
		const buf = new Uint8Array(await response.arrayBuffer());
		if (buf.length < 24 || buf.length > MAX_BYTES) {
			return json(400, { error: "Bad size" });
		}
		const type = response.headers.get("content-type") || "application/octet-stream";
		return new Response(buf, {
			headers: { ...cors(), "Content-Type": type, "Cache-Control": "no-store" }
		});
	} catch {
		return json(400, { error: "Fetch failed" });
	}
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
		return json(403, { error: "Bad admin key" }, { "Set-Cookie": deskCookieHeader(request, "", true) });
	}
	const state = await loadState(env);
	if (request.method === "POST" && !body.uuid && !body.name) {
		const players = await playersFor(env, state, true);
		await saveState(env, state);
		return adminJson(request, env, 200, { uuids: state.whitelist, players });
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
	return adminJson(request, env, 200, { ok: true, uuids: state.whitelist, players });
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

async function handleNote(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.notes = objectMap(state.notes);
	const note = request.method === "DELETE" ? "" : sanitizeNote(checked.body.note);
	if (note) {
		state.notes[uuid] = note;
	} else {
		delete state.notes[uuid];
	}
	await saveState(env, state);
	return json(200, { ok: true, note, players: await playersFor(env, state) });
}

async function handleCooldown(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.capeAt = objectMap(state.capeAt);
	delete state.capeAt[uuid];
	await saveState(env, state);
	return json(200, { ok: true, retryIn: 0, players: await playersFor(env, state) });
}

async function handleBulk(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const raw = Array.isArray(checked.body.names)
		? checked.body.names
		: String(checked.body.text || "").split(/[\n,]+/);
	const names = raw.map((value) => String(value || "").trim()).filter(Boolean).slice(0, 25);
	if (!names.length) {
		return json(400, { error: "Paste usernames or UUIDs, one per line" });
	}
	const state = await loadState(env);
	const added = [];
	const skipped = [];
	const failed = [];
	for (const name of names) {
		const resolved = await resolvePlayer(name);
		if (!resolved.uuid) {
			failed.push(name);
			continue;
		}
		if (state.whitelist.includes(resolved.uuid)) {
			skipped.push(name);
			continue;
		}
		state.whitelist.push(resolved.uuid);
		if (resolved.name) {
			rememberName(state, resolved.uuid, resolved.name);
		}
		added.push(resolved.name || resolved.uuid);
	}
	await saveState(env, state);
	const players = await playersFor(env, state, true);
	await saveState(env, state);
	return json(200, { ok: true, added: added.length, skipped: skipped.length, failed, players });
}

async function handleShopConfig(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const state = await loadState(env);
	state.config = {
		...shopConfig(state, env),
		paypal: sanitizePaypal(checked.body.paypal),
		price: sanitizePrice(checked.body.price),
		title: sanitizeTitle(checked.body.title),
		blurb: sanitizeBlurb(checked.body.blurb)
	};
	await saveState(env, state);
	return json(200, { ok: true, ...state.config });
}

async function handleBan(request, env) {
	const checked = await adminBody(request, env);
	if (checked.error) {
		return checked.error;
	}
	const uuid = normalizeUuid(checked.body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	const state = await loadState(env);
	if (!state.whitelist.includes(uuid)) {
		return json(403, { error: "uuid not whitelisted" });
	}
	state.bans = objectMap(state.bans);
	let banId = "";
	let until = 0;
	if (request.method === "DELETE") {
		delete state.bans[uuid];
	} else {
		banId = randomBanId();
		until = Date.now() + BAN_MS;
		state.bans[uuid] = { id: banId, until };
	}
	await saveState(env, state);
	return json(200, { ok: true, ban: Boolean(banId), banId, banUntil: until, players: await playersFor(env, state) });
}

async function adminBody(request, env) {
	const admin = env.ADMIN || "";
	if (!admin) {
		return { error: json(500, { error: "Admin key is not set on the Worker" }) };
	}
	let body;
	try {
		body = await request.json();
	} catch {
		body = {};
	}
	if ((body.admin || "") !== admin) {
		return { error: json(403, { error: "Bad admin key" }) };
	}
	return { body };
}

async function readCapeBytes(request, adminOk) {
	const type = (request.headers.get("content-type") || "").toLowerCase();
	if (type.includes("application/json")) {
		if (!adminOk) {
			return null;
		}
		let payload;
		try {
			payload = await request.json();
		} catch {
			return null;
		}
		const url = String(payload.url || "").trim();
		if (!/^https?:\/\//i.test(url)) {
			return null;
		}
		try {
			const response = await fetch(url, {
				headers: { "User-Agent": "Voidmark" },
				signal: AbortSignal.timeout(10000)
			});
			if (!response.ok) {
				return null;
			}
			return new Uint8Array(await response.arrayBuffer());
		} catch {
			return null;
		}
	}
	return new Uint8Array(await request.arrayBuffer());
}

function tagFor(state, uuid) {
	return sanitizeTag(objectMap(state.tags)[uuid]);
}

function noteFor(state, uuid) {
	return sanitizeNote(objectMap(state.notes)[uuid]);
}

function liveBan(state, uuid) {
	state.bans = objectMap(state.bans);
	const value = state.bans[uuid];
	let id = "";
	let until = 0;
	let dirty = false;
	if (typeof value === "string") {
		id = value.startsWith("#") ? value : "";
		until = id ? Date.now() + BAN_MS : 0;
		if (id) {
			state.bans[uuid] = { id, until };
			dirty = true;
		}
	} else if (value && typeof value === "object") {
		id = String(value.id || "");
		id = id.startsWith("#") ? id : "";
		until = Number(value.until) || 0;
	}
	if (!id || until <= Date.now()) {
		if (state.bans[uuid] != null) {
			delete state.bans[uuid];
			dirty = true;
		}
		return { id: "", until: 0, dirty };
	}
	return { id, until, dirty };
}

function banIdOf(state, uuid) {
	return liveBan(state, uuid).id;
}

function randomBanId() {
	const bytes = crypto.getRandomValues(new Uint8Array(4));
	return "#" + [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("").toUpperCase();
}

function shopConfig(state, env) {
	const stored = objectMap(state.config);
	return {
		title: stored.title || env.TITLE || "VOIDMARK Capes",
		discord: "@evilkitten911",
		blurb: stored.blurb || ""
	};
}

const MAX_TAG = 48;
const MAX_NOTE = 160;
const DAY_MS = 24 * 60 * 60 * 1000;
const BAN_MS = 360 * DAY_MS;

function sanitizeTag(value) {
	return String(value || "")
		.replace(/[\u0000-\u001f\\"]/g, "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, MAX_TAG);
}

function sanitizeNote(value) {
	return String(value || "")
		.replace(/[\u0000-\u001f]/g, "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, MAX_NOTE);
}

function sanitizePaypal(value) {
	return String(value || "")
		.replace(/\s+/g, "")
		.slice(0, 80);
}

function sanitizePrice(value) {
	const price = String(value || "").replace(/\s+/g, " ").trim().slice(0, 24);
	return price || "$1";
}

function sanitizeTitle(value) {
	const title = String(value || "").replace(/\s+/g, " ").trim().slice(0, 48);
	return title || "VOIDMARK Capes";
}

function sanitizeBlurb(value) {
	return String(value || "")
		.replace(/[\u0000-\u001f]/g, "")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, 280);
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
		const fake = liveBan(state, uuid);
		return {
			uuid,
			name: await mojangName(uuid, state, forceNames),
			cape: Boolean(head),
			hash: head?.customMetadata?.hash || "",
			tag: tagFor(state, uuid),
			bypass: hasBypass(state, uuid),
			retryIn: capeRetrySec(state, uuid),
			note: noteFor(state, uuid),
			ban: Boolean(fake.id),
			banId: fake.id,
			banUntil: fake.until
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
	if (state.notes) {
		delete state.notes[uuid];
	}
	if (state.bans) {
		delete state.bans[uuid];
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
	const empty = { whitelist: [], names: {}, namesAt: {}, tags: {}, bypass: {}, capeAt: {}, notes: {}, bans: {}, config: {} };
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
			capeAt: objectMap(parsed.capeAt),
			notes: objectMap(parsed.notes),
			bans: objectMap(parsed.bans),
			config: objectMap(parsed.config)
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

function json(status, body, extraHeaders) {
	return new Response(JSON.stringify(body), {
		status,
		headers: {
			...cors(),
			"Content-Type": "application/json; charset=utf-8",
			"Cache-Control": "no-store",
			...(extraHeaders || {})
		}
	});
}

const DESK_COOKIE = "voidmark_desk";
const DESK_TTL_SEC = 60 * 60 * 24 * 7;

function canonicalizePath(pathname) {
	let raw = String(pathname || "/");
	try {
		raw = decodeURIComponent(raw);
	} catch {
		return "/";
	}
	raw = raw.replace(/\\/g, "/");
	const parts = [];
	for (const seg of raw.split("/")) {
		if (!seg || seg === ".") {
			continue;
		}
		if (seg === "..") {
			parts.pop();
			continue;
		}
		if (seg.includes("\0")) {
			return "/";
		}
		parts.push(seg);
	}
	return "/" + parts.join("/");
}

function isManagePath(path) {
	return path === "/manage";
}

function cookieOf(request, name) {
	const raw = request.headers.get("Cookie") || "";
	const parts = raw.split(";");
	for (let i = 0; i < parts.length; i++) {
		const piece = parts[i].trim();
		const eq = piece.indexOf("=");
		if (eq < 0) {
			continue;
		}
		if (piece.slice(0, eq) === name) {
			return piece.slice(eq + 1);
		}
	}
	return "";
}

function deskSecure(request) {
	try {
		return new URL(request.url).protocol === "https:";
	} catch {
		return true;
	}
}

function deskCookieHeader(request, token, clear) {
	const parts = [
		DESK_COOKIE + "=" + (clear ? "" : token),
		"Path=/",
		"HttpOnly",
		"SameSite=Lax",
		clear ? "Max-Age=0" : ("Max-Age=" + DESK_TTL_SEC)
	];
	if (deskSecure(request)) {
		parts.push("Secure");
	}
	return parts.join("; ");
}

function bytesToHex(bytes) {
	const arr = new Uint8Array(bytes);
	let out = "";
	for (let i = 0; i < arr.length; i++) {
		out += arr[i].toString(16).padStart(2, "0");
	}
	return out;
}

function timingSafeEqualStr(a, b) {
	if (a.length !== b.length) {
		return false;
	}
	let diff = 0;
	for (let i = 0; i < a.length; i++) {
		diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	}
	return diff === 0;
}

async function hmacHex(secret, msg) {
	const key = await crypto.subtle.importKey(
		"raw",
		new TextEncoder().encode(secret),
		{ name: "HMAC", hash: "SHA-256" },
		false,
		["sign"]
	);
	const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(msg));
	return bytesToHex(sig);
}

async function mintDeskToken(secret) {
	const exp = Math.floor(Date.now() / 1000) + DESK_TTL_SEC;
	const msg = "v1." + exp;
	return msg + "." + await hmacHex(secret, msg);
}

async function verifyDeskToken(secret, token) {
	const parts = String(token || "").split(".");
	if (parts.length !== 3 || parts[0] !== "v1") {
		return false;
	}
	const exp = Number(parts[1]);
	if (!Number.isFinite(exp) || exp < Date.now() / 1000) {
		return false;
	}
	const sig = await hmacHex(secret, "v1." + parts[1]);
	return timingSafeEqualStr(sig, parts[2]);
}

async function deskCookieOk(request, env) {
	const secret = env.ADMIN || "";
	if (!secret) {
		return false;
	}
	return verifyDeskToken(secret, cookieOf(request, DESK_COOKIE));
}

async function adminJson(request, env, status, body) {
	const token = await mintDeskToken(env.ADMIN || "");
	return json(status, body, { "Set-Cookie": deskCookieHeader(request, token, false) });
}

function redirectLogin() {
	return new Response(null, {
		status: 302,
		headers: {
			Location: "/admin",
			"Cache-Control": "no-store",
			Pragma: "no-cache"
		}
	});
}

async function serveManage(request, env) {
	if (!(await deskCookieOk(request, env))) {
		return redirectLogin();
	}
	if (env.ASSETS) {
		const asset = await env.ASSETS.fetch(request);
		if (asset.status !== 404) {
			const headers = new Headers(asset.headers);
			headers.set("Cache-Control", "private, no-store, no-cache");
			headers.set("Pragma", "no-cache");
			return new Response(asset.body, { status: asset.status, headers });
		}
	}
	return page(MANAGE_HTML);
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
		headers: {
			"Content-Type": "text/html; charset=utf-8",
			"Cache-Control": "private, no-store, no-cache",
			Pragma: "no-cache"
		}
	});
}

const CAPE_CROP_JS = "window.VoidmarkCapeCrop = (function () {\n\tvar ASPECT = 10 / 16;\n\tvar FACE_W = 10;\n\tvar FACE_H = 16;\n\tvar LAYOUT_W = 64;\n\tvar LAYOUT_H = 32;\n\tvar MAX_SCALE = 16;\n\tvar overlay = null;\n\n\tfunction isVanilla(w, h) {\n\t\treturn w >= 64 && h >= 32 && w % 64 === 0 && h % 32 === 0 && w / 64 === h / 32;\n\t}\n\n\tfunction cover(srcW, srcH) {\n\t\tvar srcAspect = srcW / Math.max(1, srcH);\n\t\tvar crop = { x: 0, y: 0, w: 1, h: 1 };\n\t\tif (srcAspect > ASPECT) {\n\t\t\tcrop.h = 1;\n\t\t\tcrop.w = ASPECT / srcAspect;\n\t\t\tcrop.x = (1 - crop.w) * 0.5;\n\t\t\tcrop.y = 0;\n\t\t} else {\n\t\t\tcrop.w = 1;\n\t\t\tcrop.h = srcAspect / ASPECT;\n\t\t\tcrop.x = 0;\n\t\t\tcrop.y = (1 - crop.h) * 0.5;\n\t\t}\n\t\treturn crop;\n\t}\n\n\tfunction clampCrop(crop, srcW, srcH) {\n\t\tsrcW = Math.max(1, srcW);\n\t\tsrcH = Math.max(1, srcH);\n\t\tvar srcAspect = srcW / srcH;\n\t\tvar normAspect = ASPECT / srcAspect;\n\t\tvar max = cover(srcW, srcH);\n\t\tvar minW = Math.max(10 / srcW, max.w * 0.12);\n\t\tcrop.w = Math.min(max.w, Math.max(minW, crop.w));\n\t\tcrop.h = crop.w / normAspect;\n\t\tif (crop.h > max.h) {\n\t\t\tcrop.h = max.h;\n\t\t\tcrop.w = crop.h * normAspect;\n\t\t}\n\t\tcrop.x = Math.min(Math.max(0, crop.x), Math.max(0, 1 - crop.w));\n\t\tcrop.y = Math.min(Math.max(0, crop.y), Math.max(0, 1 - crop.h));\n\t}\n\n\tfunction loadImage(file) {\n\t\treturn new Promise(function (resolve, reject) {\n\t\t\tvar url = URL.createObjectURL(file);\n\t\t\tvar img = new Image();\n\t\t\timg.onload = function () {\n\t\t\t\tURL.revokeObjectURL(url);\n\t\t\t\tresolve(img);\n\t\t\t};\n\t\t\timg.onerror = function () {\n\t\t\t\tURL.revokeObjectURL(url);\n\t\t\t\treject(new Error(\"Could not read that image.\"));\n\t\t\t};\n\t\t\timg.src = url;\n\t\t});\n\t}\n\n\tfunction pickScale(sw, sh) {\n\t\tvar needed = Math.max(4, Math.min(MAX_SCALE, Math.max(Math.floor(sw / FACE_W), Math.floor(sh / FACE_H))));\n\t\treturn Math.min(MAX_SCALE, Math.max(4, needed));\n\t}\n\n\tfunction bakeAtlas(img, crop) {\n\t\tvar sw = img.width;\n\t\tvar sh = img.height;\n\t\t\tvar scale = pickScale(img.width, img.height);\n\t\tvar aw = LAYOUT_W * scale;\n\t\tvar ah = LAYOUT_H * scale;\n\t\tvar out = document.createElement(\"canvas\");\n\t\tout.width = aw;\n\t\tout.height = ah;\n\t\tvar ctx = out.getContext(\"2d\");\n\t\tctx.fillStyle = \"#000\";\n\t\tctx.fillRect(0, 0, aw, ah);\n\t\tctx.imageSmoothingEnabled = crop.w * sw > 10 * scale || crop.h * sh > 16 * scale;\n\t\tvar fu = scale;\n\t\tvar fv = scale;\n\t\tvar fw = FACE_W * scale;\n\t\tvar fh = FACE_H * scale;\n\t\tvar sx = crop.x * sw;\n\t\tvar sy = crop.y * sh;\n\t\tvar sWidth = crop.w * sw;\n\t\tvar sHeight = crop.h * sh;\n\t\tctx.drawImage(img, sx, sy, sWidth, sHeight, fu, fv, fw, fh);\n\t\tctx.drawImage(out, fu, fv, fw, fh, 12 * scale, fv, fw, fh);\n\t\tvar edge = ctx.getImageData(fu, fv, fw, fh);\n\t\tfor (var y = 0; y < fh; y++) {\n\t\t\tvar left = (y * fw) * 4;\n\t\t\tvar right = (y * fw + fw - 1) * 4;\n\t\t\tfor (var x = 0; x < scale; x++) {\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[left] + \",\" + edge.data[left + 1] + \",\" + edge.data[left + 2] + \")\";\n\t\t\t\tctx.fillRect(x, fv + y, 1, 1);\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[right] + \",\" + edge.data[right + 1] + \",\" + edge.data[right + 2] + \")\";\n\t\t\t\tctx.fillRect(11 * scale + x, fv + y, 1, 1);\n\t\t\t}\n\t\t}\n\t\tfor (var i = 0; i < fw; i++) {\n\t\t\tvar top = i * 4;\n\t\t\tvar bot = ((fh - 1) * fw + i) * 4;\n\t\t\tfor (var t = 0; t < scale; t++) {\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[top] + \",\" + edge.data[top + 1] + \",\" + edge.data[top + 2] + \")\";\n\t\t\t\tctx.fillRect(fu + i, t, 1, 1);\n\t\t\t\tctx.fillStyle = \"rgb(\" + edge.data[bot] + \",\" + edge.data[bot + 1] + \",\" + edge.data[bot + 2] + \")\";\n\t\t\t\tctx.fillRect(11 * scale + i, t, 1, 1);\n\t\t\t}\n\t\t}\n\t\treturn out;\n\t}\n\n\tfunction canvasPng(canvas) {\n\t\treturn new Promise(function (resolve, reject) {\n\t\t\tcanvas.toBlob(function (blob) {\n\t\t\t\tif (!blob) reject(new Error(\"Could not encode cape.\"));\n\t\t\t\telse resolve(blob);\n\t\t\t}, \"image/png\");\n\t\t});\n\t}\n\n\tfunction close() {\n\t\tif (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);\n\t\toverlay = null;\n\t}\n\n\tfunction open(file, onDone, onCancel) {\n\t\tclose();\n\t\tloadImage(file).then(function (img) {\n\t\t\tif (isVanilla(img.width, img.height)) {\n\t\t\t\tonDone(file);\n\t\t\t\treturn;\n\t\t\t}\n\t\t\tvar crop = cover(img.width, img.height);\n\t\t\toverlay = document.createElement(\"div\");\n\t\t\toverlay.className = \"overlay center\";\n\t\t\toverlay.innerHTML = \"\"\n\t\t\t\t+ '<div class=\"sheet crop-sheet\">'\n\t\t\t\t+ \"<h1 style=\\\"font-size:16px\\\">CAPE CREATOR</h1>\"\n\t\t\t\t+ '<p class=\"who\">Drag to pan. Scroll or pinch to zoom. The box is the 10\u00d716 face other players see.</p>'\n\t\t\t\t+ '<div class=\"crop-wrap\">'\n\t\t\t\t+ '<div class=\"crop-stage\" id=\"crop-stage\"><div class=\"crop-holder\"><canvas id=\"crop-src\"></canvas><div id=\"crop-box\"></div></div></div>'\n\t\t\t\t+ '<div class=\"crop-side\"><div class=\"crop-face\"><canvas id=\"crop-face\"></canvas></div><p class=\"hint\">In-game face</p></div>'\n\t\t\t\t+ \"</div>\"\n\t\t\t\t+ '<div class=\"row\">'\n\t\t\t\t+ '<button type=\"button\" class=\"ghost\" id=\"crop-reset\">Reset</button>'\n\t\t\t\t+ '<button type=\"button\" class=\"ghost\" id=\"crop-cancel\">Cancel</button>'\n\t\t\t\t+ '<button type=\"button\" class=\"primary\" id=\"crop-apply\">Apply cape</button>'\n\t\t\t\t+ \"</div></div>\";\n\t\t\tdocument.body.appendChild(overlay);\n\t\t\tvar stage = overlay.querySelector(\"#crop-stage\");\n\t\t\tvar srcCanvas = overlay.querySelector(\"#crop-src\");\n\t\t\tvar faceCanvas = overlay.querySelector(\"#crop-face\");\n\t\t\tvar box = overlay.querySelector(\"#crop-box\");\n\t\t\tvar dragging = false;\n\t\t\tvar lastX = 0;\n\t\t\tvar lastY = 0;\n\n\t\t\tfunction layout() {\n\t\t\t\tvar maxW = Math.min(420, stage.clientWidth || 420);\n\t\t\t\tvar maxH = 280;\n\t\t\t\tvar fit = Math.min(maxW / img.width, maxH / img.height);\n\t\t\t\tsrcCanvas.width = Math.max(1, Math.round(img.width * fit));\n\t\t\t\tsrcCanvas.height = Math.max(1, Math.round(img.height * fit));\n\t\t\t\tvar sctx = srcCanvas.getContext(\"2d\");\n\t\t\t\tsctx.imageSmoothingEnabled = true;\n\t\t\t\tsctx.drawImage(img, 0, 0, srcCanvas.width, srcCanvas.height);\n\t\t\t\tbox.style.left = crop.x * srcCanvas.width + \"px\";\n\t\t\t\tbox.style.top = crop.y * srcCanvas.height + \"px\";\n\t\t\t\tbox.style.width = crop.w * srcCanvas.width + \"px\";\n\t\t\t\tbox.style.height = crop.h * srcCanvas.height + \"px\";\n\t\t\t\tfaceCanvas.width = 50;\n\t\t\t\tfaceCanvas.height = 80;\n\t\t\t\tvar fctx = faceCanvas.getContext(\"2d\");\n\t\t\t\tfctx.imageSmoothingEnabled = true;\n\t\t\t\tfctx.drawImage(\n\t\t\t\t\timg,\n\t\t\t\t\tcrop.x * img.width,\n\t\t\t\t\tcrop.y * img.height,\n\t\t\t\t\tcrop.w * img.width,\n\t\t\t\t\tcrop.h * img.height,\n\t\t\t\t\t0,\n\t\t\t\t\t0,\n\t\t\t\t\t50,\n\t\t\t\t\t80\n\t\t\t\t);\n\t\t\t}\n\n\t\t\toverlay.querySelector(\"#crop-reset\").onclick = function () {\n\t\t\t\tcrop = cover(img.width, img.height);\n\t\t\t\tlayout();\n\t\t\t};\n\t\t\toverlay.querySelector(\"#crop-cancel\").onclick = function () {\n\t\t\t\tclose();\n\t\t\t\tif (onCancel) onCancel();\n\t\t\t};\n\t\t\toverlay.querySelector(\"#crop-apply\").onclick = function () {\n\t\t\t\tcanvasPng(bakeAtlas(img, crop)).then(function (blob) {\n\t\t\t\t\tclose();\n\t\t\t\t\tonDone(blob);\n\t\t\t\t}).catch(function (error) {\n\t\t\t\t\talert(error.message);\n\t\t\t\t});\n\t\t\t};\n\t\t\toverlay.addEventListener(\"click\", function (event) {\n\t\t\t\tif (event.target === overlay) {\n\t\t\t\t\tclose();\n\t\t\t\t\tif (onCancel) onCancel();\n\t\t\t\t}\n\t\t\t});\n\t\t\tstage.addEventListener(\"pointerdown\", function (event) {\n\t\t\t\tdragging = true;\n\t\t\t\tlastX = event.clientX;\n\t\t\t\tlastY = event.clientY;\n\t\t\t\tstage.setPointerCapture(event.pointerId);\n\t\t\t});\n\t\t\tstage.addEventListener(\"pointerup\", function () { dragging = false; });\n\t\t\tstage.addEventListener(\"pointermove\", function (event) {\n\t\t\t\tif (!dragging) return;\n\t\t\t\tvar dx = (event.clientX - lastX) / srcCanvas.width;\n\t\t\t\tvar dy = (event.clientY - lastY) / srcCanvas.height;\n\t\t\t\tlastX = event.clientX;\n\t\t\t\tlastY = event.clientY;\n\t\t\t\tcrop.x += dx;\n\t\t\t\tcrop.y += dy;\n\t\t\t\tclampCrop(crop, img.width, img.height);\n\t\t\t\tlayout();\n\t\t\t});\n\t\t\tstage.addEventListener(\"wheel\", function (event) {\n\t\t\t\tevent.preventDefault();\n\t\t\t\tvar rect = srcCanvas.getBoundingClientRect();\n\t\t\t\tvar px = (event.clientX - rect.left) / srcCanvas.width;\n\t\t\t\tvar py = (event.clientY - rect.top) / srcCanvas.height;\n\t\t\t\tvar factor = event.deltaY < 0 ? 0.88 : 1.14;\n\t\t\t\tcrop.x = px - (px - crop.x) * factor;\n\t\t\t\tcrop.y = py - (py - crop.y) * factor;\n\t\t\t\tcrop.w *= factor;\n\t\t\t\tcrop.h *= factor;\n\t\t\t\tclampCrop(crop, img.width, img.height);\n\t\t\t\tlayout();\n\t\t\t}, { passive: false });\n\t\t\trequestAnimationFrame(layout);\n\t\t}).catch(function (error) {\n\t\t\tif (onCancel) onCancel();\n\t\t\talert(error.message);\n\t\t});\n\t}\n\n\treturn { open: open, isVanilla: isVanilla };\n})();\n";

const STORE_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>VOIDMARK Capes</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		:root { --bg:#03050a; --pane:#0a0e18; --line:#1a2336; --text:#e8edf5; --muted:#8b95a8; --accent:#2fb5ff; }
		* { box-sizing: border-box; }
		html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--text); font-family: "Nunito Sans", sans-serif; }
		#stars { position: fixed; inset: 0; z-index: 0; }
		.glow { position: fixed; inset: 0; z-index: 1; pointer-events: none; background:
			radial-gradient(720px 420px at 50% 18%, rgba(47,181,255,0.20), transparent 58%),
			radial-gradient(900px 700px at 50% 110%, rgba(47,181,255,0.06), transparent 50%); }
		.page { position: relative; z-index: 2; min-height: 100vh; display: grid; grid-template-rows: auto 1fr auto; width: min(720px, calc(100% - 32px)); margin: 0 auto; padding: 22px 0 28px; }
		.top { display: flex; align-items: center; }
		.mark { letter-spacing: 0.34em; font-weight: 800; font-size: 12px; }
		.mark span { color: var(--accent); }
		.hero { display: grid; justify-items: center; text-align: center; align-content: center; padding: 32px 0 40px; }
		.cape { width: 52px; height: 82px; margin-bottom: 22px; background:
			linear-gradient(180deg, #7ad4ff 0%, var(--accent) 38%, #14608a 100%);
			clip-path: polygon(18% 0, 82% 0, 100% 8%, 92% 100%, 8% 100%, 0 8%);
			box-shadow: 0 18px 40px rgba(47,181,255,0.28); }
		.kicker { font-size: 11px; letter-spacing: 0.22em; text-transform: uppercase; color: var(--accent); margin: 0 0 10px; }
		h1 { margin: 0; font-size: clamp(48px, 12vw, 84px); letter-spacing: 0.16em; line-height: 0.92; }
		.rule { width: 28px; height: 3px; background: var(--accent); border-radius: 2px; margin: 16px auto 18px; box-shadow: 0 0 18px var(--accent); }
		.lede { margin: 0; max-width: 460px; color: var(--muted); font-size: 16px; line-height: 1.65; }
		.card { width: min(440px, 100%); margin-top: 28px; padding: 22px 22px 18px; border-radius: 20px; border: 1px solid var(--line); background: color-mix(in srgb, var(--pane) 88%, transparent); box-shadow: 0 28px 80px #0008, inset 0 1px 0 #ffffff12; backdrop-filter: blur(18px); }
		.card .kicker { margin-bottom: 8px; }
		.handle { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 12px 0 10px; padding: 12px 14px; border-radius: 12px; background: #070b12; border: 1px solid var(--line); }
		.handle b { font-size: 20px; letter-spacing: 0.02em; }
		.copy { border: 0; border-radius: 9px; background: var(--accent); color: #041018; font: inherit; font-weight: 800; padding: 8px 12px; cursor: pointer; letter-spacing: 0.06em; text-transform: uppercase; font-size: 11px; }
		.copy:hover { filter: brightness(1.08); }
		.note { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.5; }
		.steps { display: grid; gap: 10px; margin: 18px 0 0; padding: 0; list-style: none; text-align: left; }
		.steps li { display: grid; grid-template-columns: 26px 1fr; gap: 10px; color: var(--muted); font-size: 13px; line-height: 1.45; }
		.num { width: 26px; height: 26px; border-radius: 8px; border: 1px solid var(--accent); color: var(--accent); display: grid; place-items: center; font-size: 11px; font-weight: 800; }
		.foot { color: var(--muted); font-size: 11px; letter-spacing: 0.12em; text-transform: uppercase; text-align: center; }
		@media (max-width: 520px) {
			.handle { flex-direction: column; align-items: stretch; }
			.handle b { font-size: 18px; }
		}
	</style>
</head>
<body>
	<canvas id="stars"></canvas>
	<div class="glow"></div>
	<div class="page">
		<div class="top">
			<div class="mark">VOID<span>MARK</span></div>
		</div>
		<section class="hero">
			<div class="cape" aria-hidden="true"></div>
			<p class="kicker">Custom client capes</p>
			<h1>VOIDMARK</h1>
			<div class="rule"></div>
			<p class="lede">A cape every Voidmark user can see in Skyblock. Not a Hypixel cosmetic — it only shows in the client.</p>
			<article class="card">
				<p class="kicker">Want one</p>
				<div class="handle">
					<b>@evilkitten911</b>
					<button type="button" class="copy" id="copy">Copy</button>
				</div>
				<p class="note">Message that Discord user with your Minecraft name. After you are on the list, open the Cape card in Voidmark and crop a photo or paste a PNG.</p>
				<ol class="steps">
					<li><span class="num">1</span><span>Message @evilkitten911 on Discord.</span></li>
					<li><span class="num">2</span><span>You get added. Create cape from the Cape card in-game.</span></li>
					<li><span class="num">3</span><span>Other Voidmark clients pick it up when they join a world.</span></li>
				</ol>
			</article>
		</section>
		<p class="foot">voidmark.cloud</p>
	</div>
	<script>
		(function stars() {
			var c = document.getElementById("stars");
			var ctx = c.getContext("2d");
			var list = [];
			function resize() {
				c.width = window.innerWidth;
				c.height = window.innerHeight;
				list = [];
				var n = Math.floor(c.width * c.height / 8500);
				for (var i = 0; i < n; i++) list.push({ x: Math.random() * c.width, y: Math.random() * c.height, z: Math.random() * 1.2 + 0.2, s: Math.random() * 1.5 + 0.2 });
			}
			function tick() {
				ctx.fillStyle = "#03050a";
				ctx.fillRect(0, 0, c.width, c.height);
				for (var i = 0; i < list.length; i++) {
					var st = list[i];
					st.y += st.z * 0.16;
					if (st.y > c.height) st.y = 0;
					ctx.fillStyle = "rgba(232,237,245," + (0.22 + st.z * 0.5) + ")";
					ctx.fillRect(st.x, st.y, st.s, st.s);
				}
				requestAnimationFrame(tick);
			}
			window.addEventListener("resize", resize);
			resize();
			tick();
		})();
		document.getElementById("copy").onclick = function () {
			var btn = this;
			navigator.clipboard.writeText("@evilkitten911").then(function () {
				btn.textContent = "Copied";
				setTimeout(function () { btn.textContent = "Copy"; }, 1400);
			}).catch(function () {
				btn.textContent = "Copy failed";
			});
		};
	</script>
</body>
</html>
`;

const LOGIN_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>VOIDMARK Admin</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		:root { --bg:#03050a; --pane:#0a0e18; --card:#10151f; --line:#1a2336; --text:#e8edf5; --muted:#8b95a8; --accent:#2fb5ff; --warn:#e8b86d; }
		* { box-sizing: border-box; }
		html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--text); font-family: "Nunito Sans", sans-serif; }
		#stars { position: fixed; inset: 0; z-index: 0; }
		.vignette { position: fixed; inset: 0; z-index: 1; pointer-events: none; background: radial-gradient(900px 500px at 50% 20%, rgba(47,181,255,0.16), transparent 60%); }
		main { position: relative; z-index: 2; width: min(420px, calc(100% - 28px)); margin: 12vh auto; background: color-mix(in srgb, var(--pane) 90%, transparent); border: 1px solid var(--line); border-radius: 18px; padding: 28px 26px 24px; box-shadow: 0 30px 90px #000a, inset 0 1px 0 #ffffff12; backdrop-filter: blur(18px); }
		.kicker { font-size: 11px; letter-spacing: 0.22em; text-transform: uppercase; color: var(--accent); }
		h1 { margin: 8px 0 6px; font-size: 28px; letter-spacing: 0.22em; }
		.rule { width: 22px; height: 3px; background: var(--accent); border-radius: 2px; margin: 10px 0 16px; box-shadow: 0 0 16px var(--accent); }
		p, label { color: var(--muted); font-size: 14px; line-height: 1.5; }
		label { display: block; margin: 0 0 6px; font-weight: 800; color: var(--text); font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; }
		input { width: 100%; background: #070b12; border: 1px solid var(--line); border-radius: 10px; color: var(--text); padding: 12px 14px; font: inherit; outline: none; }
		input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(47,181,255,0.15); }
		button { margin-top: 16px; width: 100%; border: 0; border-radius: 10px; background: var(--accent); color: #041018; font-weight: 800; padding: 12px; cursor: pointer; letter-spacing: 0.08em; text-transform: uppercase; }
		button:disabled { opacity: 0.5; }
		.status { min-height: 20px; margin-top: 14px; font-size: 13px; }
		.status.err { color: var(--warn); }
		.back { display: inline-block; margin-top: 16px; color: var(--muted); text-decoration: none; font-size: 12px; letter-spacing: 0.1em; text-transform: uppercase; }
	</style>
</head>
<body>
	<canvas id="stars"></canvas>
	<div class="vignette"></div>
	<main>
		<div class="kicker">Restricted</div>
		<h1>VOIDMARK</h1>
		<div class="rule"></div>
		<p>Enter the Worker admin secret to open the cape desk.</p>
		<label for="admin">Admin key</label>
		<input id="admin" type="password" autocomplete="current-password" placeholder="Secret">
		<button type="button" id="go">Enter</button>
		<div class="status" id="status"></div>
		<a class="back" href="/">Back to shop</a>
	</main>
	<script>
		(function stars() {
			var c = document.getElementById("stars");
			var ctx = c.getContext("2d");
			var list = [];
			function resize() {
				c.width = window.innerWidth;
				c.height = window.innerHeight;
				list = [];
				var n = Math.floor(c.width * c.height / 9000);
				for (var i = 0; i < n; i++) list.push({ x: Math.random() * c.width, y: Math.random() * c.height, z: Math.random() * 1.2 + 0.2, s: Math.random() * 1.5 + 0.2 });
			}
			function tick() {
				ctx.fillStyle = "#03050a";
				ctx.fillRect(0, 0, c.width, c.height);
				for (var i = 0; i < list.length; i++) {
					var st = list[i];
					st.y += st.z * 0.16;
					if (st.y > c.height) st.y = 0;
					ctx.fillStyle = "rgba(232,237,245," + (0.22 + st.z * 0.5) + ")";
					ctx.fillRect(st.x, st.y, st.s, st.s);
				}
				requestAnimationFrame(tick);
			}
			window.addEventListener("resize", resize);
			resize();
			tick();
		})();
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
				location.href = "/manage";
			} catch (error) {
				sessionStorage.removeItem("voidmark-admin");
				status.className = "status err";
				status.textContent = error.message;
			} finally {
				go.disabled = false;
			}
		}
		go.onclick = enter;
		admin.addEventListener("keydown", function (event) { if (event.key === "Enter") enter(); });
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
	<title>VOIDMARK Desk</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		@font-face {
			font-family: "Minecraft";
			src: url("https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/assets/minecraft.woff2") format("woff2");
			font-display: swap;
		}
		:root { --bg:#03050a; --pane:#0a0e18; --card:#10151f; --line:#1a2336; --text:#e8edf5; --muted:#8b95a8; --accent:#2fb5ff; --warn:#e8b86d; --danger:#ff6b7a; --ok:#3ee0a0; }
		* { box-sizing: border-box; }
		html, body { margin: 0; height: 100%; background: var(--bg); color: var(--text); font-family: "Nunito Sans", sans-serif; }
		#stars { position: fixed; inset: 0; z-index: 0; }
		.app { position: relative; z-index: 2; display: grid; grid-template-columns: 220px minmax(0, 1fr); min-height: 100%; }
		.rail { border-right: 1px solid var(--line); background: color-mix(in srgb, var(--pane) 82%, transparent); padding: 22px 16px; backdrop-filter: blur(18px); }
		.brand { letter-spacing: 0.28em; font-weight: 800; font-size: 13px; }
		.brand span { color: var(--accent); }
		.sub { color: var(--muted); font-size: 11px; letter-spacing: 0.16em; text-transform: uppercase; margin: 8px 0 22px; }
		nav { display: grid; gap: 6px; }
		nav button, .ghost, .warn, .danger, .primary { border: 0; border-radius: 10px; cursor: pointer; font: inherit; font-weight: 800; }
		.out { width: 100%; margin-top: 18px; background: #101822; color: var(--text); border: 1px solid var(--line); padding: 10px; }
		.content { padding: 22px 22px 48px; }
		.top { display: flex; justify-content: space-between; gap: 12px; align-items: end; flex-wrap: wrap; }
		h1 { margin: 0; font-size: 22px; letter-spacing: 0.16em; }
		.hint { color: var(--muted); font-size: 13px; margin: 6px 0 0; }
		.stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 18px 0; }
		.stat { background: color-mix(in srgb, var(--pane) 88%, transparent); border: 1px solid var(--line); border-radius: 14px; padding: 14px; }
		.stat b { display: block; font-size: 22px; color: var(--accent); }
		.stat span { color: var(--muted); font-size: 11px; letter-spacing: 0.12em; text-transform: uppercase; }
		.toolbar, .add, .row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
		input, textarea, select { background: #070b12; border: 1px solid var(--line); border-radius: 10px; color: var(--text); padding: 10px 12px; font: inherit; outline: none; }
		input:focus, textarea:focus { border-color: var(--accent); }
		.grow { flex: 1; min-width: 160px; }
		.primary { background: var(--accent); color: #041018; padding: 10px 14px; }
		.ghost { background: #101822; color: var(--text); border: 1px solid var(--line); padding: 10px 12px; }
		.warn { background: #2a2214; color: var(--warn); border: 1px solid #5a4430; padding: 10px 12px; }
		.danger { background: #2a1216; color: var(--danger); border: 1px solid #5a3038; padding: 10px 12px; }
		button:disabled { opacity: 0.5; }
		.chips { display: flex; gap: 6px; flex-wrap: wrap; margin: 12px 0; }
		.chip { background: #101822; color: var(--muted); border: 1px solid var(--line); padding: 7px 10px; font-size: 12px; }
		.chip.on { color: var(--accent); border-color: var(--accent); }
		.status { min-height: 18px; margin: 10px 0 0; font-size: 13px; }
		.status.ok { color: var(--ok); }
		.status.err { color: var(--warn); }
		.list { display: grid; gap: 10px; margin-top: 14px; }
		.player { display: grid; grid-template-columns: 56px minmax(0, 1fr) 54px; gap: 12px; align-items: center; background: #0e1420; border: 1px solid var(--line); border-radius: 16px; padding: 12px; cursor: pointer; }
		.player:hover { border-color: #2a3a55; box-shadow: 0 0 0 1px rgba(47,181,255,0.15); }
		.head { width: 56px; height: 56px; border-radius: 10px; background: #000; image-rendering: pixelated; }
		.name { font-weight: 800; }
		.uuid { color: var(--muted); font-size: 12px; word-break: break-all; margin-top: 3px; }
		.badges { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
		.badge { font-size: 10px; letter-spacing: 0.1em; text-transform: uppercase; border-radius: 999px; padding: 3px 8px; border: 1px solid var(--line); color: var(--muted); }
		.badge.on { color: var(--accent); border-color: var(--accent); }
		.badge.warn { color: var(--warn); border-color: #5a4430; }
		.cape { width: 42px; height: 66px; object-fit: contain; image-rendering: pixelated; justify-self: center; }
		.nocape { color: var(--muted); font-size: 11px; text-align: center; }
		.empty { color: var(--muted); padding: 28px 8px; }
		.panel { display: block; }
		.card { background: color-mix(in srgb, var(--pane) 88%, transparent); border: 1px solid var(--line); border-radius: 16px; padding: 18px; margin-top: 16px; }
		label { display: block; margin: 0 0 6px; font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; font-weight: 800; }
		textarea { width: 100%; min-height: 140px; resize: vertical; }
		.overlay { position: fixed; inset: 0; background: #03050acc; display: flex; align-items: stretch; justify-content: flex-end; z-index: 20; }
		.overlay[hidden] { display: none; }
		.overlay.center { align-items: center; justify-content: center; padding: 16px; }
		.drawer, .sheet { width: min(440px, 100%); background: var(--pane); border-left: 1px solid var(--line); padding: 22px; overflow: auto; box-shadow: -20px 0 80px #000a; }
		.sheet { width: min(460px, 100%); border: 1px solid var(--line); border-radius: 16px; border-left: 1px solid var(--line); }
		.crop-sheet { width: min(740px, 100%) !important; }
		.crop-wrap { display: grid; grid-template-columns: minmax(0, 1fr) 90px; gap: 14px; margin: 12px 0 16px; align-items: start; }
		.crop-stage { position: relative; background: #070b12; border: 1px solid var(--line); border-radius: 10px; min-height: 220px; display: flex; justify-content: center; align-items: center; overflow: hidden; }
		.crop-holder { position: relative; display: inline-block; }
		.crop-stage canvas { display: block; }
		#crop-box { position: absolute; border: 2px solid var(--accent); box-shadow: 0 0 0 9999px #0009; pointer-events: none; }
		.crop-face canvas { width: 50px; height: 80px; background: #000; border: 1px solid var(--line); border-radius: 6px; display: block; }
		.who { color: var(--muted); font-size: 13px; margin: 0 0 14px; }
		.preview { margin-bottom: 16px; }
		.stage { position: relative; height: 360px; border-radius: 10px; overflow: hidden; background: #070b12; border: 1px solid var(--line); }
		.stage canvas { display: block; width: 100%; height: 100%; }
		.labels { position: absolute; left: 8px; right: 8px; top: 6%; display: flex; flex-direction: column; align-items: center; gap: 1px; pointer-events: none; z-index: 1; }
		.mc-tag { font-family: "Minecraft", monospace; font-size: 16px; line-height: 1; -webkit-font-smoothing: none; image-rendering: pixelated; background: rgba(0, 0, 0, 0.25); color: #fff; text-shadow: 1px 1px 0 #3f3f3f; padding: 1px 4px; white-space: nowrap; max-width: 100%; overflow: hidden; }
		.drag { position: absolute; left: 10px; bottom: 8px; margin: 0; color: var(--muted); font-size: 11px; letter-spacing: 0.06em; pointer-events: none; }
		.preview .nocape { font-size: 12px; margin: 8px 0 0; text-align: left; }
		.codes { display: flex; flex-wrap: wrap; gap: 6px; margin: 0 0 12px; }
		.swatch { width: 28px; height: 28px; border-radius: 6px; border: 1px solid #ffffff33; padding: 0; color: #111; font-size: 11px; font-weight: 800; }
		.swatch.fmt { background: var(--card); color: var(--text); width: auto; padding: 0 8px; }
		.preview-plate { background: rgba(0, 0, 0, 0.25); min-height: 22px; display: flex; align-items: center; justify-content: center; padding: 4px 8px; margin: 0 0 14px; font-family: "Minecraft", monospace; font-size: 16px; line-height: 1; image-rendering: pixelated; -webkit-font-smoothing: none; }
		.field { margin: 0 0 12px; }
		.bypass { display: flex; align-items: center; gap: 8px; font-weight: 800; cursor: pointer; }
		@media (max-width: 860px) {
			.app { grid-template-columns: 1fr; }
			.rail { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; border-right: 0; border-bottom: 1px solid var(--line); }
			.sub { margin: 0 auto 0 0; }
			nav { display: flex; flex-wrap: wrap; }
			.out { margin: 0; width: auto; }
			.stats { grid-template-columns: 1fr 1fr; }
		}
	</style>
</head>
<body>
	<canvas id="stars"></canvas>
	<div class="app">
		<aside class="rail">
			<div class="brand">VOID<span>MARK</span></div>
			<div class="sub">Cape desk</div>
			<button type="button" class="out ghost" id="out">Log out</button>
		</aside>
		<main class="content">
			<div class="status" id="status"></div>
			<section class="panel on" id="view-players">
				<div class="top">
					<div>
						<h1>PLAYERS</h1>
						<p class="hint">Click a row for cape, tag, note, cooldown, fake ban, and dewhitelist.</p>
					</div>
					<div class="toolbar">
						<button type="button" class="ghost" id="refresh">Refresh names</button>
						<button type="button" class="ghost" id="export">Export JSON</button>
					</div>
				</div>
				<div class="stats">
					<div class="stat"><b id="s-all">0</b><span>Listed</span></div>
					<div class="stat"><b id="s-cape">0</b><span>Capes</span></div>
					<div class="stat"><b id="s-tag">0</b><span>Tags</span></div>
					<div class="stat"><b id="s-bypass">0</b><span>Bypass</span></div>
				</div>
				<div class="add">
					<input class="grow" id="uuid" type="text" autocomplete="off" spellcheck="false" placeholder="Username or UUID">
					<button type="button" class="primary" id="add">Add</button>
				</div>
				<div class="toolbar" style="margin-top:12px">
					<input class="grow" id="search" type="search" placeholder="Search name or UUID">
					<select id="sort">
						<option value="name">Sort by name</option>
						<option value="cape">Capes first</option>
						<option value="tag">Tags first</option>
					</select>
				</div>
				<div class="chips" id="filters">
					<button type="button" class="chip on" data-filter="all">All</button>
					<button type="button" class="chip" data-filter="cape">Has cape</button>
					<button type="button" class="chip" data-filter="none">No cape</button>
					<button type="button" class="chip" data-filter="tag">Tagged</button>
					<button type="button" class="chip" data-filter="bypass">Bypass</button>
					<button type="button" class="chip" data-filter="ban">Fake ban</button>
					<button type="button" class="chip" data-filter="lock">On cooldown</button>
				</div>
				<p class="empty" id="empty">No players yet.</p>
				<div class="list" id="list"></div>
				<div class="card">
					<label for="bulktext">Bulk add</label>
					<textarea id="bulktext" placeholder="One username or UUID per line" style="min-height:88px"></textarea>
					<div class="row" style="margin-top:12px">
						<button type="button" class="primary" id="bulkadd">Add all</button>
					</div>
				</div>
			</section>
		</main>
	</div>
	<div class="overlay" id="drawer" hidden>
		<div class="drawer">
			<div class="top">
				<h1 style="font-size:16px">PLAYER</h1>
				<button type="button" class="ghost" id="close">Close</button>
			</div>
			<p class="who" id="d-who"></p>
			<div class="preview">
				<div class="stage" id="d-stage">
					<canvas id="d-model"></canvas>
					<div class="labels">
						<div class="mc-tag" id="d-htag" hidden></div>
						<div class="mc-tag" id="d-nname"></div>
					</div>
					<p class="drag">Drag to rotate</p>
				</div>
				<p class="nocape" id="d-nocape">No cape</p>
			</div>
			<div class="field">
				<label>Note</label>
				<input id="d-note" maxlength="160" placeholder="Paid, cape pending…">
			</div>
			<div class="field">
				<label>Cape from URL</label>
				<div class="row">
					<input class="grow" id="d-url" placeholder="https://…/cape.png">
					<button type="button" class="ghost" id="d-urlgo">Fetch</button>
				</div>
			</div>
			<input id="d-file" type="file" accept="image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp" hidden>
			<div class="row">
				<button type="button" class="ghost" id="d-copy">Copy UUID</button>
				<button type="button" class="ghost" id="d-upload">Create cape</button>
				<button type="button" class="ghost" id="d-tag">Head tag</button>
			</div>
			<div class="row" style="margin-top:8px">
				<button type="button" class="ghost" id="d-dl">Download cape</button>
				<button type="button" class="warn" id="d-reset">Reset cooldown</button>
			</div>
			<label class="bypass" style="margin:14px 0"><input id="d-bypass" type="checkbox"> Upload bypass</label>
			<div class="row" style="margin-top:8px">
				<button type="button" class="danger" id="d-ban">Fake ban</button>
				<button type="button" class="warn" id="d-unban" hidden>Lift fake ban</button>
			</div>
			<button type="button" class="danger" id="d-kick" style="margin-top:12px">Dewhitelist</button>
		</div>
	</div>
	<div class="overlay center" id="tagbox" hidden>
		<div class="sheet">
			<h1 style="font-size:16px">HEAD TAG</h1>
			<p class="who" id="tagwho"></p>
			<label for="tagtext">Text</label>
			<input id="tagtext" type="text" maxlength="48" autocomplete="off" spellcheck="false" placeholder="&amp;bVIP  or  &amp;6&amp;lDonor">
			<div class="codes" id="codes"></div>
			<label>Preview</label>
			<div class="preview-plate" id="tagpreview"></div>
			<div class="row">
				<button type="button" class="primary" id="tagsave">Save</button>
				<button type="button" class="ghost" id="tagclear">Clear</button>
				<button type="button" class="ghost" id="tagcancel">Cancel</button>
			</div>
		</div>
	</div>
	<script src="https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/bundles/skinview3d.bundle.js"></script>
	<script src="/cape-crop.js"></script>
	<script>
		const key = sessionStorage.getItem("voidmark-admin") || "";
		if (!key) location.replace("/admin");
		const status = document.getElementById("status");
		const list = document.getElementById("list");
		const empty = document.getElementById("empty");
		const uuid = document.getElementById("uuid");
		const search = document.getElementById("search");
		const sort = document.getElementById("sort");
		const drawer = document.getElementById("drawer");
		const tagbox = document.getElementById("tagbox");
		const tagtext = document.getElementById("tagtext");
		const tagpreview = document.getElementById("tagpreview");
		const tagwho = document.getElementById("tagwho");
		let cache = [];
		let filter = "all";
		let selected = "";
		let tagTarget = "";
		let skinViewer = null;
		let capeLoadGen = 0;
		const atlasCache = {};
		const atlasWait = {};
		const STAGE_H = 360;
		const COLORS = [
			["0", "#000000"], ["1", "#0000aa"], ["2", "#00aa00"], ["3", "#00aaaa"],
			["4", "#aa0000"], ["5", "#aa00aa"], ["6", "#ffaa00"], ["7", "#aaaaaa"],
			["8", "#555555"], ["9", "#5555ff"], ["a", "#55ff55"], ["b", "#55ffff"],
			["c", "#ff5555"], ["d", "#ff55ff"], ["e", "#ffff55"], ["f", "#ffffff"]
		];
		const FORMATS = [["l", "Bold"], ["o", "Italic"], ["n", "Under"], ["m", "Strike"], ["r", "Reset"]];

		(function stars() {
			var c = document.getElementById("stars");
			var ctx = c.getContext("2d");
			var dots = [];
			function resize() {
				c.width = window.innerWidth;
				c.height = window.innerHeight;
				dots = [];
				var n = Math.floor(c.width * c.height / 11000);
				for (var i = 0; i < n; i++) dots.push({ x: Math.random() * c.width, y: Math.random() * c.height, z: Math.random() * 1.2 + 0.2, s: Math.random() * 1.4 + 0.2 });
			}
			function tick() {
				ctx.fillStyle = "#03050a";
				ctx.fillRect(0, 0, c.width, c.height);
				for (var i = 0; i < dots.length; i++) {
					var st = dots[i];
					st.y += st.z * 0.12;
					if (st.y > c.height) st.y = 0;
					ctx.fillStyle = "rgba(232,237,245," + (0.18 + st.z * 0.45) + ")";
					ctx.fillRect(st.x, st.y, st.s, st.s);
				}
				requestAnimationFrame(tick);
			}
			window.addEventListener("resize", resize);
			resize();
			tick();
		})();

		(function paintCodes() {
			const box = document.getElementById("codes");
			for (const pair of COLORS) {
				const chip = document.createElement("button");
				chip.type = "button";
				chip.className = "swatch";
				chip.style.background = pair[1];
				chip.textContent = pair[0];
				chip.style.color = "018".indexOf(pair[0]) >= 0 ? "#fff" : "#111";
				chip.onclick = function () { insertCode(pair[0]); };
				box.append(chip);
			}
			for (const pair of FORMATS) {
				const chip = document.createElement("button");
				chip.type = "button";
				chip.className = "swatch fmt";
				chip.textContent = pair[1];
				chip.onclick = function () { insertCode(pair[0]); };
				box.append(chip);
			}
		})();

		function insertCode(code) {
			const start = tagtext.selectionStart || tagtext.value.length;
			const end = tagtext.selectionEnd || start;
			tagtext.value = tagtext.value.slice(0, start) + "&" + code + tagtext.value.slice(end);
			tagtext.focus();
			tagtext.setSelectionRange(start + 2, start + 2);
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

		function shadowOf(hex) {
			const n = parseInt(hex.slice(1), 16);
			if (isNaN(n)) return "#3f3f3f";
			const r = Math.floor(((n >> 16) & 255) * 0.25);
			const g = Math.floor(((n >> 8) & 255) * 0.25);
			const b = Math.floor((n & 255) * 0.25);
			function hex2(v) { return (v < 16 ? "0" : "") + v.toString(16); }
			return "#" + hex2(r) + hex2(g) + hex2(b);
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
				span.style.textShadow = "1px 1px 0 " + shadowOf(color);
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
					const next = COLORS.find(function (pair) { return pair[0] === code; });
					if (next) { flush(); color = next[1]; i++; continue; }
					if (code === "l") { flush(); bold = true; i++; continue; }
					if (code === "o") { flush(); italic = true; i++; continue; }
					if (code === "n") { flush(); under = true; i++; continue; }
					if (code === "m") { flush(); strike = true; i++; continue; }
					if (code === "r") { flush(); color = "#ffffff"; bold = italic = strike = under = false; i++; continue; }
					if (raw.charAt(i + 1) === "&") { buffer += "&"; i++; continue; }
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

		function kickAuth(response) {
			if (response.status === 403) {
				sessionStorage.removeItem("voidmark-admin");
				fetch("/api/logout", { method: "POST" }).finally(function () {
					location.replace("/admin");
				});
				return true;
			}
			return false;
		}

		async function api(method, id) {
			const response = await fetch("/api/whitelist", {
				method: method,
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ admin: key, uuid: id || undefined })
			});
			const data = await response.json();
			if (kickAuth(response)) throw new Error("Bad admin key");
			if (!response.ok) throw new Error(data.error || "Failed");
			return data;
		}

		async function admin(path, method, payload) {
			const response = await fetch(path, {
				method: method,
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(Object.assign({ admin: key }, payload || {}))
			});
			const data = await response.json().catch(function () { return {}; });
			if (kickAuth(response)) throw new Error("Bad admin key");
			if (!response.ok) throw new Error(data.error || "Failed");
			return data;
		}

		function formatWait(seconds) {
			const total = Math.max(0, Number(seconds) || 0);
			const hours = Math.floor(total / 3600);
			const minutes = Math.floor((total % 3600) / 60);
			if (hours >= 1) return hours + "h " + minutes + "m";
			if (minutes >= 1) return minutes + "m";
			return total + "s";
		}

		function formatBanLeft(until) {
			var ms = Number(until) - Date.now();
			if (!(ms > 0)) return "";
			var s = Math.floor(ms / 1000);
			var d = Math.floor(s / 86400);
			s = s % 86400;
			var h = Math.floor(s / 3600);
			s = s % 3600;
			var m = Math.floor(s / 60);
			s = s % 60;
			return d + "d " + h + "h " + m + "m " + s + "s";
		}

		function playerBy(id) {
			return cache.find(function (player) { return player.uuid === id; });
		}

		function matches(player) {
			const q = search.value.trim().toLowerCase();
			if (q && (player.name || "").toLowerCase().indexOf(q) < 0 && player.uuid.indexOf(q) < 0 && (player.note || "").toLowerCase().indexOf(q) < 0) return false;
			if (filter === "cape") return player.cape;
			if (filter === "none") return !player.cape;
			if (filter === "tag") return Boolean(player.tag);
			if (filter === "bypass") return Boolean(player.bypass);
			if (filter === "ban") return Boolean(player.ban);
			if (filter === "lock") return !player.bypass && player.retryIn > 0;
			return true;
		}

		function ordered() {
			const rows = cache.filter(matches);
			rows.sort(function (a, b) {
				if (sort.value === "cape") return Number(b.cape) - Number(a.cape) || (a.name || "").localeCompare(b.name || "");
				if (sort.value === "tag") return Number(Boolean(b.tag)) - Number(Boolean(a.tag)) || (a.name || "").localeCompare(b.name || "");
				return (a.name || a.uuid).localeCompare(b.name || b.uuid);
			});
			return rows;
		}

		function paintStats() {
			document.getElementById("s-all").textContent = String(cache.length);
			document.getElementById("s-cape").textContent = String(cache.filter(function (p) { return p.cape; }).length);
			document.getElementById("s-tag").textContent = String(cache.filter(function (p) { return p.tag; }).length);
			document.getElementById("s-bypass").textContent = String(cache.filter(function (p) { return p.bypass; }).length);
		}

		function draw(players) {
			cache = players || cache;
			paintStats();
			const rows = ordered();
			list.innerHTML = "";
			empty.style.display = cache.length ? "none" : "block";
			if (cache.length && !rows.length) empty.style.display = "block";
			if (cache.length && !rows.length) empty.textContent = "No players match that filter.";
			else empty.textContent = "No players yet.";
			for (const player of rows) {
				const row = document.createElement("article");
				row.className = "player";
				const head = document.createElement("img");
				head.className = "head";
				head.width = 56;
				head.height = 56;
				head.alt = player.name || "Head";
				head.src = "https://crafthead.net/helm/" + player.uuid + "/64";
				head.onerror = function () { head.onerror = null; head.src = "https://mc-heads.net/avatar/" + player.uuid + "/64"; };
				const meta = document.createElement("div");
				const name = document.createElement("div");
				name.className = "name";
				name.textContent = player.name || "Unknown";
				const id = document.createElement("div");
				id.className = "uuid";
				id.textContent = player.uuid;
				meta.append(name, id);
				if (player.note) {
					const note = document.createElement("div");
					note.className = "uuid";
					note.textContent = player.note;
					meta.append(note);
				}
				const badges = document.createElement("div");
				badges.className = "badges";
				function badge(label, on, warn) {
					const el = document.createElement("span");
					el.className = "badge" + (on ? " on" : "") + (warn ? " warn" : "");
					el.textContent = label;
					badges.append(el);
				}
				if (player.cape) badge("Cape", true, false);
				if (player.tag) badge("Tag", true, false);
				if (player.bypass) badge("Bypass", true, false);
				if (player.ban) badge(player.banId ? "Ban " + player.banId : "Fake ban", false, true);
				if (player.ban && player.banUntil) badge(formatBanLeft(player.banUntil), false, true);
				if (!player.bypass && player.retryIn > 0) badge(formatWait(player.retryIn), false, true);
				meta.append(badges);
				let capeBox;
				if (player.cape) {
					capeBox = document.createElement("img");
					capeBox.className = "cape";
					capeBox.alt = "Cape";
					capeBox.dataset.uuid = player.uuid;
					loadCapeAtlas(player, function (atlas) {
						if (!atlas || capeBox.dataset.uuid !== player.uuid) return;
						capeBox.src = capeFaceUrl(atlas);
					});
				} else {
					capeBox = document.createElement("div");
					capeBox.className = "nocape";
					capeBox.textContent = "No cape";
				}
				row.append(head, meta, capeBox);
				row.onclick = function () { openPlayer(player.uuid); };
				list.append(row);
			}
			if (selected && playerBy(selected)) fillDrawer(playerBy(selected));
		}

		function fillDrawer(player) {
			selected = player.uuid;
			document.getElementById("d-who").textContent = (player.name || "Unknown") + "  ·  " + player.uuid + (player.banId ? "  ·  " + player.banId + (player.banUntil ? "  ·  " + formatBanLeft(player.banUntil) : "") : "");
			document.getElementById("d-note").value = player.note || "";
			document.getElementById("d-bypass").checked = Boolean(player.bypass);
			document.getElementById("d-url").value = "";
			document.getElementById("d-nname").textContent = player.name || "Unknown";
			const tagEl = document.getElementById("d-htag");
			tagEl.innerHTML = "";
			if (player.tag) {
				tagEl.hidden = false;
				tagEl.appendChild(renderLegacy(player.tag));
			} else {
				tagEl.hidden = true;
			}
			document.getElementById("d-nocape").hidden = Boolean(player.cape);
			document.getElementById("d-dl").disabled = !player.cape;
			document.getElementById("d-reset").disabled = !(player.retryIn > 0);
			document.getElementById("d-ban").hidden = Boolean(player.ban);
			document.getElementById("d-unban").hidden = !player.ban;
			loadModel(player);
		}

		function ensureViewer() {
			if (skinViewer) return skinViewer;
			if (!window.skinview3d || !skinview3d.SkinViewer) return null;
			const canvas = document.getElementById("d-model");
			skinViewer = new skinview3d.SkinViewer({
				canvas: canvas,
				width: Math.max(220, canvas.clientWidth || 360),
				height: STAGE_H,
				zoom: 0.7
			});
			skinViewer.autoRotate = true;
			skinViewer.autoRotateSpeed = 0.38;
			skinViewer.controls.enableZoom = false;
			skinViewer.controls.enablePan = false;
			return skinViewer;
		}

		function loadModel(player) {
			const viewer = ensureViewer();
			if (!viewer) return;
			viewer.renderPaused = false;
			const stage = document.getElementById("d-stage");
			viewer.setSize(Math.max(220, stage.clientWidth || 360), STAGE_H);
			viewer.loadSkin("https://crafthead.net/skin/" + player.uuid).catch(function () {
				return viewer.loadSkin("https://mc-heads.net/skin/" + player.uuid);
			});
			if (player.cape) {
				applyCape(viewer, player);
			} else {
				capeLoadGen++;
				viewer.loadCape(null);
			}
		}

		function applyCape(viewer, player) {
			const gen = ++capeLoadGen;
			loadCapeAtlas(player, function (atlas) {
				if (gen !== capeLoadGen) return;
				if (!atlas) {
					viewer.loadCape(null);
					return;
				}
				try {
					viewer.loadCape(atlas);
				} catch (error) {
					viewer.loadCape(null);
				}
			});
		}

		function capeSrc(player) {
			return "/capes/" + player.uuid + ".png?h=" + encodeURIComponent(player.hash || Date.now());
		}

		function loadCapeAtlas(player, done) {
			const key = player.uuid + ":" + (player.hash || "");
			if (Object.prototype.hasOwnProperty.call(atlasCache, key)) {
				done(atlasCache[key]);
				return;
			}
			if (!atlasWait[key]) atlasWait[key] = [];
			atlasWait[key].push(done);
			if (atlasWait[key].length > 1) return;
			const img = new Image();
			img.crossOrigin = "anonymous";
			img.onload = function () {
				let atlas = null;
				try {
					atlas = capeAtlas(img);
				} catch (error) {
					atlas = null;
				}
				finishCapeAtlas(key, atlas);
			};
			img.onerror = function () {
				finishCapeAtlas(key, null);
			};
			img.src = capeSrc(player);
		}

		function finishCapeAtlas(key, atlas) {
			atlasCache[key] = atlas;
			const waiting = atlasWait[key] || [];
			delete atlasWait[key];
			for (let i = 0; i < waiting.length; i++) waiting[i](atlas);
		}

		function capeFaceUrl(atlas) {
			const scale = atlas.width / 64;
			const face = document.createElement("canvas");
			face.width = 40;
			face.height = 64;
			const ctx = face.getContext("2d");
			ctx.imageSmoothingEnabled = false;
			ctx.drawImage(atlas, scale, scale, 10 * scale, 16 * scale, 0, 0, 40, 64);
			return face.toDataURL("image/png");
		}

		function capeAtlas(img) {
			const w = img.width;
			const h = img.height;
			if (w >= 64 && h >= 32 && w % 64 === 0 && h % 32 === 0 && w / 64 === h / 32) {
				return img;
			}
			return bakeCape(img);
		}

		function bakeCape(img) {
			const srcCanvas = document.createElement("canvas");
			srcCanvas.width = img.width;
			srcCanvas.height = img.height;
			const srcCtx = srcCanvas.getContext("2d", { willReadFrequently: true });
			srcCtx.drawImage(img, 0, 0);
			const src = srcCtx.getImageData(0, 0, img.width, img.height);
			const sw = src.width;
			const sh = src.height;
			const sd = src.data;
			const scale = pickCapeScale(sw, sh);
			const aw = 64 * scale;
			const ah = 32 * scale;
			const out = document.createElement("canvas");
			out.width = aw;
			out.height = ah;
			const ctx = out.getContext("2d");
			const dest = ctx.createImageData(aw, ah);
			const dd = dest.data;
			for (let i = 0; i < dd.length; i += 4) {
				dd[i] = 0;
				dd[i + 1] = 0;
				dd[i + 2] = 0;
				dd[i + 3] = 255;
			}
			const pad = capeBorder(sd, sw, sh);
			const fu = scale;
			const fv = scale;
			const fw = 10 * scale;
			const fh = 16 * scale;
			paintCapeFace(sd, sw, sh, dd, aw, fu, fv, fw, fh, pad);
			copyCapeRect(dd, aw, fu, fv, 11 * scale, 0, fw, fh);
			paintCapeEdges(dd, aw, scale);
			ctx.putImageData(dest, 0, 0);
			return out;
		}

		function pickCapeScale(sw, sh) {
			const needed = Math.max(4, Math.min(16, Math.max(Math.floor(sw / 10), Math.floor(sh / 16))));
			return Math.min(16, Math.max(4, needed));
		}

		function capeBorder(sd, sw, sh) {
			let r = 0;
			let g = 0;
			let b = 0;
			let n = 0;
			function add(x, y) {
				const i = (y * sw + x) * 4;
				r += sd[i];
				g += sd[i + 1];
				b += sd[i + 2];
				n++;
			}
			for (let x = 0; x < sw; x++) {
				add(x, 0);
				add(x, sh - 1);
			}
			for (let y = 1; y < sh - 1; y++) {
				add(0, y);
				add(sw - 1, y);
			}
			if (!n) return [0, 0, 0, 255];
			return [Math.floor(r / n), Math.floor(g / n), Math.floor(b / n), 255];
		}

		function paintCapeFace(sd, sw, sh, dd, aw, dx, dy, dw, dh, pad) {
			const fit = Math.min(dw / sw, dh / sh);
			const rw = sw * fit;
			const rh = sh * fit;
			const ox = dx + (dw - rw) * 0.5;
			const oy = dy + (dh - rh) * 0.5;
			const bilinear = fit < 1;
			for (let y = 0; y < dh; y++) {
				for (let x = 0; x < dw; x++) {
					const px = dx + x + 0.5;
					const py = dy + y + 0.5;
					const di = ((dy + y) * aw + (dx + x)) * 4;
					if (px < ox || py < oy || px >= ox + rw || py >= oy + rh) {
						dd[di] = pad[0];
						dd[di + 1] = pad[1];
						dd[di + 2] = pad[2];
						dd[di + 3] = 255;
						continue;
					}
					const sx = (px - ox) / fit - 0.5;
					const sy = (py - oy) / fit - 0.5;
					const color = bilinear ? sampleBilinear(sd, sw, sh, sx, sy) : sampleNearest(sd, sw, sh, sx, sy);
					dd[di] = color[0];
					dd[di + 1] = color[1];
					dd[di + 2] = color[2];
					dd[di + 3] = 255;
				}
			}
		}

		function paintCapeEdges(dd, aw, scale) {
			const fu = scale;
			const fv = scale;
			const fw = 10 * scale;
			const fh = 16 * scale;
			for (let y = 0; y < fh; y++) {
				const left = ((fv + y) * aw + fu) * 4;
				const right = ((fv + y) * aw + (fu + fw - 1)) * 4;
				for (let x = 0; x < scale; x++) {
					copyPixel(dd, left, ((fv + y) * aw + x) * 4);
					copyPixel(dd, right, ((fv + y) * aw + (11 * scale + x)) * 4);
				}
			}
			for (let x = 0; x < fw; x++) {
				const top = (fv * aw + (fu + x)) * 4;
				const bottom = ((fv + fh - 1) * aw + (fu + x)) * 4;
				for (let y = 0; y < scale; y++) {
					copyPixel(dd, top, (y * aw + (fu + x)) * 4);
					copyPixel(dd, bottom, (y * aw + (11 * scale + x)) * 4);
				}
			}
		}

		function copyCapeRect(dd, aw, x, y, dx, dy, w, h) {
			for (let j = 0; j < h; j++) {
				for (let i = 0; i < w; i++) {
					copyPixel(dd, ((y + j) * aw + (x + i)) * 4, ((y + dy + j) * aw + (x + dx + i)) * 4);
				}
			}
		}

		function copyPixel(dd, from, to) {
			dd[to] = dd[from];
			dd[to + 1] = dd[from + 1];
			dd[to + 2] = dd[from + 2];
			dd[to + 3] = dd[from + 3];
		}

		function sampleNearest(sd, sw, sh, x, y) {
			const sx = clampCape(Math.floor(x + 0.5), 0, sw - 1);
			const sy = clampCape(Math.floor(y + 0.5), 0, sh - 1);
			const i = (sy * sw + sx) * 4;
			return [sd[i], sd[i + 1], sd[i + 2]];
		}

		function sampleBilinear(sd, sw, sh, x, y) {
			const x0 = clampCape(Math.floor(x), 0, sw - 1);
			const y0 = clampCape(Math.floor(y), 0, sh - 1);
			const x1 = clampCape(x0 + 1, 0, sw - 1);
			const y1 = clampCape(y0 + 1, 0, sh - 1);
			const tx = x - Math.floor(x);
			const ty = y - Math.floor(y);
			return mixCape(
				mixCape(pixelAt(sd, sw, x0, y0), pixelAt(sd, sw, x1, y0), tx),
				mixCape(pixelAt(sd, sw, x0, y1), pixelAt(sd, sw, x1, y1), tx),
				ty
			);
		}

		function pixelAt(sd, sw, x, y) {
			const i = (y * sw + x) * 4;
			return [sd[i], sd[i + 1], sd[i + 2]];
		}

		function mixCape(a, b, t) {
			if (t < 0) t = 0;
			if (t > 1) t = 1;
			return [
				Math.round(a[0] + (b[0] - a[0]) * t),
				Math.round(a[1] + (b[1] - a[1]) * t),
				Math.round(a[2] + (b[2] - a[2]) * t)
			];
		}

		function clampCape(value, min, max) {
			return Math.max(min, Math.min(max, value));
		}

		function openPlayer(id) {
			const player = playerBy(id);
			if (!player) return;
			drawer.hidden = false;
			fillDrawer(player);
			requestAnimationFrame(function () {
				if (selected === player.uuid) loadModel(player);
			});
		}

		function closeDrawer() {
			drawer.hidden = true;
			selected = "";
			if (skinViewer) skinViewer.renderPaused = true;
		}

		async function loadPlayers(force) {
			const data = await api("POST");
			draw(data.players || []);
			if (force) setStatus(true, "Names refreshed from Mojang.");
			else setStatus(true, "Loaded " + cache.length + " players.");
		}

		document.querySelectorAll("#filters .chip").forEach(function (btn) {
			btn.onclick = function () {
				filter = btn.dataset.filter;
				document.querySelectorAll("#filters .chip").forEach(function (el) { el.classList.toggle("on", el === btn); });
				draw(cache);
			};
		});
		search.oninput = function () { draw(cache); };
		sort.onchange = function () { draw(cache); };
		document.getElementById("add").onclick = function () {
			if (!uuid.value.trim()) { setStatus(false, "Enter a username or UUID"); return; }
			api("PUT", uuid.value.trim()).then(function (data) {
				draw(data.players || []);
				uuid.value = "";
				setStatus(true, "Whitelisted. They can set a cape in Voidmark.");
			}).catch(function (error) { setStatus(false, error.message); });
		};
		uuid.addEventListener("keydown", function (event) { if (event.key === "Enter") document.getElementById("add").click(); });
		document.getElementById("refresh").onclick = function () {
			loadPlayers(true).catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("export").onclick = function () {
			const blob = new Blob([JSON.stringify(cache, null, 2)], { type: "application/json" });
			const a = document.createElement("a");
			a.href = URL.createObjectURL(blob);
			a.download = "voidmark-whitelist.json";
			a.click();
		};
		document.getElementById("bulkadd").onclick = function () {
			admin("/api/bulk", "PUT", { text: document.getElementById("bulktext").value }).then(function (data) {
				draw(data.players || []);
				document.getElementById("bulktext").value = "";
				const fail = (data.failed || []).join(", ");
				setStatus(true, "Added " + data.added + ". Skipped " + data.skipped + "." + (fail ? " Unknown: " + fail : ""));
			}).catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("out").onclick = function () {
			sessionStorage.removeItem("voidmark-admin");
			fetch("/api/logout", { method: "POST" }).finally(function () {
				location.replace("/admin");
			});
		};
		document.getElementById("close").onclick = closeDrawer;
		drawer.addEventListener("click", function (event) { if (event.target === drawer) closeDrawer(); });
		window.addEventListener("resize", function () {
			if (drawer.hidden || !skinViewer || !selected) return;
			skinViewer.setSize(Math.max(220, document.getElementById("d-stage").clientWidth), STAGE_H);
		});
		document.getElementById("d-copy").onclick = function () {
			if (!selected) return;
			navigator.clipboard.writeText(selected).then(function () { setStatus(true, "UUID copied."); });
		};
		function uploadCapeBlob(blob) {
			return fetch("/api/cape", { method: "PUT", headers: { "X-UUID": selected, "X-Admin": key }, body: blob })
				.then(function (response) { return response.json().then(function (data) { if (!response.ok) throw new Error(data.error || "Upload failed"); }); })
				.then(function () { return loadPlayers(false); })
				.then(function () { setStatus(true, "Cape updated."); });
		}
		document.getElementById("d-upload").onclick = function () { document.getElementById("d-file").click(); };
		document.getElementById("d-file").onchange = function () {
			const file = document.getElementById("d-file").files[0];
			if (!file || !selected) return;
			if (!window.VoidmarkCapeCrop) {
				uploadCapeBlob(file).catch(function (error) { setStatus(false, error.message); }).finally(function () { document.getElementById("d-file").value = ""; });
				return;
			}
			VoidmarkCapeCrop.open(file, function (png) {
				uploadCapeBlob(png).catch(function (error) { setStatus(false, error.message); }).finally(function () { document.getElementById("d-file").value = ""; });
			}, function () { document.getElementById("d-file").value = ""; });
		};
		document.getElementById("d-urlgo").onclick = function () {
			const url = document.getElementById("d-url").value.trim();
			if (!url || !selected) return;
			fetch("/api/cape/import", {
				method: "POST",
				headers: { "Content-Type": "application/json", "X-Admin": key },
				body: JSON.stringify({ url: url })
			}).then(function (response) {
				if (!response.ok) {
					return response.json().then(function (data) { throw new Error(data.error || "Fetch failed"); });
				}
				return response.blob();
			}).then(function (blob) {
				if (!window.VoidmarkCapeCrop) return uploadCapeBlob(blob);
				return new Promise(function (resolve, reject) {
					VoidmarkCapeCrop.open(blob, function (png) { uploadCapeBlob(png).then(resolve).catch(reject); }, function () { resolve(); });
				});
			}).catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-note").addEventListener("change", function () {
			if (!selected) return;
			admin("/api/note", "PUT", { uuid: selected, note: document.getElementById("d-note").value })
				.then(function (data) { draw(data.players || []); setStatus(true, "Note saved."); })
				.catch(function (error) { setStatus(false, error.message); });
		});
		document.getElementById("d-bypass").onchange = function () {
			if (!selected) return;
			admin("/api/bypass", "PUT", { uuid: selected, bypass: document.getElementById("d-bypass").checked })
				.then(function (data) { draw(data.players || []); setStatus(true, document.getElementById("d-bypass").checked ? "Bypass on." : "Bypass off."); })
				.catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-reset").onclick = function () {
			if (!selected) return;
			admin("/api/cooldown", "DELETE", { uuid: selected })
				.then(function (data) { draw(data.players || []); setStatus(true, "Cooldown cleared."); })
				.catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-dl").onclick = function () {
			if (!selected) return;
			const a = document.createElement("a");
			a.href = "/capes/" + selected + ".png";
			a.download = selected + ".png";
			a.click();
		};
		document.getElementById("d-tag").onclick = function () {
			const player = playerBy(selected);
			if (!player) return;
			tagTarget = player.uuid;
			tagwho.textContent = (player.name || "Unknown") + "  " + player.uuid;
			tagtext.value = player.tag || "";
			tagbox.hidden = false;
			paintPreview();
		};
		document.getElementById("d-ban").onclick = function () {
			const player = playerBy(selected);
			if (!player) return;
			admin("/api/ban", "PUT", { uuid: selected })
				.then(function (data) {
					draw(data.players || []);
					setStatus(true, "Fake ban " + (data.banId || "") + " queued. They see it within a couple of seconds.");
				})
				.catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-unban").onclick = function () {
			if (!selected) return;
			admin("/api/ban", "DELETE", { uuid: selected })
				.then(function (data) {
					draw(data.players || []);
					setStatus(true, "Fake ban lifted.");
				})
				.catch(function (error) { setStatus(false, error.message); });
		};
		document.getElementById("d-kick").onclick = function () {
			if (!selected) return;
			if (!confirm("Remove this player from the cape list?")) return;
			api("DELETE", selected).then(function (data) {
				closeDrawer();
				draw(data.players || []);
				setStatus(true, "Removed from the list.");
			}).catch(function (error) { setStatus(false, error.message); });
		};
		function closeTag() { tagbox.hidden = true; tagTarget = ""; }
		async function saveTag(clear) {
			if (!tagTarget) return;
			try {
				const data = await admin("/api/tag", clear ? "DELETE" : "PUT", { uuid: tagTarget, tag: tagtext.value });
				draw(data.players || []);
				closeTag();
				setStatus(true, clear || !tagtext.value.trim() ? "Head tag cleared." : "Head tag saved.");
			} catch (error) {
				setStatus(false, error.message);
			}
		}
		tagtext.addEventListener("input", paintPreview);
		document.getElementById("tagsave").onclick = function () { saveTag(false); };
		document.getElementById("tagclear").onclick = function () { saveTag(true); };
		document.getElementById("tagcancel").onclick = closeTag;
		tagbox.addEventListener("click", function (event) { if (event.target === tagbox) closeTag(); });
		loadPlayers(false).catch(function (error) { setStatus(false, error.message); });
	</script>
</body>
</html>
`;
