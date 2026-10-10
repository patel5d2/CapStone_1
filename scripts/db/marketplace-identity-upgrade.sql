-- ADR-012, marketplace slice: upgrade an existing PostgreSQL database in place.
--
-- A new database gets these columns from src/main/resources/schema.sql. A database that
-- already holds data (the Aiven dev database, any long-lived instance) needs this once:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/marketplace-identity-upgrade.sql
-- It is idempotent: re-running it changes nothing that a previous run already did.
--
-- Backfill rule: a row gets a subject only when exactly one known Clerk identity holds its
-- address (a student row bound to that subject, or the webhook's clerk_identity record).
-- Every other row is left on its address and counted at the end; the application binds it
-- the first time its owner signs in (MarketplaceClaims via CallerIdentity.caller). Nothing
-- is deleted or rewritten: only the new subject columns are filled.

BEGIN;

ALTER TABLE listing ADD COLUMN IF NOT EXISTS seller_subject varchar(255);
ALTER TABLE listing_favorite ADD COLUMN IF NOT EXISTS user_subject varchar(255);
ALTER TABLE listing_report ADD COLUMN IF NOT EXISTS reporter_subject varchar(255);

CREATE INDEX IF NOT EXISTS idx_listing_seller_subject ON listing (seller_subject);
CREATE UNIQUE INDEX IF NOT EXISTS uk_listing_favorite_subject ON listing_favorite (listing_id, user_subject);
CREATE INDEX IF NOT EXISTS idx_listing_favorite_user_subject ON listing_favorite (user_subject);

-- Addresses held by exactly one known subject. Two subjects on one address (recycled
-- between accounts) are deliberately absent: that needs a human, not a guess.
CREATE TEMP TABLE address_subject ON COMMIT DROP AS
-- Addresses compare case-insensitively, as the application's claims do.
SELECT email, min(subject) AS subject
FROM (SELECT lower(email) AS email, clerk_user_id AS subject FROM student WHERE clerk_user_id IS NOT NULL
      UNION
      SELECT lower(email), clerk_user_id FROM clerk_identity) known
GROUP BY email
HAVING count(DISTINCT subject) = 1;

UPDATE listing l SET seller_subject = a.subject
FROM address_subject a
WHERE l.seller_subject IS NULL AND lower(l.seller_email) = a.email;

-- Favorites: one row per (listing, subject), so the unique index cannot trip even if one
-- person favorited a listing under two of their addresses.
UPDATE listing_favorite f SET user_subject = pick.subject
FROM (SELECT DISTINCT ON (f.listing_id, a.subject) f.id, a.subject
      FROM listing_favorite f JOIN address_subject a ON a.email = lower(f.user_email)
      WHERE f.user_subject IS NULL
        AND NOT EXISTS (SELECT 1 FROM listing_favorite o
                        WHERE o.listing_id = f.listing_id AND o.user_subject = a.subject)
      ORDER BY f.listing_id, a.subject, f.id) pick
WHERE f.id = pick.id;

UPDATE listing_report r SET reporter_subject = a.subject
FROM address_subject a
WHERE r.reporter_subject IS NULL AND lower(r.reporter_email) = a.email;

-- The record of unmatched rows: still keyed only on an address after this run.
SELECT 'listing.seller_subject' AS identity_column,
       count(*) FILTER (WHERE seller_subject IS NULL) AS left_on_address, count(*) AS total_rows
FROM listing
UNION ALL SELECT 'listing_favorite.user_subject', count(*) FILTER (WHERE user_subject IS NULL), count(*) FROM listing_favorite
UNION ALL SELECT 'listing_report.reporter_subject', count(*) FILTER (WHERE reporter_subject IS NULL), count(*) FROM listing_report;

COMMIT;
