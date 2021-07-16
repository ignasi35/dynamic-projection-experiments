# Spanner

```sql

CREATE TABLE journal (
  persistence_id STRING(MAX) NOT NULL,
  sequence_nr INT64 NOT NULL,

  writer_uuid STRING(MAX) NOT NULL,
  write_time TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),

  event BYTES(MAX),
  ser_id INT64 NOT NULL,
  ser_manifest STRING(MAX) NOT NULL,

) PRIMARY KEY (persistence_id, sequence_nr)

CREATE TABLE tags (
  persistence_id STRING(MAX) NOT NULL,
  sequence_nr INT64 NOT NULL,
  tag STRING(MAX) NOT NULL,
  write_time TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (persistence_id, sequence_nr, tag),
INTERLEAVE IN PARENT journal ON DELETE CASCADE

CREATE INDEX tags_tag_and_offset
ON tags (
  tag,
  write_time
)

```



# JDBC


```sql

CREATE TABLE IF NOT EXISTS public.event_journal(
  -- total ordering of the journal (used as offset, monotonically increasing, unique)
  ordering BIGSERIAL,

  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,

  writer VARCHAR(255) NOT NULL,
  write_timestamp BIGINT,

  event_payload BYTEA NOT NULL,
  event_ser_id INTEGER NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,

  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON public.event_journal(ordering);

CREATE TABLE IF NOT EXISTS public.event_tag(
    event_id BIGINT,
    tag VARCHAR(256),
    PRIMARY KEY(event_id, tag),
    CONSTRAINT fk_event_journal
      FOREIGN KEY(event_id)
      REFERENCES event_journal(ordering)
      ON DELETE CASCADE
);

```