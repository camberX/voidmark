import { createHash, randomBytes } from "node:crypto";
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
	codes: await loadJson(join(DATA, "codes.json"), []),
	tokens: await loadJson(join(DATA, "tokens.json"), {}),
	config: await loadJson(join(DATA, "config.json"), {
		paypal: "your-paypal@email.com",
		price: "$1",
		title: "VOIDMARK Capes"
	})
};

if (store.codes.length === 0) {
	store.codes.push("local-dev");
	await saveJson(join(DATA, "codes.json"), store.codes);
	console.log("Starter upload code: local-dev");
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
			json(res, 200, { has: false, hash: "" });
			return;
		}
		json(res, 200, { has: true, hash: hashFile(file) });
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
		await handleGrant(req, res);
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
	const key = (header(req, "x-key") || header(req, "x-code") || header(req, "x-token")).trim();
	if (!uuid) {
		json(res, 400, { error: "Need a valid UUID" });
		return;
	}
	if (!key) {
		json(res, 401, { error: "Need an upload code or shop token" });
		return;
	}
	const body = await readBody(req, MAX_BYTES);
	if (!isPng(body)) {
		json(res, 400, { error: "Not a PNG" });
		return;
	}
	const auth = authorize(uuid, key);
	if (!auth.ok) {
		json(res, auth.status, { error: auth.error });
		return;
	}
	await writeFile(capePath(uuid), body);
	await saveJson(join(DATA, "codes.json"), store.codes);
	await saveJson(join(DATA, "tokens.json"), store.tokens);
	json(res, 200, { ok: true, token: auth.token, uuid });
}

async function handleDelete(req, res) {
	const uuid = normalizeUuid(header(req, "x-uuid"));
	const key = (header(req, "x-key") || header(req, "x-token")).trim();
	if (!uuid || !key) {
		json(res, 400, { error: "Need UUID and token" });
		return;
	}
	const auth = authorize(uuid, key, false);
	if (!auth.ok) {
		json(res, auth.status, { error: auth.error });
		return;
	}
	const file = capePath(uuid);
	if (existsSync(file)) {
		const { unlink } = await import("node:fs/promises");
		await unlink(file);
	}
	json(res, 200, { ok: true });
}

async function handleGrant(req, res) {
	const body = JSON.parse((await readBody(req, 4096)).toString("utf8") || "{}");
	if ((body.admin || "") !== ADMIN) {
		json(res, 403, { error: "Bad admin key" });
		return;
	}
	const code = randomBytes(4).toString("hex");
	store.codes.push(code);
	await saveJson(join(DATA, "codes.json"), store.codes);
	json(res, 200, { code });
}

function authorize(uuid, key, allowCode = true) {
	if (store.tokens[uuid] && store.tokens[uuid] === key) {
		return { ok: true, token: key };
	}
	if (allowCode) {
		const index = store.codes.indexOf(key);
		if (index >= 0) {
			store.codes.splice(index, 1);
			const token = store.tokens[uuid] || randomBytes(16).toString("hex");
			store.tokens[uuid] = token;
			return { ok: true, token };
		}
	}
	if (store.tokens[uuid]) {
		return { ok: false, status: 403, error: "Wrong token for this UUID" };
	}
	return { ok: false, status: 401, error: "Unknown code. Pay first, then use the code you were sent." };
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
		"Access-Control-Allow-Headers": "Content-Type, X-UUID, X-Key, X-Code, X-Token"
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
	console.log(`Admin key: ${ADMIN}`);
});
