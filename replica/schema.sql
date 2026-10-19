-- Local SQLite schema (Room). Single user, on device, no sync.
-- IDs are UUID strings. Times are UTC epoch milliseconds.

CREATE TABLE assistant (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,
  instruction  TEXT NOT NULL,            -- the prompt template; user text is inserted as data
  icon         TEXT,
  is_builtin   INTEGER NOT NULL DEFAULT 0 CHECK (is_builtin IN (0,1)),
  sort_order   INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE tone (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,
  instruction  TEXT NOT NULL,
  is_builtin   INTEGER NOT NULL DEFAULT 0 CHECK (is_builtin IN (0,1)),
  sort_order   INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE theme (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,
  colors_json  TEXT NOT NULL,
  layout_json  TEXT,
  is_builtin   INTEGER NOT NULL DEFAULT 0 CHECK (is_builtin IN (0,1)),
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);

-- Usage log. Stores NO typed text, only counts and timings, to keep privacy promises.
CREATE TABLE ai_usage (
  id           TEXT PRIMARY KEY,
  feature      TEXT NOT NULL CHECK (feature IN
                 ('grammar','tone','reply','assistant','translate','voice')),
  engine       TEXT NOT NULL CHECK (engine IN ('gemma_local','gemini_key')),
  duration_ms  INTEGER NOT NULL,
  ok           INTEGER NOT NULL CHECK (ok IN (0,1)),
  created_at   INTEGER NOT NULL
);
CREATE INDEX idx_ai_usage_feature_time ON ai_usage(feature, created_at);

CREATE TABLE model_file (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,            -- e.g. gemma-4-E2B-it
  path         TEXT NOT NULL,
  size_bytes   INTEGER NOT NULL,
  sha256       TEXT NOT NULL,
  status       TEXT NOT NULL CHECK (status IN ('downloading','ready','failed')),
  created_at   INTEGER NOT NULL
);

-- Settings (selected engine, language prefs, keyboard height, optional Gemini key reference)
-- live in DataStore, not SQL. The Gemini key itself is kept in Keystore-backed storage.
