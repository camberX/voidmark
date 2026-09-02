# Host the cape shop on Cloudflare (free)

This puts the UUID list and cape files on Cloudflare Workers + R2. You are not running a VPS. The free tier is enough for Voidmark: 10 GB of PNGs, no bandwidth bill.

**R2 asks for a card even on the free plan.** That is verification, not a charge. Stay under the free limits and the bill is $0. After you add a card, set a spending cap (step 10 in the CLI path).

The admin key never goes in the HTML. It is a Worker **secret**.

## Windows: dashboard only (no Git, no WSL)

Origin has no Download ZIP. Do not type your Google password into Git.

1. Cloudflare → **Workers & Pages** (Compute) → **Create** → start from a Hello World Worker.
2. Name it `voidmark-capes`. Deploy once so it exists.
3. **Edit code**. Delete the sample. On [the Voidmark codebase](https://cursor.com/codebase/shora/voidmark) open `web/worker.js`, copy the whole file, paste it into the Worker editor. **Deploy**.
4. Worker **Settings** → **Bindings** → **R2** → Add. Variable name must be `CAPES`. Bucket: `voidmark-capes`. Save.
5. Worker **Settings** → **Variables and Secrets**:
   - `ADMIN` → Encrypt / Secret. Paste a long random string and save it in a password manager.
   - `PAYPAL` → your Friends and Family email (plain text).
   - `PRICE` → `$1` (plain text).
6. Deploy again if it asks.
7. Open the Worker URL (`https://voidmark-capes.…workers.dev`). Enter the admin key. That opens the list: names, heads, capes, head tags, change cape, and dewhitelist.
8. The shipped mod already uses `https://voidmark-capes.inputm4.workers.dev`. Only change `capeServerUrl` in `.minecraft/config/voidmark.json` if you deployed a different Worker. No trailing slash, no `/manage.html`. Restart Minecraft.

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

Save that string in a password manager. That is what you type on `/admin.html`. It is not in the HTML or in git.

## 5. Put your PayPal address on the public page

Edit `web/wrangler.toml` and change:

```toml
PAYPAL = "your-paypal@email.com"
PRICE = "$1"
```

to your Friends and Family email and price.

## 6. Deploy

```bash
npx wrangler deploy
```

Wrangler prints a URL like:

```
https://voidmark-capes.YOURNAME.workers.dev
```

Open it. You should see the UUID whitelist page.

If deploy fails with a bucket error, the bucket name in the dashboard does not match `voidmark-capes`. Rename it or change `bucket_name` in `wrangler.toml` to match.

## 7. Point Voidmark at that URL

On every PC that should see shop capes, edit `.minecraft/config/voidmark.json`:

```json
"capeServerUrl": "https://voidmark-capes.YOURNAME.workers.dev"
```

No trailing slash. Restart Minecraft (or reopen the world) so the mod reloads config.

If you ship a jar to other people, they all need this same public URL. Localhost will only work on your machine.

## 8. After someone pays

1. They send $1 Friends and Family with their Minecraft name.
2. Look up their UUID (NameMC, etc.).
3. Open `https://voidmark-capes.YOURNAME.workers.dev`, enter the admin key, and you land on the list.
4. Paste their UUID and click **Add**. You should see their name, head, and cape.
5. **Dewhitelist** drops them. **Change cape** uploads a PNG for them.

Friends and Family has no PayPal purchase protection. Capes only show for Voidmark users.

## 9. Check it worked

```bash
curl https://voidmark-capes.YOURNAME.workers.dev/api/config
```

You should see your PayPal and price. After they set a cape in-game:

```bash
curl https://voidmark-capes.YOURNAME.workers.dev/api/cape/THEIR-UUID
```

should return `"has":true` and a hash. Changing the cape in the Voidmark menu overwrites that file; other clients pick up the new hash within a couple of seconds.

## 10. Cap the bill (do this once)

1. Cloudflare dashboard → **Billing** (or account **Manage Account** → **Billing**).
2. Add a budget / spending notification, ideally **$5**.
3. You will not hit this unless something is very wrong. Cape PNGs are tiny.

## Optional: custom domain

If you already have a domain on Cloudflare:

1. Workers & Pages → `voidmark-capes` → **Settings** → **Domains & Routes** → **Add**.
2. Use something like `capes.yourdomain.com`.
3. Put that `https://…` URL in `capeServerUrl` instead of `workers.dev`.

## Updating later

After you change `web/worker.js`, `web/public/`, or `wrangler.toml`:

```bash
cd web
npx wrangler deploy
```

Changing the `ADMIN` secret is another `npx wrangler secret put ADMIN`. The UUID list and cape PNGs stay in the R2 bucket.

## Local testing (not Cloudflare)

```bash
node web/server.mjs
```

That still uses `http://127.0.0.1:43150` and files under `web/data/`. The Worker is only for the public host.
