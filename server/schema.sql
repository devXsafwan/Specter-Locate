CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 phone TEXT UNIQUE,
 email TEXT UNIQUE,
 role TEXT NOT NULL CHECK(role IN ('user','admin')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS otp_requests (
 id BIGSERIAL PRIMARY KEY,
 email TEXT NOT NULL,
 code_hash TEXT NOT NULL,
 role TEXT NOT NULL CHECK(role IN ('user','admin')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 expires_at TIMESTAMPTZ NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 consumed BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS otp_email_created ON otp_requests(email,created_at DESC);

CREATE TABLE IF NOT EXISTS devices (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 device_name TEXT NOT NULL,
 manufacturer TEXT,
 model TEXT,
 android_version TEXT,
 app_version TEXT NOT NULL,
 battery_percent INTEGER,
 charging BOOLEAN,
 online BOOLEAN NOT NULL DEFAULT false,
 last_seen_at TIMESTAMPTZ,
 last_latitude DOUBLE PRECISION CHECK(last_latitude BETWEEN -90 AND 90),
 last_longitude DOUBLE PRECISION CHECK(last_longitude BETWEEN -180 AND 180),
 last_accuracy REAL,
 last_location_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS devices_user ON devices(user_id);
CREATE INDEX IF NOT EXISTS devices_last_seen ON devices(last_seen_at DESC);

CREATE TABLE IF NOT EXISTS pairings (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 admin_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
 code_hash TEXT,
 expires_at TIMESTAMPTZ NOT NULL,
 claimed_at TIMESTAMPTZ,
 revoked_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS pairings_admin ON pairings(admin_id,created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS pairings_active_device ON pairings(device_id) WHERE revoked_at IS NULL AND claimed_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS location_points (
 id BIGSERIAL PRIMARY KEY,
 device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
 latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 accuracy REAL,
 captured_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS location_device_time ON location_points(device_id,captured_at DESC);

CREATE TABLE IF NOT EXISTS device_events (
 id BIGSERIAL PRIMARY KEY,
 device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
 event_type TEXT NOT NULL,
 payload JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS device_events_device_time ON device_events(device_id,created_at DESC);

-- Migration for databases created by the previous phone/Twilio schema.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE otp_requests ADD COLUMN IF NOT EXISTS email TEXT;
ALTER TABLE otp_requests ADD COLUMN IF NOT EXISTS code_hash TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users(email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS otp_email_created_v2 ON otp_requests(email,created_at DESC);
