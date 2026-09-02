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
		const head = await env.CAPES.head(capeKey(id));
		if (!head) {
			return json(200, { has: false, hash: "" });
		}
		return json(200, { has: true, hash: head.customMetadata?.hash || "" });
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
	if (request.method === "POST" && path === "/api/grant") {
		return handleGrant(request, env);
	}
	if (request.method === "GET" && env.ASSETS) {
		return env.ASSETS.fetch(request);
	}
	return json(404, { error: "Not found" });
}

async function handlePublish(request, env) {
	const uuid = normalizeUuid(request.headers.get("x-uuid"));
	const key = (request.headers.get("x-key") || request.headers.get("x-code") || request.headers.get("x-token") || "").trim();
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	if (!key) {
		return json(401, { error: "Need an upload code or shop token" });
	}
	const body = new Uint8Array(await request.arrayBuffer());
	if (!isPng(body)) {
		return json(400, { error: "Not a PNG" });
	}
	const state = await loadState(env);
	const auth = authorize(state, uuid, key, true);
	if (!auth.ok) {
		return json(auth.status, { error: auth.error });
	}
	const hash = await hashBytes(body);
	await env.CAPES.put(capeKey(uuid), body, {
		httpMetadata: { contentType: "image/png" },
		customMetadata: { hash }
	});
	await saveState(env, state);
	return json(200, { ok: true, token: auth.token, uuid });
}

async function handleDelete(request, env) {
	const uuid = normalizeUuid(request.headers.get("x-uuid"));
	const key = (request.headers.get("x-key") || request.headers.get("x-token") || "").trim();
	if (!uuid || !key) {
		return json(400, { error: "Need UUID and token" });
	}
	const state = await loadState(env);
	const auth = authorize(state, uuid, key, false);
	if (!auth.ok) {
		return json(auth.status, { error: auth.error });
	}
	await env.CAPES.delete(capeKey(uuid));
	return json(200, { ok: true });
}

async function handleGrant(request, env) {
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
	const code = hexBytes(4);
	state.codes.push(code);
	await saveState(env, state);
	return json(200, { code });
}

function authorize(state, uuid, key, allowCode) {
	if (state.tokens[uuid] && state.tokens[uuid] === key) {
		return { ok: true, token: key };
	}
	if (allowCode) {
		const index = state.codes.indexOf(key);
		if (index >= 0) {
			state.codes.splice(index, 1);
			const token = state.tokens[uuid] || hexBytes(16);
			state.tokens[uuid] = token;
			return { ok: true, token };
		}
	}
	if (state.tokens[uuid]) {
		return { ok: false, status: 403, error: "Wrong token for this UUID" };
	}
	return { ok: false, status: 401, error: "Unknown code. Pay first, then use the code you were sent." };
}

async function loadState(env) {
	const object = await env.CAPES.get("state.json");
	if (!object) {
		return { codes: [], tokens: {} };
	}
	try {
		const parsed = JSON.parse(await object.text());
		return {
			codes: Array.isArray(parsed.codes) ? parsed.codes : [],
			tokens: parsed.tokens && typeof parsed.tokens === "object" ? parsed.tokens : {}
		};
	} catch {
		return { codes: [], tokens: {} };
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

function hexBytes(n) {
	const bytes = new Uint8Array(n);
	crypto.getRandomValues(bytes);
	return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
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
		"Access-Control-Allow-Headers": "Content-Type, X-UUID, X-Key, X-Code, X-Token"
	};
}
