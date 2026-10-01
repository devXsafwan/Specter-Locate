# SPECTER LOCATE

Consent-first Android location sharing and local location history.

## Android
Kotlin, Jetpack Compose, Material 3, Room, Google Fused Location Provider, foreground location service and runtime permissions.

Tracking starts only after explicit user action. Android's foreground-service notification remains visible while tracking is active. The app does not bypass permissions, hide tracking, or fabricate locations.

## Backend
Node.js + TypeScript + Express + PostgreSQL + JWT.

Run:

```bash
cd server
npm install
npm run build
npm start
```

Environment:

```env
DATABASE_URL=postgresql://user:password@host:5432/specter_locate
JWT_SECRET=use-a-long-random-secret
PORT=3000
```

Initialize PostgreSQL with `server/schema.sql`.

## Repository structure

- `app/` — Android client
- `server/` — authenticated API
- `Specter-Locate-complete.zip` — packaged project snapshot

The current implementation establishes the core location collector, persistent local history, authenticated backend ingestion, validation and per-user storage. Production deployment should additionally configure TLS, real identity/device registration, token refresh/rotation, retention policies and monitoring.
