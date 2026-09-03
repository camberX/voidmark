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
	if (request.method === "GET" && env.ASSETS) {
		const asset = await env.ASSETS.fetch(request);
		if (asset.status !== 404) {
			return asset;
		}
	}
	if (request.method === "GET" && path === "/manage.html") {
		return page(MANAGE_HTML);
	}
	if (request.method === "GET" && path === "/admin.html") {
		return page(LOGIN_HTML);
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
		paypal: stored.paypal || env.PAYPAL || "your-paypal@email.com",
		price: stored.price || env.PRICE || "$1",
		title: stored.title || env.TITLE || "VOIDMARK Capes",
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

const STORE_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>VOIDMARK Capes</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link href="https://fonts.googleapis.com/css2?family=Nunito+Sans:wght@500;700;800&display=swap" rel="stylesheet">
	<style>
		:root { --bg:#03050a; --pane:#0a0e18; --card:#10151f; --line:#1a2336; --text:#e8edf5; --muted:#8b95a8; --accent:#2fb5ff; --warn:#e8b86d; }
		* { box-sizing: border-box; }
		html, body { margin: 0; min-height: 100%; background: var(--bg); color: var(--text); font-family: "Nunito Sans", sans-serif; }
		#stars { position: fixed; inset: 0; z-index: 0; }
		.vignette { position: fixed; inset: 0; z-index: 1; pointer-events: none; background: radial-gradient(1200px 700px at 50% -10%, rgba(47,181,255,0.18), transparent 55%), linear-gradient(180deg, transparent, #03050ad9 92%); }
		.wrap { position: relative; z-index: 2; width: min(980px, calc(100% - 28px)); margin: 0 auto; padding: 28px 0 64px; }
		.top { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
		.mark { letter-spacing: 0.34em; font-weight: 800; font-size: 13px; }
		.mark span { color: var(--accent); }
		.admin { color: var(--muted); text-decoration: none; font-size: 12px; letter-spacing: 0.12em; text-transform: uppercase; border: 1px solid var(--line); padding: 8px 12px; border-radius: 8px; background: color-mix(in srgb, var(--pane) 80%, transparent); }
		.admin:hover { color: var(--text); border-color: var(--accent); }
		.hero { margin: 72px 0 36px; }
		.hero h1 { margin: 0 0 10px; font-size: clamp(40px, 7vw, 72px); letter-spacing: 0.18em; line-height: 0.95; }
		.rule { width: 28px; height: 3px; background: var(--accent); border-radius: 2px; margin: 14px 0 18px; box-shadow: 0 0 18px var(--accent); }
		.lede { max-width: 520px; color: var(--muted); font-size: 16px; line-height: 1.6; }
		.grid { display: grid; grid-template-columns: 1.1fr 0.9fr; gap: 16px; }
		.card { background: color-mix(in srgb, var(--pane) 88%, transparent); border: 1px solid var(--line); border-radius: 18px; padding: 22px; box-shadow: 0 24px 80px #0008, inset 0 1px 0 #ffffff10; backdrop-filter: blur(16px); }
		.price { font-size: 42px; font-weight: 800; color: var(--accent); letter-spacing: 0.04em; }
		.kicker { font-size: 11px; letter-spacing: 0.18em; text-transform: uppercase; color: var(--muted); margin-bottom: 8px; }
		.steps { display: grid; gap: 12px; margin: 0; padding: 0; list-style: none; }
		.steps li { display: grid; grid-template-columns: 28px 1fr; gap: 10px; align-items: start; color: var(--muted); font-size: 14px; line-height: 1.45; }
		.num { width: 28px; height: 28px; border-radius: 8px; background: #041018; border: 1px solid var(--accent); color: var(--accent); display: grid; place-items: center; font-size: 12px; font-weight: 800; }
		.paypal { margin-top: 16px; padding: 12px 14px; border-radius: 10px; background: #041018; border: 1px dashed var(--line); font-weight: 800; word-break: break-all; }
		.warn { color: var(--warn); font-size: 13px; margin-top: 14px; line-height: 1.5; }
		.foot { margin-top: 28px; color: var(--muted); font-size: 12px; letter-spacing: 0.08em; text-transform: uppercase; }
		@media (max-width: 800px) {
			.hero { margin-top: 40px; }
			.grid { grid-template-columns: 1fr; }
		}
	</style>
</head>
<body>
	<canvas id="stars"></canvas>
	<div class="vignette"></div>
	<div class="wrap">
		<div class="top">
			<div class="mark">VOID<span>MARK</span></div>
			<a class="admin" href="/admin.html">Admin</a>
		</div>
		<section class="hero">
			<div class="kicker">Hypixel Skyblock client capes</div>
			<h1 id="title">VOIDMARK</h1>
			<div class="rule"></div>
			<p class="lede" id="blurb">A custom cape that every Voidmark user sees. Pay Friends and Family, send your Minecraft name, and it shows in-game after you are added to the list.</p>
		</section>
		<div class="grid">
			<article class="card">
				<div class="kicker">How it works</div>
				<ol class="steps">
					<li><span class="num">1</span><span>Send the listed amount as PayPal Friends and Family. Include your Minecraft username in the note.</span></li>
					<li><span class="num">2</span><span>You get whitelisted. Open Voidmark, open the Cape card, and set a PNG.</span></li>
					<li><span class="num">3</span><span>Other Voidmark clients pick it up when they join a world. Capes only show for Voidmark users.</span></li>
				</ol>
				<p class="warn">Friends and Family has no PayPal purchase protection. This is a cape for a client mod, not a Hypixel cosmetic.</p>
			</article>
			<article class="card">
				<div class="kicker">PayPal Friends and Family</div>
				<div class="price" id="price">$1</div>
				<p class="lede" style="margin: 8px 0 0;">Send to this address, then wait to be added.</p>
				<div class="paypal" id="paypal">Loading…</div>
			</article>
		</div>
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
				var g = ctx.createRadialGradient(c.width * 0.5, 0, 10, c.width * 0.5, 180, Math.max(c.width, 800) * 0.55);
				g.addColorStop(0, "rgba(47,181,255,0.16)");
				g.addColorStop(1, "rgba(3,5,10,0)");
				ctx.fillStyle = g;
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
		fetch("/api/config").then(function (response) { return response.json(); }).then(function (data) {
			if (data.title) document.getElementById("title").textContent = data.title.replace(/ capes$/i, "") || "VOIDMARK";
			if (data.price) document.getElementById("price").textContent = data.price;
			if (data.paypal) document.getElementById("paypal").textContent = data.paypal;
			if (data.blurb) document.getElementById("blurb").textContent = data.blurb;
			if (data.title) document.title = data.title;
		}).catch(function () {
			document.getElementById("paypal").textContent = "Unavailable";
		});
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
			<input id="d-file" type="file" accept="image/png,.png" hidden>
			<div class="row">
				<button type="button" class="ghost" id="d-copy">Copy UUID</button>
				<button type="button" class="ghost" id="d-upload">Upload PNG</button>
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
	<script>
		const key = sessionStorage.getItem("voidmark-admin") || "";
		if (!key) location.replace("/admin.html");
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
				location.replace("/admin.html");
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
			location.replace("/admin.html");
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
		document.getElementById("d-upload").onclick = function () { document.getElementById("d-file").click(); };
		document.getElementById("d-file").onchange = function () {
			const file = document.getElementById("d-file").files[0];
			if (!file || !selected) return;
			fetch("/api/cape", { method: "PUT", headers: { "X-UUID": selected, "X-Admin": key }, body: file })
				.then(function (response) { return response.json().then(function (data) { if (!response.ok) throw new Error(data.error || "Upload failed"); }); })
				.then(function () { return loadPlayers(false); })
				.then(function () { setStatus(true, "Cape updated."); })
				.catch(function (error) { setStatus(false, error.message); })
				.finally(function () { document.getElementById("d-file").value = ""; });
		};
		document.getElementById("d-urlgo").onclick = function () {
			const url = document.getElementById("d-url").value.trim();
			if (!url || !selected) return;
			fetch("/api/cape", {
				method: "PUT",
				headers: { "Content-Type": "application/json", "X-UUID": selected, "X-Admin": key },
				body: JSON.stringify({ url: url })
			}).then(function (response) { return response.json().then(function (data) { if (!response.ok) throw new Error(data.error || "Fetch failed"); }); })
				.then(function () { return loadPlayers(false); })
				.then(function () { setStatus(true, "Cape fetched and saved."); })
				.catch(function (error) { setStatus(false, error.message); });
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
