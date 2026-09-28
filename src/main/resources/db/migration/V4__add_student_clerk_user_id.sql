-- S1-02 / ADR-012: the stable identity column.
--
-- Clerk email addresses are mutable, and 16 ownership columns across 13 tables key on
-- email, so an address change today orphans a student's listings, conversations, blocks
-- and posts. This is the stable key those columns migrate to in S1-10, S1-11 and S1-12.
--
-- Nullable on purpose. Every existing row stays valid, and NULL states the truth: nobody
-- has proved they own that row yet. The column is filled only when a Clerk subject
-- creates its own directory row (and, from S1-04, by the verified user.created webhook)
-- -- never by a bulk email match. That is what keeps the 33 fabricated V3 demo students
-- unbindable: they were seeded on plausible addresses such as sarah.johnson@mail.uc.edu,
-- and matching on email alone would hand a real student a fabricated profile.
--
-- No separate index: PostgreSQL implements UNIQUE with a btree index, so the constraint
-- below already serves lookups by clerk_user_id. NULLs are distinct in a unique
-- constraint, so any number of unbound rows coexist while a Clerk subject still owns at
-- most one student row.
--
-- Rules, options considered and the D-IDENTITY ballot: docs/phase-1/identity-backfill.md

ALTER TABLE student ADD COLUMN clerk_user_id varchar(255);

ALTER TABLE student ADD CONSTRAINT uk_student_clerk_user_id UNIQUE (clerk_user_id);
