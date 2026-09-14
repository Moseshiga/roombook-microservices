CREATE TABLE user_profiles (
    user_id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    locale VARCHAR(35) NOT NULL,
    notifications_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT user_profiles_email_check CHECK (email = lower(email)),
    CONSTRAINT user_profiles_updated_check CHECK (updated_at >= created_at)
);
