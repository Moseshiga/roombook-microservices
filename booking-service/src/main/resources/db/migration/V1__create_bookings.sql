CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    user_id UUID NOT NULL,
    slot_start TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT bookings_status_check CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT bookings_expiry_check CHECK (expires_at > created_at AND expires_at <= slot_start),
    CONSTRAINT bookings_hour_check CHECK (
        slot_start = date_trunc('hour', slot_start AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'
    )
);

-- Time passing does not update an index. Release expired PENDING rows explicitly.
CREATE UNIQUE INDEX bookings_active_slot_uq ON bookings (room_id, slot_start)
    WHERE status IN ('PENDING', 'CONFIRMED');
