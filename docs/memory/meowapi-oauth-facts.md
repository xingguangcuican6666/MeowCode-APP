---
name: meowapi-oauth-facts
description: "MeowAPI (new-api) OAuth 2.0 server — endpoints, the meowcode-app client, scopes, token lifecycle"
metadata:
  node_type: memory
  type: reference
  originSessionId: bf74a85a-8a67-45f1-8c8e-f4f2337f69ca
  modified: 2026-11-26T05:01:41.781Z
---

MeowCode logs into **MeowAPI** (the user's new-api instance) via its built-in OAuth 2.0 /
OIDC authorization server. Canonical client implementation:
`MeowCode/src/lib/oauth.ts`.

Facts (verified against `new-api-ach` source + `oauth-server-docs/`):
- Endpoints under the issuer: `{issuer}/oauth2/authorize`, `/oauth2/token`,
  `/oauth2/userinfo`, `/oauth2/revoke`, discovery `/.well-known/openid-configuration`.
- Public client + PKCE **S256 only**; token request carries only `client_id`, `code`,
  `redirect_uri`, `code_verifier`.
- `redirect_uri` must match a registered value **exactly** (`RedirectUriRegistered`).
  The server accepts any absolute scheme (only `http` is loopback-restricted), so a
  custom scheme works once registered.
- **meowcode-app client (registered 2026-11-26 by the user as root):**
  `cli_44cf45ec8ef543e49edede2e329836fe`, public, redirect
  `icu.xingguangcuican.meowcode://oauth/callback` (app-owned scheme per RFC 8252 §7.1,
  not a generic `meowcode://` that any app could claim). Granted scopes: openid,
  profile, email, groups, wallet.read, apikeys.manage, models.read, models.invoke.
- CLI client id `cli_58b66dae26024151a27e1a0685cb6463` (loopback `127.0.0.1:8788`) is
  separate; do not reuse it in the app.
- Scoped business API (bearer = the `at_` token directly): `/oauth2/userinfo` for
  identity, `/oauth2/api/wallet` (`{success,message,data:{quota,used_quota}}`,
  QuotaPerUnit = 500000 per ￥1), `/oauth2/api/models|groups|pricing`,
  `/oauth2/api/tokens` (apikeys.manage). `/v1/messages` accepts the `at_` token.
- Grants supported: `authorization_code` and `refresh_token` only — **no device-code
  flow** in new-api, so the native app must use the browser redirect.
- Access token ~1h; refresh tokens **rotate** — replaying an old one revokes the family.
  Single-flight refresh, persist the rotated token inside the lock.
- Base URL `https://api.xingguangcuican.icu`. That host sits behind **Cloudflare**, so
  unstubbed server probes return 403 "Just a moment" — not an OAuth error.

**Why:** the decision "how does an Android app receive the callback" is now closed.
**How to apply:** keep the redirect URI in `OAuthConfig` in sync with the dashboard
registration; never request `apikeys.manage` from the phone UI.
