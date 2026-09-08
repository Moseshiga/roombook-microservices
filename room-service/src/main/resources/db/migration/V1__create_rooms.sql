CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    location VARCHAR(120) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT rooms_name_uq UNIQUE (name)
);
