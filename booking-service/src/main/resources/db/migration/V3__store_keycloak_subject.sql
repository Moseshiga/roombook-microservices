ALTER TABLE bookings
    ALTER COLUMN user_id TYPE VARCHAR(255)
    USING user_id::text;
