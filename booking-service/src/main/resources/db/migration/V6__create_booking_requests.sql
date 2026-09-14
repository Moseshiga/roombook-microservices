CREATE TABLE booking_requests (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    room_id UUID NOT NULL,
    slot_start TIMESTAMP WITH TIME ZONE NOT NULL,
    booking_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT booking_requests_user_key_uq UNIQUE (user_id, idempotency_key),
    CONSTRAINT booking_requests_booking_uq UNIQUE (booking_id),
    CONSTRAINT booking_requests_booking_fk FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

COMMENT ON COLUMN booking_requests.booking_id IS
    'Null only while the owning transaction is creating the booking; committed requests always have a result.';
