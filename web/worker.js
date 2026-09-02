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
	if (request.method === "GET" && (path === "/" || path === "/index.html")) {
		return page(INDEX_HTML);
	}
	if (request.method === "GET" && path === "/admin.html") {
		return page(ADMIN_HTML);
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

function page(html) {
	return new Response(html, {
		headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
	});
}

const INDEX_HTML = `<!DOCTYPE html>
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
		main { width: min(520px, calc(100% - 32px)); margin: 48px auto; background: color-mix(in srgb, var(--pane) 92%, transparent); border: 1px solid var(--line); border-radius: 16px; padding: 28px 26px 24px; box-shadow: 0 24px 80px #0008; }
		h1 { margin: 0 0 6px; font-size: 22px; letter-spacing: 0.18em; }
		.rule { width: 18px; height: 2px; background: var(--accent); border-radius: 2px; margin: 10px 0 16px; }
		p, label { color: var(--muted); font-size: 14px; line-height: 1.5; }
		ol { color: var(--muted); font-size: 14px; padding-left: 18px; }
		label { display: block; margin: 14px 0 6px; font-weight: 700; color: var(--text); font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; }
		input[type=text], input[type=file] { width: 100%; background: var(--card); border: 1px solid var(--line); border-radius: 8px; color: var(--text); padding: 10px 12px; font: inherit; }
		input[type=file] { padding: 8px; }
		button { margin-top: 18px; width: 100%; border: 0; border-radius: 8px; background: var(--accent); color: #041018; font-weight: 800; padding: 12px; cursor: pointer; }
		button:disabled { opacity: 0.5; cursor: default; }
		.status { min-height: 20px; margin-top: 14px; font-size: 13px; }
		.status.ok { color: var(--accent); }
		.status.err { color: var(--warn); }
		.token { word-break: break-all; background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; margin-top: 8px; font-size: 13px; }
		.preview { display: none; margin-top: 12px; width: 100%; max-height: 180px; object-fit: contain; background: #000; border-radius: 8px; }
		.foot { margin-top: 22px; font-size: 12px; color: var(--muted); }
		a { color: var(--accent); }
	</style>
</head>
<body>
	<main>
		<h1>VOIDMARK</h1>
		<div class="rule"></div>
		<p id="lead">Custom cape. Pay via PayPal Friends and Family, get an upload code, then drop your PNG here. Voidmark players see it in-game. Change it later from the Voidmark Cape menu and everyone updates.</p>
		<ol>
			<li>Send <strong id="price">$1</strong> Friends and Family to <strong id="paypal">your-paypal@email.com</strong> with your Minecraft name.</li>
			<li>You get an upload code back.</li>
			<li>Paste your UUID, the code, and a PNG.</li>
		</ol>
		<form id="form">
			<label for="uuid">Minecraft UUID</label>
			<input id="uuid" type="text" autocomplete="off" spellcheck="false" placeholder="f1b21931-667f-4be2-91bb-a06074978e0e" required>
			<label for="code">Upload code</label>
			<input id="code" type="text" autocomplete="off" spellcheck="false" placeholder="code from after payment" required>
			<label for="file">Cape PNG</label>
			<input id="file" type="file" accept="image/png,.png" required>
			<img id="preview" class="preview" alt="Cape preview">
			<button type="submit" id="go">Upload cape</button>
		</form>
		<div class="status" id="status"></div>
		<div class="token" id="token" hidden></div>
		<p class="foot">Keep the shop token. Paste it in Voidmark → Player → Cape if you want to change the cape in-game. Friends and Family has no PayPal purchase protection. Capes only show for Voidmark users. <a href="/admin.html">Admin</a></p>
	</main>
	<script>
		const preview = document.getElementById("preview");
		document.getElementById("file").addEventListener("change", (event) => {
			const file = event.target.files[0];
			if (!file) { preview.style.display = "none"; return; }
			preview.src = URL.createObjectURL(file);
			preview.style.display = "block";
		});
		fetch("/api/config").then((r) => r.json()).then((cfg) => {
			document.getElementById("paypal").textContent = cfg.paypal;
			document.getElementById("price").textContent = cfg.price;
		}).catch(() => {});
		document.getElementById("form").addEventListener("submit", async (event) => {
			event.preventDefault();
			const status = document.getElementById("status");
			const token = document.getElementById("token");
			const go = document.getElementById("go");
			const file = document.getElementById("file").files[0];
			status.className = "status";
			status.textContent = "Uploading…";
			token.hidden = true;
			go.disabled = true;
			try {
				const response = await fetch("/api/cape", {
					method: "PUT",
					headers: {
						"X-UUID": document.getElementById("uuid").value.trim(),
						"X-Key": document.getElementById("code").value.trim()
					},
					body: file
				});
				const data = await response.json();
				if (!response.ok) throw new Error(data.error || "Upload failed");
				status.className = "status ok";
				status.textContent = "Cape is live. Other Voidmark users will see it within a few seconds.";
				token.hidden = false;
				token.textContent = "Shop token (paste in Voidmark Cape menu to change it in-game): " + data.token;
			} catch (error) {
				status.className = "status err";
				status.textContent = error.message;
			} finally {
				go.disabled = false;
			}
		});
	</script>
</body>
</html>
`;

const ADMIN_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>VOIDMARK Cape admin</title>
	<style>
		body { font-family: sans-serif; background: #05070d; color: #e8edf5; max-width: 420px; margin: 48px auto; }
		input, button { width: 100%; padding: 10px; margin: 8px 0; border-radius: 8px; border: 1px solid #1c2230; background: #12151c; color: inherit; }
		button { background: #2fb5ff; color: #041018; font-weight: 700; border: 0; cursor: pointer; }
		.out { margin-top: 12px; word-break: break-all; }
	</style>
</head>
<body>
	<h1>Grant upload code</h1>
	<p>After a Friends and Family payment, generate a one-time code and send it to them.</p>
	<input id="admin" type="password" placeholder="Admin key">
	<button id="go">New code</button>
	<div class="out" id="out"></div>
	<script>
		document.getElementById("go").onclick = async () => {
			const out = document.getElementById("out");
			const response = await fetch("/api/grant", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ admin: document.getElementById("admin").value })
			});
			const data = await response.json();
			out.textContent = response.ok ? data.code : (data.error || "Failed");
		};
	</script>
</body>
</html>
`;

