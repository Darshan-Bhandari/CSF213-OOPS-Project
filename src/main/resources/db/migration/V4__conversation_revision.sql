-- Ensure message writes change the incident version even at an identical clock instant.
ALTER TABLE incidents ADD COLUMN conversation_revision BIGINT NOT NULL DEFAULT 0
 CHECK (conversation_revision >= 0);
