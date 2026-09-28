-- S1-06: the profile fields the directory, theming and community surfaces need.
--
-- Names and types follow the planned ERD accepted in S0-5 (docs/phase-0/erd-planned.mmd):
-- graduation_year integer, bio varchar, photo_url varchar.
--
-- All three are nullable. Every existing row predates them, and the contract asks for a
-- profile a student completes over time, not seven required fields at sign-up. A NOT NULL
-- here would mean inventing a default for 34 rows, including the fabricated V3 demo people.
--
-- Lengths, and why -- each one is mirrored by an @Size or @Min/@Max on the request objects,
-- because the defect this story exists to prevent is an over-length value reaching Postgres
-- and coming back as an opaque 500 from ExceptionTranslator's catch-all:
--
--   * bio varchar(1000) -- no recorded requirement sets this. 1000 is a few paragraphs,
--     enough for a self-description on a directory card and small enough that it can be
--     returned in a list response without bloating it. **A team decision could change it;
--     it is a chosen value, not a derived one.**
--   * photo_url varchar(255) -- matches the two URL columns already in the schema,
--     social_media_link and listing_photo.photo_url.
--   * graduation_year integer -- as the ERD has it. The request objects bound it to a
--     four-digit year; anything outside that is a typing error, not a student.
--
-- photo_url holds a reference, not an image. Constraining it to the approved asset host
-- so a student cannot point their profile at someone else's asset needs that host, which
-- is S1-05 (Cloudinary, decision 016, still Proposed). Until then the request objects
-- require https and bound the length, exactly as listing photos are bounded today.

ALTER TABLE student ADD COLUMN graduation_year integer;
ALTER TABLE student ADD COLUMN bio             varchar(1000);
ALTER TABLE student ADD COLUMN photo_url       varchar(255);
