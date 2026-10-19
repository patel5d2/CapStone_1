-- ADR-012, community and support slice (S1-12): upgrade an existing PostgreSQL database in place.
--
-- A new database gets these columns from src/main/resources/schema.sql. A database that
-- already holds data (the Aiven dev database, any long-lived instance) needs this once:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/community-support-identity-upgrade.sql
-- It is idempotent: re-running it changes nothing that a previous run already did.
--
-- Backfill rule: a row gets a subject only when exactly one known Clerk identity holds its
-- address (a student row bound to that subject, or the webhook's clerk_identity record).
-- Every other row is left on its address and counted at the end; the application binds it
-- the first time its owner signs in (CommunityClaims, SupportClaims). Nothing is deleted or
-- rewritten: only the new *_subject columns are written.

BEGIN;

ALTER TABLE app_group ADD COLUMN IF NOT EXISTS created_by_subject varchar(255);
ALTER TABLE group_membership ADD COLUMN IF NOT EXISTS user_subject varchar(255);
ALTER TABLE post ADD COLUMN IF NOT EXISTS author_subject varchar(255);
ALTER TABLE post_comment ADD COLUMN IF NOT EXISTS author_subject varchar(255);
ALTER TABLE post_like ADD COLUMN IF NOT EXISTS user_subject varchar(255);
ALTER TABLE event ADD COLUMN IF NOT EXISTS created_by_subject varchar(255);
ALTER TABLE anonymous_request ADD COLUMN IF NOT EXISTS requester_subject varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_group_membership_subject ON group_membership (group_id, user_subject);
CREATE INDEX IF NOT EXISTS idx_group_membership_subject ON group_membership (user_subject);
CREATE UNIQUE INDEX IF NOT EXISTS uk_post_like_subject ON post_like (post_id, user_subject);
CREATE INDEX IF NOT EXISTS idx_anonymous_request_requester_subject ON anonymous_request (requester_subject);

-- Addresses held by exactly one known subject. Two subjects on one address (recycled
-- between accounts) are deliberately absent: that needs a human, not a guess.
-- Addresses compare case-insensitively, as the application's claims do.
CREATE TEMP TABLE address_subject ON COMMIT DROP AS
SELECT email, min(subject) AS subject
FROM (SELECT lower(email) AS email, clerk_user_id AS subject FROM student WHERE clerk_user_id IS NOT NULL
      UNION
      SELECT lower(email), clerk_user_id FROM clerk_identity) known
GROUP BY email
HAVING count(DISTINCT subject) = 1;

UPDATE app_group g SET created_by_subject = a.subject
FROM address_subject a
WHERE g.created_by_subject IS NULL AND lower(g.created_by_email) = a.email;

-- Memberships: one row per (group, subject), so the unique index cannot trip even if one
-- person joined a group under two of their addresses.
UPDATE group_membership m SET user_subject = pick.subject
FROM (SELECT DISTINCT ON (m.group_id, a.subject) m.id, a.subject
      FROM group_membership m JOIN address_subject a ON a.email = lower(m.user_email)
      WHERE m.user_subject IS NULL
        AND NOT EXISTS (SELECT 1 FROM group_membership o
                        WHERE o.group_id = m.group_id AND o.user_subject = a.subject)
      ORDER BY m.group_id, a.subject, m.id) pick
WHERE m.id = pick.id;

UPDATE post p SET author_subject = a.subject
FROM address_subject a
WHERE p.author_subject IS NULL AND lower(p.author_email) = a.email;

UPDATE post_comment c SET author_subject = a.subject
FROM address_subject a
WHERE c.author_subject IS NULL AND lower(c.author_email) = a.email;

-- Likes: one row per (post, subject), as memberships.
UPDATE post_like l SET user_subject = pick.subject
FROM (SELECT DISTINCT ON (l.post_id, a.subject) l.id, a.subject
      FROM post_like l JOIN address_subject a ON a.email = lower(l.user_email)
      WHERE l.user_subject IS NULL
        AND NOT EXISTS (SELECT 1 FROM post_like o
                        WHERE o.post_id = l.post_id AND o.user_subject = a.subject)
      ORDER BY l.post_id, a.subject, l.id) pick
WHERE l.id = pick.id;

UPDATE event e SET created_by_subject = a.subject
FROM address_subject a
WHERE e.created_by_subject IS NULL AND lower(e.created_by_email) = a.email;

UPDATE anonymous_request r SET requester_subject = a.subject
FROM address_subject a
WHERE r.requester_subject IS NULL AND lower(r.requester_email) = a.email;

-- The record of unmatched rows: still keyed only on an address after this run.
SELECT 'app_group.created_by_subject' AS identity_column,
       count(*) FILTER (WHERE created_by_subject IS NULL) AS left_on_address, count(*) AS total_rows
FROM app_group
UNION ALL SELECT 'group_membership.user_subject', count(*) FILTER (WHERE user_subject IS NULL), count(*) FROM group_membership
UNION ALL SELECT 'post.author_subject', count(*) FILTER (WHERE author_subject IS NULL), count(*) FROM post
UNION ALL SELECT 'post_comment.author_subject', count(*) FILTER (WHERE author_subject IS NULL), count(*) FROM post_comment
UNION ALL SELECT 'post_like.user_subject', count(*) FILTER (WHERE user_subject IS NULL), count(*) FROM post_like
UNION ALL SELECT 'event.created_by_subject', count(*) FILTER (WHERE created_by_subject IS NULL), count(*) FROM event
UNION ALL SELECT 'anonymous_request.requester_subject', count(*) FILTER (WHERE requester_subject IS NULL), count(*) FROM anonymous_request;

COMMIT;
