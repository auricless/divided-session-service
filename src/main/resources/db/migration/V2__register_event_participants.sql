ALTER TABLE session_events ADD COLUMN roster_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE session_events ADD COLUMN latest_assignment_json TEXT;

CREATE TABLE event_participants (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES session_events(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    age INTEGER NOT NULL CHECK (age > 0),
    gender VARCHAR(6) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    life_stage VARCHAR(32) CHECK (life_stage IN ('STUDENT', 'WORKING_PROFESSIONAL'))
);
CREATE INDEX event_participants_event_id_idx ON event_participants(event_id);
