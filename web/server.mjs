import { createHash } from "node:crypto";
import { createReadStream, existsSync, readFileSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL(".", import.meta.url));
const PUBLIC = join(ROOT, "public");
const DATA = join(ROOT, "data");
const CAPES = join(DATA, "capes");
const PORT = Number(process.env.VOIDMARK_CAPE_PORT || 43150);
const ADMIN = process.env.VOIDMARK_CAPE_ADMIN || "change-me";
const MAX_BYTES = 2 * 1024 * 1024;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

await mkdir(CAPES, { recursive: true });

const store = {
	whitelist: await loadJson(join(DATA, "whitelist.json"), []),
	names: await loadJson(join(DATA, "names.json"), {}),
	namesAt: await loadJson(join(DATA, "namesAt.json"), {}),
	tags: await loadJson(join(DATA, "tags.json"), {}),
	bypass: await loadJson(join(DATA, "bypass.json"), {}),
	capeAt: await loadJson(join(DATA, "capeAt.json"), {}),
	config: await loadJson(join(DATA, "config.json"), {
		paypal: "your-paypal@email.com",
		price: "$1",
		title: "VOIDMARK Capes",
		blurb: ""
	}),
	notes: await loadJson(join(DATA, "notes.json"), {})
};
if (!store.tags || typeof store.tags !== "object" || Array.isArray(store.tags)) {
	store.tags = {};
}
if (!store.bypass || typeof store.bypass !== "object" || Array.isArray(store.bypass)) {
	store.bypass = {};
}
if (!store.capeAt || typeof store.capeAt !== "object" || Array.isArray(store.capeAt)) {
	store.capeAt = {};
}
if (!store.namesAt || typeof store.namesAt !== "object" || Array.isArray(store.namesAt)) {
	store.namesAt = {};
}
if (!store.notes || typeof store.notes !== "object" || Array.isArray(store.notes)) {
	store.notes = {};
}
if (!store.config || typeof store.config !== "object" || Array.isArray(store.config)) {
	store.config = { paypal: "your-paypal@email.com", price: "$1", title: "VOIDMARK Capes", blurb: "" };
}

const MIME = {
	".html": "text/html; charset=utf-8",
	".css": "text/css; charset=utf-8",
	".js": "text/javascript; charset=utf-8",
	".png": "image/png",
	".svg": "image/svg+xml",
	".json": "application/json; charset=utf-8"
};

const server = createServer(async (req, res) => {
	try {
		await route(req, res);
	} catch (error) {
		console.error(error);
		json(res, 500, { error: "Server error" });
	}
});

async function route(req, res) {
	const url = new URL(req.url || "/", `http://${req.headers.host || "127.0.0.1"}`);
	const path = url.pathname;

	if (req.method === "OPTIONS") {
		res.writeHead(204, cors()).end();
		return;
	}
	if (req.method === "GET" && path === "/api/config") {
		json(res, 200, shopConfig());
		return;
	}
	if (req.method === "PUT" && path === "/api/config") {
		await handleShopConfig(req, res);
		return;
	}
	if (req.method === "GET" && path.startsWith("/api/cape/")) {
		const id = normalizeUuid(path.slice("/api/cape/".length));
		if (!id) {
			json(res, 400, { error: "Bad UUID" });
			return;
		}
		const file = capePath(id);
		if (!existsSync(file)) {
			json(res, 200, { has: false, hash: "", allowed: whitelisted(id), tag: tagFor(id), bypass: hasBypass(id), retryIn: capeRetrySec(id) });
			return;
		}
		json(res, 200, { has: true, hash: hashFile(file), allowed: whitelisted(id), tag: tagFor(id), bypass: hasBypass(id), retryIn: capeRetrySec(id) });
		return;
	}
	if (req.method === "GET" && path.startsWith("/capes/") && path.endsWith(".png")) {
		const id = normalizeUuid(path.slice("/capes/".length, -4));
		if (!id) {
			res.writeHead(400, cors()).end();
			return;
		}
		const file = capePath(id);
		if (!existsSync(file)) {
			res.writeHead(404, cors()).end();
			return;
		}
		res.writeHead(200, {
			...cors(),
			"Content-Type": "image/png",
			"Cache-Control": "no-store",
			ETag: `"${hashFile(file)}"`
		});
		createReadStream(file).pipe(res);
		return;
	}
	if ((req.method === "POST" || req.method === "PUT") && path === "/api/cape") {
		await handlePublish(req, res);
		return;
	}
	if (req.method === "DELETE" && path === "/api/cape") {
		await handleDelete(req, res);
		return;
	}
	if (req.method === "POST" && path === "/api/grant") {
		json(res, 410, { error: "Codes are gone. Whitelist the UUID instead." });
		return;
	}
	if ((req.method === "POST" || req.method === "PUT" || req.method === "DELETE") && path === "/api/whitelist") {
		await handleWhitelist(req, res);
		return;
	}
	if ((req.method === "PUT" || req.method === "DELETE") && path === "/api/tag") {
		await handleTag(req, res);
		return;
	}
	if (req.method === "PUT" && path === "/api/bypass") {
		await handleBypass(req, res);
		return;
	}
	if ((req.method === "PUT" || req.method === "DELETE") && path === "/api/note") {
		await handleNote(req, res);
		return;
	}
	if (req.method === "DELETE" && path === "/api/cooldown") {
		await handleCooldown(req, res);
		return;
	}
	if (req.method === "PUT" && path === "/api/bulk") {
		await handleBulk(req, res);
		return;
	}
	if (req.method === "GET") {
		await servePublic(res, path === "/" ? "/index.html" : path);
		return;
	}
	json(res, 404, { error: "Not found" });
}

async function handlePublish(req, res) {
	const uuid = normalizeUuid(header(req, "x-uuid"));
	if (!uuid) {
		json(res, 400, { error: "Need a valid UUID" });
		return;
	}
	if (!whitelisted(uuid)) {
		json(res, 403, { error: "uuid not whitelisted" });
		return;
	}
	const adminOk = adminHeaderOk(req);
	const locked = capeRetryMs(uuid);
	if (!adminOk && locked > 0) {
		json(res, 429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1000) });
		return;
	}
	const body = await readCapeBytes(req, adminOk);
	if (!body) {
		json(res, 400, { error: adminOk ? "Need a PNG or a cape URL" : "Not a PNG" });
		return;
	}
	if (!isPng(body)) {
		json(res, 400, { error: "Not a PNG" });
		return;
	}
	await writeFile(capePath(uuid), body);
	if (!adminOk) {
		await touchCapeAt(uuid);
	}
	json(res, 200, { ok: true, uuid });
}

async function handleDelete(req, res) {
	const uuid = normalizeUuid(header(req, "x-uuid"));
	if (!uuid) {
		json(res, 400, { error: "Need a UUID" });
		return;
	}
	if (!whitelisted(uuid)) {
		json(res, 403, { error: "uuid not whitelisted" });
		return;
	}
	const adminOk = adminHeaderOk(req);
	const locked = capeRetryMs(uuid);
	if (!adminOk && locked > 0) {
		json(res, 429, { error: "Cape can be changed once per 24 hours", retryIn: Math.ceil(locked / 1000) });
		return;
	}
	const file = capePath(uuid);
	if (existsSync(file)) {
		const { unlink } = await import("node:fs/promises");
		await unlink(file);
	}
	if (!adminOk) {
		await touchCapeAt(uuid);
	}
	json(res, 200, { ok: true });
}

async function handleWhitelist(req, res) {
	let body;
	try {
		body = JSON.parse((await readBody(req, 4096)).toString("utf8") || "{}");
	} catch {
		body = {};
	}
	if ((body.admin || "") !== ADMIN) {
		json(res, 403, { error: "Bad admin key" });
		return;
	}
	if (req.method === "POST" && !body.uuid && !body.name) {
		json(res, 200, { uuids: store.whitelist, players: await playersFor(store.whitelist, true) });
		return;
	}
	const resolved = await resolvePlayer(body.uuid || body.name);
	if (!resolved.uuid) {
		const raw = String(body.uuid || body.name || "").trim();
		if (!raw) {
			json(res, 400, { error: "Need a username or UUID" });
			return;
		}
		if (sanitizeUsername(raw)) {
			json(res, 404, { error: "Unknown player" });
			return;
		}
		json(res, 400, { error: "Need a username or UUID" });
		return;
	}
	const uuid = resolved.uuid;
	if (req.method === "DELETE") {
		store.whitelist = store.whitelist.filter((id) => id !== uuid);
		forgetPlayer(uuid);
		const file = capePath(uuid);
		if (existsSync(file)) {
			const { unlink } = await import("node:fs/promises");
			await unlink(file);
		}
	} else if (!store.whitelist.includes(uuid)) {
		store.whitelist.push(uuid);
	}
	if (req.method !== "DELETE" && resolved.name) {
		rememberName(uuid, resolved.name);
	}
	const players = await playersFor(store.whitelist, req.method !== "DELETE" && !resolved.name);
	if (req.method !== "DELETE" && resolved.name) {
		rememberName(uuid, resolved.name);
	}
	await persistStore();
	json(res, 200, { ok: true, uuids: store.whitelist, players });
}

async function handleTag(req, res) {
	let body;
	try {
		body = JSON.parse((await readBody(req, 4096)).toString("utf8") || "{}");
	} catch {
		body = {};
	}
	if ((body.admin || "") !== ADMIN) {
		json(res, 403, { error: "Bad admin key" });
		return;
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		json(res, 400, { error: "Need a valid UUID" });
		return;
	}
	if (!whitelisted(uuid)) {
		json(res, 403, { error: "uuid not whitelisted" });
		return;
	}
	const tag = req.method === "DELETE" ? "" : sanitizeTag(body.tag);
	if (tag) {
		store.tags[uuid] = tag;
	} else {
		delete store.tags[uuid];
	}
	await saveJson(join(DATA, "tags.json"), store.tags);
	json(res, 200, { ok: true, tag, players: await playersFor(store.whitelist) });
}

async function handleBypass(req, res) {
	let body;
	try {
		body = JSON.parse((await readBody(req, 4096)).toString("utf8") || "{}");
	} catch {
		body = {};
	}
	if ((body.admin || "") !== ADMIN) {
		json(res, 403, { error: "Bad admin key" });
		return;
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		json(res, 400, { error: "Need a valid UUID" });
		return;
	}
	if (!whitelisted(uuid)) {
		json(res, 403, { error: "uuid not whitelisted" });
		return;
	}
	if (body.bypass) {
		store.bypass[uuid] = true;
	} else {
		delete store.bypass[uuid];
	}
	await saveJson(join(DATA, "bypass.json"), store.bypass);
	json(res, 200, { ok: true, bypass: Boolean(store.bypass[uuid]), players: await playersFor(store.whitelist) });
}

async function handleNote(req, res) {
	const body = await readAdminBody(req, res);
	if (!body) {
		return;
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		json(res, 400, { error: "Need a valid UUID" });
		return;
	}
	if (!whitelisted(uuid)) {
		json(res, 403, { error: "uuid not whitelisted" });
		return;
	}
	const note = req.method === "DELETE" ? "" : sanitizeNote(body.note);
	if (note) {
		store.notes[uuid] = note;
	} else {
		delete store.notes[uuid];
	}
	await saveJson(join(DATA, "notes.json"), store.notes);
	json(res, 200, { ok: true, note, players: await playersFor(store.whitelist) });
}

async function handleCooldown(req, res) {
	const body = await readAdminBody(req, res);
	if (!body) {
		return;
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		json(res, 400, { error: "Need a valid UUID" });
		return;
	}
	if (!whitelisted(uuid)) {
		json(res, 403, { error: "uuid not whitelisted" });
		return;
	}
	delete store.capeAt[uuid];
	await saveJson(join(DATA, "capeAt.json"), store.capeAt);
	json(res, 200, { ok: true, retryIn: 0, players: await playersFor(store.whitelist) });
}

async function handleBulk(req, res) {
	const body = await readAdminBody(req, res, 16384);
	if (!body) {
		return;
	}
	const raw = Array.isArray(body.names) ? body.names : String(body.text || "").split(/[\n,]+/);
	const names = raw.map((value) => String(value || "").trim()).filter(Boolean).slice(0, 25);
	if (!names.length) {
		json(res, 400, { error: "Paste usernames or UUIDs, one per line" });
		return;
	}
	const added = [];
	const skipped = [];
	const failed = [];
	for (const name of names) {
		const resolved = await resolvePlayer(name);
		if (!resolved.uuid) {
			failed.push(name);
			continue;
		}
		if (store.whitelist.includes(resolved.uuid)) {
			skipped.push(name);
			continue;
		}
		store.whitelist.push(resolved.uuid);
		if (resolved.name) {
			rememberName(resolved.uuid, resolved.name);
		}
		added.push(resolved.name || resolved.uuid);
	}
	const players = await playersFor(store.whitelist, true);
	await persistStore();
	json(res, 200, { ok: true, added: added.length, skipped: skipped.length, failed, players });
}

async function handleShopConfig(req, res) {
	const body = await readAdminBody(req, res);
	if (!body) {
		return;
	}
	store.config = {
		...shopConfig(),
		paypal: sanitizePaypal(body.paypal),
		price: sanitizePrice(body.price),
		title: sanitizeTitle(body.title),
		blurb: sanitizeBlurb(body.blurb)
	};
	await saveJson(join(DATA, "config.json"), store.config);
	json(res, 200, { ok: true, ...store.config });
}

async function readAdminBody(req, res, max) {
	let body;
	try {
		body = JSON.parse((await readBody(req, max || 4096)).toString("utf8") || "{}");
	} catch {
		body = {};
	}
	if ((body.admin || "") !== ADMIN) {
		json(res, 403, { error: "Bad admin key" });
		return null;
	}
	return body;
}

async function readCapeBytes(req, adminOk) {
	const type = header(req, "content-type").toLowerCase();
	if (type.includes("application/json")) {
		if (!adminOk) {
			return null;
		}
		let payload;
		try {
			payload = JSON.parse((await readBody(req, 4096)).toString("utf8") || "{}");
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
			return Buffer.from(await response.arrayBuffer());
		} catch {
			return null;
		}
	}
	return readBody(req, MAX_BYTES);
}

async function persistStore() {
	await saveJson(join(DATA, "whitelist.json"), store.whitelist);
	await saveJson(join(DATA, "names.json"), store.names);
	await saveJson(join(DATA, "namesAt.json"), store.namesAt);
	await saveJson(join(DATA, "tags.json"), store.tags);
	await saveJson(join(DATA, "bypass.json"), store.bypass);
	await saveJson(join(DATA, "capeAt.json"), store.capeAt);
	await saveJson(join(DATA, "notes.json"), store.notes);
	await saveJson(join(DATA, "config.json"), store.config);
}

async function playersFor(uuids, forceNames) {
	const players = await Promise.all(uuids.map(async (uuid) => {
		const file = capePath(uuid);
		const has = existsSync(file);
		return {
			uuid,
			name: await mojangName(uuid, forceNames),
			cape: has,
			hash: has ? hashFile(file) : "",
			tag: tagFor(uuid),
			bypass: hasBypass(uuid),
			retryIn: capeRetrySec(uuid),
			note: noteFor(uuid)
		};
	}));
	await saveJson(join(DATA, "names.json"), store.names);
	await saveJson(join(DATA, "namesAt.json"), store.namesAt);
	return players;
}

const NAME_TTL_MS = 10 * 60 * 1000;

function rememberName(uuid, name) {
	const clean = String(name || "").trim();
	if (!uuid || !clean) {
		return;
	}
	store.names[uuid] = clean;
	store.namesAt[uuid] = Date.now();
}

function forgetPlayer(uuid) {
	delete store.names[uuid];
	delete store.namesAt[uuid];
	delete store.tags[uuid];
	delete store.bypass[uuid];
	delete store.capeAt[uuid];
	delete store.notes[uuid];
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

async function mojangName(uuid, force) {
	const cached = typeof store.names[uuid] === "string" ? store.names[uuid] : "";
	const at = Number(store.namesAt[uuid]) || 0;
	if (!force && cached && Date.now() - at < NAME_TTL_MS) {
		return cached;
	}
	const name = await lookupName(uuid);
	if (name) {
		rememberName(uuid, name);
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

function whitelisted(uuid) {
	return store.whitelist.includes(uuid);
}

function tagFor(uuid) {
	return sanitizeTag(store.tags[uuid]);
}

function noteFor(uuid) {
	return sanitizeNote(store.notes[uuid]);
}

function shopConfig() {
	const stored = store.config && typeof store.config === "object" ? store.config : {};
	return {
		paypal: stored.paypal || "your-paypal@email.com",
		price: stored.price || "$1",
		title: stored.title || "VOIDMARK Capes",
		blurb: stored.blurb || ""
	};
}

const MAX_TAG = 48;
const MAX_NOTE = 160;
const DAY_MS = 24 * 60 * 60 * 1000;

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

function hasBypass(uuid) {
	return Boolean(store.bypass[uuid]);
}

function capeRetryMs(uuid) {
	if (hasBypass(uuid)) {
		return 0;
	}
	const last = Number(store.capeAt[uuid]) || 0;
	if (!last) {
		return 0;
	}
	return Math.max(0, last + DAY_MS - Date.now());
}

function capeRetrySec(uuid) {
	return Math.ceil(capeRetryMs(uuid) / 1000);
}

async function touchCapeAt(uuid) {
	store.capeAt[uuid] = Date.now();
	await saveJson(join(DATA, "capeAt.json"), store.capeAt);
}

function adminHeaderOk(req) {
	return Boolean(ADMIN) && header(req, "x-admin") === ADMIN;
}

async function servePublic(res, requestPath) {
	const safe = normalize(requestPath).replace(/^(\.\.[/\\])+/, "");
	const file = join(PUBLIC, safe);
	if (!file.startsWith(PUBLIC) || !existsSync(file)) {
		json(res, 404, { error: "Not found" });
		return;
	}
	const type = MIME[extname(file).toLowerCase()] || "application/octet-stream";
	res.writeHead(200, { "Content-Type": type });
	createReadStream(file).pipe(res);
}

function capePath(uuid) {
	return join(CAPES, `${uuid}.png`);
}

function hashFile(file) {
	return createHash("sha256").update(readFileSync(file)).digest("hex").slice(0, 16);
}

function header(req, name) {
	const value = req.headers[name];
	return Array.isArray(value) ? value[0] || "" : value || "";
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

function json(res, status, body) {
	const payload = JSON.stringify(body);
	res.writeHead(status, {
		...cors(),
		"Content-Type": "application/json; charset=utf-8",
		"Cache-Control": "no-store",
		"Content-Length": Buffer.byteLength(payload)
	});
	res.end(payload);
}

function cors() {
	return {
		"Access-Control-Allow-Origin": "*",
		"Access-Control-Allow-Methods": "GET,PUT,POST,DELETE,OPTIONS",
		"Access-Control-Allow-Headers": "Content-Type, X-UUID, X-Key, X-Code, X-Token, X-Admin"
	};
}

function readBody(req, max) {
	return new Promise((resolve, reject) => {
		const chunks = [];
		let size = 0;
		req.on("data", (chunk) => {
			size += chunk.length;
			if (size > max) {
				req.destroy();
				reject(new Error("too large"));
				return;
			}
			chunks.push(chunk);
		});
		req.on("end", () => resolve(Buffer.concat(chunks)));
		req.on("error", reject);
	});
}

async function loadJson(path, fallback) {
	try {
		return JSON.parse(await readFile(path, "utf8"));
	} catch {
		return fallback;
	}
}

async function saveJson(path, value) {
	await writeFile(path, JSON.stringify(value, null, 2) + "\n");
}

server.listen(PORT, "0.0.0.0", () => {
	console.log(`Voidmark cape shop http://127.0.0.1:${PORT}`);
});
