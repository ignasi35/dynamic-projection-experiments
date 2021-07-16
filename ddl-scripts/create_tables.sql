--DROP TABLE IF EXISTS public.event_journal;

CREATE TABLE IF NOT EXISTS public.event_journal(
  -- total ordering of the journal (used as offset, monotonically increasing, unique)
  ordering BIGSERIAL,
  -- entity identifier ("type$id") and event ordering given a single entity
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,

  -- soft delete
  deleted BOOLEAN DEFAULT FALSE NOT NULL,

  -- Writer identifies the process that did the write
  writer VARCHAR(255) NOT NULL,
  write_timestamp BIGINT,
  -- ???
  adapter_manifest VARCHAR(255),

  -- The event payload, and the serialiser metadata (name and version)
  event_ser_id INTEGER NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,
  event_payload BYTEA NOT NULL,

  -- Used for extra payload (e.g. Replicated Event-Sourcing)
  meta_ser_id INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload BYTEA,

  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON public.event_journal(ordering);

--DROP TABLE IF EXISTS public.event_tag;

CREATE TABLE IF NOT EXISTS public.event_tag(
    event_id BIGINT,
    tag VARCHAR(256),
    PRIMARY KEY(event_id, tag),
    CONSTRAINT fk_event_journal
      FOREIGN KEY(event_id)
      REFERENCES event_journal(ordering)
      ON DELETE CASCADE
);

--DROP TABLE IF EXISTS public.snapshot;

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,

  snapshot_ser_id INTEGER NOT NULL,
  snapshot_ser_manifest VARCHAR(255) NOT NULL,
  snapshot_payload BYTEA NOT NULL,

  meta_ser_id INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload BYTEA,

  PRIMARY KEY(persistence_id, sequence_number)
);

--drop table if exists public.akka_projection_offset_store;

CREATE TABLE IF NOT EXISTS public.akka_projection_offset_store (
    projection_name VARCHAR(255) NOT NULL,
    projection_key VARCHAR(255) NOT NULL,
    current_offset VARCHAR(255) NOT NULL,
    manifest VARCHAR(4) NOT NULL,
    mergeable BOOLEAN NOT NULL,
    last_updated BIGINT NOT NULL,
    PRIMARY KEY(projection_name, projection_key)
    );

CREATE INDEX IF NOT EXISTS projection_name_index ON akka_projection_offset_store (projection_name);