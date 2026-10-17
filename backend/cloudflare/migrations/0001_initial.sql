CREATE TABLE tt_profiles (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  deleted_at INTEGER
);
CREATE TABLE tt_identities (
  provider TEXT NOT NULL CHECK(provider IN ('email','google')),
  subject TEXT NOT NULL,
  owner_id TEXT NOT NULL REFERENCES tt_profiles(id),
  PRIMARY KEY(provider, subject)
);
CREATE INDEX tt_identities_owner ON tt_identities(owner_id);
CREATE TABLE tt_email_challenges (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL,
  digest TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER,
  created_at INTEGER NOT NULL
);
CREATE INDEX tt_challenges_email ON tt_email_challenges(email, created_at);
CREATE TABLE tt_google_challenges (
  id TEXT PRIMARY KEY,
  nonce TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER
);
CREATE TABLE tt_sessions (
  token_hash TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL REFERENCES tt_profiles(id),
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX tt_sessions_owner ON tt_sessions(owner_id);
CREATE TABLE tt_rate_limits (
  key TEXT PRIMARY KEY,
  hits INTEGER NOT NULL,
  resets_at INTEGER NOT NULL
);

-- Purchases and their separate lines/assets travel as one versioned aggregate.
-- Android remains normalized in Room; cloud queries consume the incremental feed.
CREATE TABLE tt_records (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL REFERENCES tt_profiles(id),
  kind TEXT NOT NULL CHECK(kind IN ('PURCHASE','DOCUMENT')),
  revision INTEGER NOT NULL CHECK(revision > 0),
  last_operation_id TEXT NOT NULL,
  body TEXT NOT NULL CHECK(json_valid(body)),
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  UNIQUE(id, owner_id)
);
CREATE INDEX tt_records_owner ON tt_records(owner_id, kind);
CREATE TABLE tt_changes (
  cursor INTEGER PRIMARY KEY AUTOINCREMENT,
  owner_id TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  kind TEXT NOT NULL,
  revision INTEGER NOT NULL,
  body TEXT NOT NULL CHECK(json_valid(body)),
  deleted_at INTEGER
);
CREATE INDEX tt_changes_owner_cursor ON tt_changes(owner_id, cursor);
CREATE TRIGGER tt_records_insert AFTER INSERT ON tt_records BEGIN
  INSERT INTO tt_changes(owner_id,entity_id,kind,revision,body,deleted_at)
  VALUES(new.owner_id,new.id,new.kind,new.revision,new.body,new.deleted_at);
END;
CREATE TRIGGER tt_records_update AFTER UPDATE ON tt_records BEGIN
  INSERT INTO tt_changes(owner_id,entity_id,kind,revision,body,deleted_at)
  VALUES(new.owner_id,new.id,new.kind,new.revision,new.body,new.deleted_at);
END;
CREATE TABLE tt_mutations (
  owner_id TEXT NOT NULL,
  operation_id TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  revision INTEGER NOT NULL,
  request_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY(owner_id, operation_id)
);
CREATE TABLE tt_variants (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL REFERENCES tt_profiles(id),
  document_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('ORIGINAL','OPTIMIZED','THUMBNAIL')),
  storage_key TEXT NOT NULL UNIQUE,
  mime_type TEXT NOT NULL,
  byte_size INTEGER NOT NULL CHECK(byte_size BETWEEN 1 AND 26214400),
  sha256 TEXT NOT NULL CHECK(length(sha256)=64),
  verified_at INTEGER,
  UNIQUE(document_id, kind),
  FOREIGN KEY(document_id, owner_id) REFERENCES tt_records(id,owner_id)
);
CREATE INDEX tt_variants_owner ON tt_variants(owner_id, document_id);
-- A failed compare-and-swap aborts the whole D1 batch, including feed/idempotency.
CREATE TABLE tt_write_guard (ok INTEGER NOT NULL CHECK(ok=1));
