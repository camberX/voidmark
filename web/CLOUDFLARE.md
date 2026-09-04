# Host the cape shop on Cloudflare (free)

This puts the UUID list and cape files on Cloudflare Workers + R2. You are not running a VPS. The free tier is enough for Voidmark: 10 GB of PNGs, no bandwidth bill.

**R2 asks for a card even on the free plan.** That is verification, not a charge. Stay under the free limits and the bill is $0. After you add a card, set a spending cap (step 9 in the CLI path).

The admin key never goes in the HTML. It is a Worker **secret**.

## Windows: dashboard only (no Git, no WSL)

Origin has no Download ZIP. Do not type your Google password into Git.

1. Cloudflare → **Workers & Pages** (Compute) → **Create** → start from a Hello World Worker.
2. Name it `voidmark-capes`. Deploy once so it exists.
3. **Edit code**. Delete the sample. On [the Voidmark codebase](https://cursor.com/codebase/shora/voidmark) open `web/worker.js`, copy the whole file, paste it into the Worker editor. **Deploy**.
4. Worker **Settings** → **Bindings** → **R2** → Add. Variable name must be `CAPES`. Bucket: `voidmark-capes`. Save.
5. Worker **Settings** → **Variables and Secrets**:
   - `ADMIN` → Encrypt / Secret. Paste a long random string and save it in a password manager.
6. Deploy again if it asks.
7. Open the Worker URL (`https://voidmark.cloud` or the `workers.dev` URL). The public shop is `/`, with a download of the latest jar at `/download`. Admin login is `/admin`. After the key is accepted, the Worker sets an HttpOnly cookie and then serves the cape desk at `/manage`. `/manage` is not sent at all without that cookie, and every desk API (list, whitelist, tags, capes, bans) also requires that cookie plus the admin key — hiding the page in the browser is not the lock. That desk is players, bulk add, notes, cooldown reset, cape upload, and fake ban.
8. The shipped mod always uses `https://voidmark.cloud`. Attach that custom domain to this Worker (Workers & Pages → `voidmark-capes` → Settings → Domains & Routes). Restart Minecraft after a domain change.

## 0. What you need (CLI path)

- A Cloudflare account ([dash.cloudflare.com](https://dash.cloudflare.com/sign-up))
- Node.js 20+ on your computer ([nodejs.org](https://nodejs.org/)) so you can run `wrangler`
- A payment method Cloudflare will accept (card; PayPal sometimes works)
- This repo, so you have the `web/` folder

## 1. Create the Cloudflare account

1. Sign up and confirm your email.
2. If it asks you to add a website, you can skip that. A `workers.dev` URL is enough.

## 2. Turn on R2 and make a bucket

1. Left sidebar → **R2 Object Storage**.
2. If it asks to add a payment method, add one. You are not buying a paid plan.
3. **Create bucket**.
4. Name it exactly `voidmark-capes`.
5. Leave location as Automatic. Create.

## 3. Log the deploy tool into Cloudflare

On your computer, in a terminal:

```bash
cd web
npm install
npx wrangler login
```

A browser window opens. Approve it.

## 4. Put the admin key in a secret (not in the page)

Still in `web/`:

```bash
npx wrangler secret put ADMIN
```

When it asks for the value, paste a long random string. Generate one with:

```bash
openssl rand -hex 16
```

Save that string in a password manager. That is what you type on `/admin`. It is not in the HTML or in git.

## 5. Deploy

```bash
npx wrangler deploy
```

Wrangler prints a URL like:

```
https://voidmark-capes.YOURNAME.workers.dev
```

Open it. You should see the cape landing page (message @evilkitten911 on Discord).

If deploy fails with a bucket error, the bucket name in the dashboard does not match `voidmark-capes`. Rename it or change `bucket_name` in `wrangler.toml` to match.

## 6. Point Voidmark at that URL

The jar is hardcoded to `https://voidmark.cloud`. There is no `capeServerUrl` in `.minecraft/config/voidmark.json`. Launch drops that key if an older config still has it.

Attach `voidmark.cloud` to the Worker (Settings → Domains & Routes). The `workers.dev` URL still works in a browser for the admin list if you want it.

## 7. After someone messages on Discord

1. They message **@evilkitten911** with their Minecraft name.
2. Open `https://voidmark.cloud/admin` (or your Worker `/admin`), enter the admin key, and you land on the cape desk. Visiting `/manage` without logging in redirects to the login page and does not include the desk HTML.
3. Type their username or UUID and click **Add**, or use Bulk add. You should see their current name, skin, and cape.
4. Click a player to change cape, head tag, note, bypass, or reset the 24 hour cooldown. **Dewhitelist** drops them.

Capes only show for Voidmark users.

## 8. Check it worked

```bash
curl https://voidmark-capes.YOURNAME.workers.dev/api/config
```

You should see a title and the Discord handle. After they set a cape in-game:

```bash
curl https://voidmark-capes.YOURNAME.workers.dev/api/cape/THEIR-UUID
```

should return `"has":true` and a hash. Changing the cape in the Voidmark menu overwrites that file; other clients pick it up the next time they join a world.

## 9. Cap the bill (do this once)

1. Cloudflare dashboard → **Billing** (or account **Manage Account** → **Billing**).
2. Add a budget / spending notification, ideally **$5**.
3. You will not hit this unless something is very wrong. Cape PNGs are tiny.

## Optional: custom domain

The jar always uses `https://voidmark.cloud`, so that hostname must be on the Worker:

1. Workers & Pages → `voidmark-capes` → **Settings** → **Domains & Routes** → **Add**.
2. Add `voidmark.cloud`.

## Updating later

The shop download does **not** need a Worker deploy for each new jar. `./gradlew build` writes `web/public/mod/latest.json` and the jar; `git push` to [camberX/voidmark](https://github.com/camberX/voidmark) is enough. The Worker fetches that on `/download` and `/api/mod`.

After you change `web/worker.js`, the cape desk HTML, or `wrangler.toml` (Worker code, not the jar):

```bash
cd web
npx wrangler deploy
```

Change `MOD_GITHUB` in `wrangler.toml` if the jar lives in a different public repo. Changing the `ADMIN` secret is another `npx wrangler secret put ADMIN`. The UUID list and cape PNGs stay in the R2 bucket.

## Local testing (not Cloudflare)

```bash
node web/server.mjs
```

That still uses `http://127.0.0.1:43150` and files under `web/data/`. The Worker is only for the public host.
