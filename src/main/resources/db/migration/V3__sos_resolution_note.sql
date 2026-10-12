-- ===========================================================================
-- SITHIYAMA backend - SOS triage note
--
-- A ranger resolving an alert used to overwrite the original distress
-- `message` with their triage note, losing the caller's own words. Keep the
-- two apart: `message` is what the person in trouble said, `resolution_note`
-- is what the operations desk recorded when closing the alert.
-- ===========================================================================

ALTER TABLE sos_alerts ADD COLUMN resolution_note varchar(500);
