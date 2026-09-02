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
	config: await loadJson(join(DATA, "config.json"), {
		paypal: "your-paypal@email.com",
		price: "$1",
		title: "VOIDMARK Capes"
	})
};

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
		json(res, 200, { paypal: store.config.paypal, price: store.config.price, title: store.config.title });
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
		json(res, 200, { has: false, hash: "", allowed: whitelisted(id) });
		return;
	}
	json(res, 200, { has: true, hash: hashFile(file), allowed: whitelisted(id) });
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
	const body = await readBody(req, MAX_BYTES);
	if (!isPng(body)) {
		json(res, 400, { error: "Not a PNG" });
		return;
	}
	await writeFile(capePath(uuid), body);
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
	const file = capePath(uuid);
	if (existsSync(file)) {
		const { unlink } = await import("node:fs/promises");
		await unlink(file);
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
	if (req.method === "POST" && !body.uuid) {
		json(res, 200, { uuids: store.whitelist, players: await playersFor(store.whitelist) });
		return;
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		json(res, 400, { error: "Need a valid UUID" });
		return;
	}
	if (req.method === "DELETE") {
		store.whitelist = store.whitelist.filter((id) => id !== uuid);
		delete store.names[uuid];
		const file = capePath(uuid);
		if (existsSync(file)) {
			const { unlink } = await import("node:fs/promises");
			await unlink(file);
		}
	} else if (!store.whitelist.includes(uuid)) {
		store.whitelist.push(uuid);
	}
	await saveJson(join(DATA, "whitelist.json"), store.whitelist);
	await saveJson(join(DATA, "names.json"), store.names);
	json(res, 200, { ok: true, uuids: store.whitelist, players: await playersFor(store.whitelist) });
}

async function playersFor(uuids) {
	return Promise.all(uuids.map(async (uuid) => {
		const file = capePath(uuid);
		const has = existsSync(file);
		return {
			uuid,
			name: await mojangName(uuid),
			cape: has,
			hash: has ? hashFile(file) : ""
		};
	}));
}

async function mojangName(uuid) {
	if (store.names[uuid]) {
		return store.names[uuid];
	}
	try {
		const response = await fetch("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replaceAll("-", ""), {
			signal: AbortSignal.timeout(5000)
		});
		if (!response.ok) {
			return "";
		}
		const data = await response.json();
		if (data.name) {
			store.names[uuid] = data.name;
			await saveJson(join(DATA, "names.json"), store.names);
			return data.name;
		}
	} catch {
		return "";
	}
	return "";
}

function whitelisted(uuid) {
	return store.whitelist.includes(uuid);
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
