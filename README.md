# SPECTER LOCATE

SPECTER LOCATE is a consent-first two-application Android device location system.

## Applications

- **SPECTER LOCATE USER** — installed on the device being shared. It verifies the device owner's phone number, registers the device, shows pairing state, and runs an explicit foreground location-sharing service.
- **SPECTER LOCATE ADMIN** — installed on the administrator device. It verifies the administrator phone number, creates short-lived pairing codes, lists paired devices, shows online/offline state, battery, Android/app information, last-seen data, current coordinates and location history, and can revoke a pairing.

## Backend

Node.js + TypeScript + Express + PostgreSQL + JWT + SMTP email OTP.

The backend is intentionally required for production pairing and realtime telemetry. HTTPS is required in production.

Environment variables are documented in `server/.env.example`.

Authentication uses email OTP sent through standard SMTP. Gmail SMTP can be used at no cost with a Gmail App Password. No Twilio account or paid SMS service is required.

## Database

Run `server/schema.sql` against PostgreSQL.

The schema stores only device-management telemetry required by the application: account identifiers, device metadata, pairing state, heartbeat state and authorized location points.

## Android configuration

The Gradle property `SPECTER_API_URL` sets the HTTPS backend base URL. GitHub Actions can receive it through a workflow-dispatch input or the repository variable `SPECTER_API_URL`.

Without an API URL, the apps still build but show that the backend URL is not configured.

## Build from GitHub Actions

The workflow builds:

- `specter-locate-user-debug-apk`
- `specter-locate-admin-debug-apk`
- the backend TypeScript project

The Android apps use Java 17 and the repository's Gradle wrapper configuration.

## Security model

Phone verification is performed server-side. Pairing codes are random, hashed before storage, short-lived and single-use. Admin device reads are scoped to active pairings. User location uploads are scoped to the authenticated device owner. Pairings can be revoked by the admin. Location tracking uses Android's visible foreground-service notification and requires runtime location permission.

The application does not attempt to bypass Android permissions or collect messages, contacts, camera, microphone, files or other unrelated private content.