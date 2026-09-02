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
		const players = await playersFor(env, state);
		await saveState(env, state);
		return json(200, { uuids: state.whitelist, players });
	}
	const uuid = normalizeUuid(body.uuid);
	if (!uuid) {
		return json(400, { error: "Need a valid UUID" });
	}
	if (request.method === "DELETE") {
		state.whitelist = state.whitelist.filter((id) => id !== uuid);
		if (state.names) {
			delete state.names[uuid];
		}
		await env.CAPES.delete(capeKey(uuid));
	} else if (!state.whitelist.includes(uuid)) {
		state.whitelist.push(uuid);
	}
	await saveState(env, state);
	return json(200, { ok: true, uuids: state.whitelist, players: await playersFor(env, state) });
}

async function playersFor(env, state) {
	return Promise.all(state.whitelist.map(async (uuid) => {
		const head = await env.CAPES.head(capeKey(uuid));
		return {
			uuid,
			name: await mojangName(uuid, state),
			cape: Boolean(head),
			hash: head?.customMetadata?.hash || ""
		};
	}));
}

async function mojangName(uuid, state) {
	state.names = state.names && typeof state.names === "object" ? state.names : {};
	if (state.names[uuid]) {
		return state.names[uuid];
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
			state.names[uuid] = data.name;
			return data.name;
		}
	} catch {
		return "";
	}
	return "";
}

async function loadState(env) {
	const object = await env.CAPES.get("state.json");
	if (!object) {
		return { whitelist: [], names: {} };
	}
	try {
		const parsed = JSON.parse(await object.text());
		return {
			whitelist: Array.isArray(parsed.whitelist) ? parsed.whitelist : [],
			names: parsed.names && typeof parsed.names === "object" ? parsed.names : {}
		};
	} catch {
		return { whitelist: [], names: {} };
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
		.add input { flex: 1; min-width: 180px; background: var(--card); border: 1px solid var(--line); border-radius: 8px; color: var(--text); padding: 10px 12px; font: inherit; }
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
		.nocape { color: var(--muted); font-size: 12px; text-align: center; }
		.actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
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
			<p>Whitelisted players. They set a cape in Voidmark, or you can set it here.</p>
			<label for="uuid">Add UUID</label>
			<div class="add">
				<input id="uuid" type="text" autocomplete="off" spellcheck="false" placeholder="f1b21931-667f-4be2-91bb-a06074978e0e">
				<button type="button" id="add">Add</button>
			</div>
			<div class="status" id="status"></div>
		</header>
		<p class="empty" id="empty">No UUIDs yet.</p>
		<div class="list" id="list"></div>
	</main>
	<script>
		const key = sessionStorage.getItem("voidmark-admin") || "";
		if (!key) location.replace("/");
		const status = document.getElementById("status");
		const list = document.getElementById("list");
		const empty = document.getElementById("empty");
		const uuid = document.getElementById("uuid");

		function setStatus(ok, text) {
			status.className = "status " + (ok ? "ok" : "err");
			status.textContent = text;
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
				const meta = document.createElement("div");
				meta.className = "meta";
				const name = document.createElement("div");
				name.className = "name";
				name.textContent = player.name || "Unknown";
				const id = document.createElement("div");
				id.className = "uuid";
				id.textContent = player.uuid;
				meta.append(name, id);
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
				const remove = document.createElement("button");
				remove.type = "button";
				remove.className = "warn";
				remove.textContent = "Dewhitelist";
				remove.onclick = () => send("DELETE", player.uuid);
				actions.append(file, change, remove);
				row.append(head, meta, capeBox, actions);
				list.append(row);
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
				setStatus(true, "Cape updated. Voidmark users will see it in a couple of seconds.");
			} catch (error) {
				setStatus(false, error.message);
			}
		}

		document.getElementById("add").onclick = () => {
			if (!uuid.value.trim()) { setStatus(false, "Paste a UUID"); return; }
			send("PUT", uuid.value.trim());
		};
		uuid.addEventListener("keydown", (event) => {
			if (event.key === "Enter") document.getElementById("add").click();
		});
		document.getElementById("out").onclick = () => {
			sessionStorage.removeItem("voidmark-admin");
			location.replace("/");
		};
		send("POST");
	</script>
</body>
</html>
`;
