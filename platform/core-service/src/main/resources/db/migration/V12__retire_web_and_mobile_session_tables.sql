-- Web and mobile session state (rotating refresh tokens, device registrations) now lives in
-- auth-server (adopt-oauth2-tokens-between-services design.md Decision 3). The tables become
-- meaningless here: customers simply log in again. The cards table (ATM PINs) stays.
DROP TABLE refresh_tokens;
DROP TABLE device_registrations;
