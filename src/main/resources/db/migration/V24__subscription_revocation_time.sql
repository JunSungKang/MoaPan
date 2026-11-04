-- An unused scheduled grant may be cancelled before its planned start.
-- Only ACTIVE periods must describe a positive access interval.
ALTER TABLE account_subscriptions DROP CONSTRAINT account_subscriptions_check;
ALTER TABLE account_subscriptions ADD CONSTRAINT account_subscriptions_period_check
    CHECK (status = 'REVOKED' OR expires_at > starts_at);

-- Legacy revocation stored its event time in updated_at but retained the planned end.
-- Preserve that recorded event time and invalidate stale administrator edit versions.
UPDATE account_subscriptions
SET expires_at = updated_at, version = version + 1
WHERE status = 'REVOKED' AND expires_at IS DISTINCT FROM updated_at;
