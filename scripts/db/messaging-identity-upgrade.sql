-- ADR-012, messaging slice: upgrade an existing PostgreSQL database in place.
--
-- A new database gets these columns from src/main/resources/schema.sql. A database that
-- already holds data (the Aiven dev database, any long-lived instance) needs this once:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/messaging-identity-upgrade.sql
-- It is idempotent: re-running it changes nothing that a previous run already did.
--
-- Backfill rule: a row gets a subject only when exactly one known Clerk identity holds its
-- address (a student row bound to that subject, or the webhook's clerk_identity record).
-- Every other row is left on its address and counted at the end; the application binds it
-- the first time its owner signs in (MessagingIdentity.caller), and until then the email
-- path keeps serving it (identity-backfill.md rule 5). Nothing is deleted or rewritten:
-- membership, unread_count and last_read_at are untouched.

BEGIN;

ALTER TABLE conversation_participant ADD COLUMN IF NOT EXISTS user_subject varchar(255);
ALTER TABLE message ADD COLUMN IF NOT EXISTS sender_subject varchar(255);
ALTER TABLE blocked_user
    ADD COLUMN IF NOT EXISTS blocker_subject varchar(255),
    ADD COLUMN IF NOT EXISTS blocked_subject varchar(255);
ALTER TABLE user_report
    ADD COLUMN IF NOT EXISTS reporter_subject varchar(255),
    ADD COLUMN IF NOT EXISTS reported_subject varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_participant_subject ON conversation_participant (conversation_id, user_subject);
CREATE INDEX IF NOT EXISTS idx_conversation_participant_subject ON conversation_participant (user_subject);
CREATE UNIQUE INDEX IF NOT EXISTS uk_blocked_user_subject ON blocked_user (blocker_subject, blocked_subject);
CREATE INDEX IF NOT EXISTS idx_blocked_user_blocked_subject ON blocked_user (blocked_subject);
CREATE INDEX IF NOT EXISTS idx_user_report_reported_subject ON user_report (reported_subject);

-- Addresses held by exactly one known subject. Two subjects on one address (recycled
-- between accounts) are deliberately absent: that needs a human, not a guess.
CREATE TEMP TABLE address_subject ON COMMIT DROP AS
-- Addresses compare case-insensitively, as the application does (Party.is).
SELECT email, min(subject) AS subject
FROM (SELECT lower(email) AS email, clerk_user_id AS subject FROM student WHERE clerk_user_id IS NOT NULL
      UNION
      SELECT lower(email), clerk_user_id FROM clerk_identity) known
GROUP BY email
HAVING count(DISTINCT subject) = 1;

-- Participants: one row per (conversation, subject), so the unique index cannot trip even
-- if one person appears in a conversation under two of their addresses.
UPDATE conversation_participant p SET user_subject = pick.subject
FROM (SELECT DISTINCT ON (p.conversation_id, a.subject) p.id, a.subject
      FROM conversation_participant p JOIN address_subject a ON a.email = lower(p.user_email)
      WHERE p.user_subject IS NULL
        AND NOT EXISTS (SELECT 1 FROM conversation_participant o
                        WHERE o.conversation_id = p.conversation_id AND o.user_subject = a.subject)
      ORDER BY p.conversation_id, a.subject, p.id) pick
WHERE p.id = pick.id;

UPDATE message m SET sender_subject = a.subject
FROM address_subject a
WHERE m.sender_subject IS NULL AND lower(m.sender_email) = a.email;

-- Blocks, blocker side then blocked side, each keeping (blocker_subject, blocked_subject)
-- unique. A NULL on the other side never collides, so those rows are all kept distinct.
UPDATE blocked_user b SET blocker_subject = pick.subject
FROM (SELECT DISTINCT ON (a.subject, COALESCE(b.blocked_subject, 'row:' || b.id)) b.id, a.subject
      FROM blocked_user b JOIN address_subject a ON a.email = lower(b.blocker_email)
      WHERE b.blocker_subject IS NULL
        AND NOT EXISTS (SELECT 1 FROM blocked_user o
                        WHERE o.blocker_subject = a.subject AND o.blocked_subject = b.blocked_subject)
      ORDER BY a.subject, COALESCE(b.blocked_subject, 'row:' || b.id), b.id) pick
WHERE b.id = pick.id;

UPDATE blocked_user b SET blocked_subject = pick.subject
FROM (SELECT DISTINCT ON (COALESCE(b.blocker_subject, 'row:' || b.id), a.subject) b.id, a.subject
      FROM blocked_user b JOIN address_subject a ON a.email = lower(b.blocked_email)
      WHERE b.blocked_subject IS NULL
        AND NOT EXISTS (SELECT 1 FROM blocked_user o
                        WHERE o.blocked_subject = a.subject AND o.blocker_subject = b.blocker_subject)
      ORDER BY COALESCE(b.blocker_subject, 'row:' || b.id), a.subject, b.id) pick
WHERE b.id = pick.id;

UPDATE user_report r SET reporter_subject = a.subject
FROM address_subject a
WHERE r.reporter_subject IS NULL AND lower(r.reporter_email) = a.email;

UPDATE user_report r SET reported_subject = a.subject
FROM address_subject a
WHERE r.reported_subject IS NULL AND lower(r.reported_email) = a.email;

-- The record of unmatched rows: still keyed only on an address after this run.
SELECT 'conversation_participant.user_subject' AS identity_column,
       count(*) FILTER (WHERE user_subject IS NULL) AS left_on_address, count(*) AS total_rows
FROM conversation_participant
UNION ALL SELECT 'message.sender_subject', count(*) FILTER (WHERE sender_subject IS NULL), count(*) FROM message
UNION ALL SELECT 'blocked_user.blocker_subject', count(*) FILTER (WHERE blocker_subject IS NULL), count(*) FROM blocked_user
UNION ALL SELECT 'blocked_user.blocked_subject', count(*) FILTER (WHERE blocked_subject IS NULL), count(*) FROM blocked_user
UNION ALL SELECT 'user_report.reporter_subject', count(*) FILTER (WHERE reporter_subject IS NULL), count(*) FROM user_report
UNION ALL SELECT 'user_report.reported_subject', count(*) FILTER (WHERE reported_subject IS NULL), count(*) FROM user_report;

COMMIT;
