CREATE TABLE session_events (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    config_json TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);
