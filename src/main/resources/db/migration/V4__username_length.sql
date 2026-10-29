-- Preserve existing accounts while permitting usernames from 1 to 128 ASCII characters.
ALTER TABLE users DROP CONSTRAINT users_username_check;
ALTER TABLE users ALTER COLUMN username TYPE varchar(128);
ALTER TABLE users ADD CONSTRAINT users_username_check CHECK (username ~ '^[a-z0-9_]{1,128}$');
