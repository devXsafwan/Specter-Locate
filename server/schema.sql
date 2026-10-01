CREATE TABLE IF NOT EXISTS location_points (
 id BIGSERIAL PRIMARY KEY,
 user_id TEXT NOT NULL,
 latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 accuracy REAL,
 captured_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS location_points_user_time ON location_points(user_id,captured_at DESC);