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
		const listed = (await loadState(env)).whitelist.includes(id);
		const head = await env.CAPES.head(capeKey(id));
		if (!head) {
			return json(200, { has: false, hash: "", allowed: listed });
		}
		return json(200, { has: true, hash: head.customMetadata?.hash || "", allowed: listed });
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
	if (request.method === "GET" && env.ASSETS) {
		return env.ASSETS.fetch(request);
	}
	if (request.method === "GET" && (path === "/" || path === "/index.html" || path === "/admin.html")) {
		return page(PAGE_HTML);
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
	const body = new Uint8Array(await request.arrayBuffer());
	if (!isPng(body)) {
		return json(400, { error: "Not a PNG" });
	}
	const hash = await hashBytes(body);
	await env.CAPES.put(capeKey(uuid), body, {
		httpMetadata: { contentType: "image/png" },
		customMetadata: { hash }
	});
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
	await env.CAPES.delete(capeKey(uuid));
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
	if (request.method === "POST" && !body.uuid) {
		return json(200, { uuids: state.whitelist });
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	if (request.method === "DELETE") {
		state.whitelist = state.whitelist.filter((id) => id !== uuid);
	} else if (!state.whitelist.includes(uuid)) {
		state.whitelist.push(uuid);
	}
	await saveState(env, state);
	return json(200, { ok: true, uuids: state.whitelist });
}

async function loadState(env) {
	const object = await env.CAPES.get("state.json");
	if (!object) {
		return { whitelist: [] };
	}
	try {
		const parsed = JSON.parse(await object.text());
		return {
			whitelist: Array.isArray(parsed.whitelist) ? parsed.whitelist : []
		};
	} catch {
		return { whitelist: [] };
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
		"Access-Control-Allow-Headers": "Content-Type, X-UUID, X-Key, X-Code, X-Token"
	};
}

function page(html) {
	return new Response(html, {
		headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
	});
}

const PAGE_HTML = `<!DOCTYPE html>
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
		body { margin: 0; min-height: 100vh; font-family: "Nunito Sans", sans-serif; background: radial-gradient(1200px 600px at 50% -10%, #12324a 0%, var(--bg) 55%); color: var(--text); }
		main { width: min(520px, calc(100% - 32px)); margin: 48px auto; background: color-mix(in srgb, var(--pane) 92%, transparent); border: 1px solid var(--line); border-radius: 16px; padding: 28px 26px 24px; box-shadow: 0 24px 80px #0008; }
		h1 { margin: 0 0 6px; font-size: 22px; letter-spacing: 0.18em; }
		.rule { width: 18px; height: 2px; background: var(--accent); border-radius: 2px; margin: 10px 0 16px; }
		p, label { color: var(--muted); font-size: 14px; line-height: 1.5; }
		label { display: block; margin: 14px 0 6px; font-weight: 700; color: var(--text); font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; }
		input { width: 100%; background: var(--card); border: 1px solid var(--line); border-radius: 8px; color: var(--text); padding: 10px 12px; font: inherit; }
		.row { display: flex; gap: 8px; }
		.row input { flex: 1; }
		button { border: 0; border-radius: 8px; background: var(--accent); color: #041018; font-weight: 800; padding: 10px 14px; cursor: pointer; }
		button.ghost { background: var(--card); color: var(--text); border: 1px solid var(--line); }
		.status { min-height: 20px; margin-top: 14px; font-size: 13px; }
		.status.ok { color: var(--accent); }
		.status.err { color: var(--warn); }
		ul { list-style: none; padding: 0; margin: 16px 0 0; }
		li { display: flex; align-items: center; justify-content: space-between; gap: 8px; background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; margin-top: 8px; font-size: 13px; word-break: break-all; }
		.empty { color: var(--muted); font-size: 13px; margin-top: 16px; }
	</style>
</head>
<body>
	<main>
		<h1>VOIDMARK</h1>
		<div class="rule"></div>
		<p>After someone pays, paste their Minecraft UUID. They set the cape in Voidmark. Everyone else running the mod sees it.</p>
		<label for="admin">Admin key</label>
		<input id="admin" type="password" autocomplete="current-password" placeholder="Worker secret">
		<label for="uuid">Minecraft UUID</label>
		<div class="row">
			<input id="uuid" type="text" autocomplete="off" spellcheck="false" placeholder="f1b21931-667f-4be2-91bb-a06074978e0e">
			<button type="button" id="add">Add</button>
		</div>
		<button type="button" class="ghost" id="load" style="margin-top:12px;width:100%">Load list</button>
		<div class="status" id="status"></div>
		<ul id="list"></ul>
		<p class="empty" id="empty">No UUIDs yet.</p>
	</main>
	<script>
		const admin = document.getElementById("admin");
		const uuid = document.getElementById("uuid");
		const status = document.getElementById("status");
		const list = document.getElementById("list");
		const empty = document.getElementById("empty");
		admin.value = sessionStorage.getItem("voidmark-admin") || "";

		function setStatus(ok, text) {
			status.className = "status " + (ok ? "ok" : "err");
			status.textContent = text;
		}

		function draw(uuids) {
			list.innerHTML = "";
			empty.style.display = uuids.length ? "none" : "block";
			for (const id of uuids) {
				const item = document.createElement("li");
				const label = document.createElement("span");
				label.textContent = id;
				const remove = document.createElement("button");
				remove.type = "button";
				remove.className = "ghost";
				remove.textContent = "Remove";
				remove.onclick = () => send("DELETE", id);
				item.append(label, remove);
				list.append(item);
			}
		}

		async function send(method, id) {
			const key = admin.value.trim();
			sessionStorage.setItem("voidmark-admin", key);
			try {
				const response = await fetch("/api/whitelist", {
					method,
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ admin: key, uuid: id || undefined })
				});
				const data = await response.json();
				if (!response.ok) throw new Error(data.error || "Failed");
				draw(data.uuids || []);
				setStatus(true, method === "DELETE" ? "Removed." : (id ? "Whitelisted. They can set a cape in Voidmark." : "Loaded."));
				if (id && method !== "DELETE") uuid.value = "";
			} catch (error) {
				setStatus(false, error.message);
			}
		}

		document.getElementById("load").onclick = () => send("POST");
		document.getElementById("add").onclick = () => {
			if (!uuid.value.trim()) { setStatus(false, "Paste a UUID"); return; }
			send("PUT", uuid.value.trim());
		};
	</script>
</body>
</html>
`;
