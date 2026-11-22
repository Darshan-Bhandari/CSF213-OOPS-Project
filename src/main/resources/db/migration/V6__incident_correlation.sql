CREATE INDEX incidents_correlation_lookup_idx
    ON incidents(service_id, category_id, created_at DESC, id DESC)
    WHERE status NOT IN ('RESOLVED', 'VERIFIED', 'CLOSED');

CREATE TABLE incident_correlation_candidates (
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    candidate_id BIGINT NOT NULL REFERENCES incidents(id),
    similarity SMALLINT NOT NULL CHECK (similarity BETWEEN 0 AND 100),
    PRIMARY KEY (incident_id, candidate_id),
    CHECK (incident_id <> candidate_id)
);

CREATE TABLE incident_duplicate_links (
    incident_id BIGINT PRIMARY KEY REFERENCES incidents(id),
    master_id BIGINT NOT NULL REFERENCES incidents(id),
    linked_by BIGINT NOT NULL REFERENCES user_accounts(id),
    linked_at TIMESTAMPTZ NOT NULL,
    CHECK (incident_id <> master_id)
);
CREATE INDEX incident_duplicate_master_idx ON incident_duplicate_links(master_id);
