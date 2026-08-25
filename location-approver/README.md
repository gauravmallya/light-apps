# 📍 Requests

A tiny approver tool for a self-hosted, friend-approved live location sharing setup. Someone visits a link, types their name, and requests access. You get to see pending requests here and approve or deny them with one tap. Once approved, they can see your live location on a simple map page — no Google account, no third-party location service, and no data leaving infrastructure you control.

This tool is intentionally minimal: it does not host or run any location tracking itself. It's a thin approval UI over a small self-hosted backend (see [Backend setup](#-backend-setup) below) that you run yourself.

## 🧩 How the whole system fits together

```
[Your phone: GPS] --(OwnTracks app, HTTP)--> [Your server: OwnTracks Recorder]
                                                       ^
                                                       | reads latest location
                                                       |
[Friend's browser] --(name + request)--> [Your server: location-gate API] <--(approve/deny)-- [This app]
                                                       |
                                                       v
                                          [Friend's browser: live map, once approved]
```

- **OwnTracks** (client app + [Recorder](https://owntracks.org/booklet/clients/recorder/)) handles the actual GPS reporting and storage.
- **`location-gate`** is a small self-hosted Flask API (not included in this repo) that gatekeeps access: it takes a name, creates a pending request, and only serves location data to tokens you've explicitly approved.
- **This tool** (`Requests`) is the approval UI for that gate, meant to live on your Light Phone so you can approve/deny from wherever you are.
- A plain static HTML page (served by your `location-gate` backend) is what friends actually open in their browser — it handles the name prompt, polls for approval, and renders a live map once approved.

None of this requires Google Play Services, a Google account, or any proprietary location-sharing product — it's designed to run entirely on hardware you control (e.g. a Raspberry Pi), reachable over something like [Tailscale](https://tailscale.com) so it doesn't need to be exposed to the open internet.

## 🔑 Configuration

This tool needs to know where your `location-gate` backend lives and an admin token to authenticate with it. Both are personal/secret and must never be committed.

Add them to `local.properties` (gitignored) in the repo root:

```properties
locationApproverBaseUrl=https://your-backend-host
locationApproverAdminToken=YOUR_ADMIN_TOKEN
```

(Alternatively, set the `LOCATION_APPROVER_BASE_URL` and `LOCATION_APPROVER_ADMIN_TOKEN` environment variables.)

If unset, the app builds fine but every request will fail — there's no hardcoded fallback, on purpose.

## 🖥️ Backend setup

`location-gate` itself isn't part of this repo (it's a small, generic Flask app, not a Light Phone tool), but here's the shape of what you need to run somewhere you control:

1. **A small always-on host** — a Raspberry Pi, an old laptop, or a cheap VPS all work. Docker is the easiest way to run everything below.
2. **[OwnTracks Recorder](https://owntracks.org/booklet/clients/recorder/)** in HTTP mode, to receive and store your live location pings.
3. **A gatekeeper API** with a few endpoints:
   - `POST /api/request-access` — takes `{ "name": "..." }`, creates a pending request (or returns an existing approved token if that name was already approved before).
   - `POST /api/check-status` — lets a waiting visitor poll for approval, or a returning visitor's saved token get re-verified.
   - `GET /api/location?token=...` — returns the latest location, only for approved tokens.
   - `GET /api/admin/pending`, `POST /api/admin/decide`, `GET /api/admin/all`, `POST /api/admin/revoke` — the admin surface this tool talks to (protected by a bearer token).
   - `GET /` — a static page for visitors: prompts for a name, polls for approval, then shows a live Leaflet/OpenStreetMap view once approved. Saves an access token client-side so returning visitors just need to re-enter their name to be recognized automatically.
4. **Tailscale** (or similar) on that host so it's reachable securely without exposing your home network — [Funnel](https://tailscale.com/kb/1223/funnel) is a good option for making just the gate API reachable over normal HTTPS without any port forwarding.

Once that's running, point this tool's `locationApproverBaseUrl` at it and rebuild.

## ▶️ Running it

Open this repo in Android Studio and run the `:location-approver` module on an emulator, [the LightOS emulator](../docs/system_app), or sideload the built APK onto a real Light Phone III.
