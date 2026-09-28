-- S1-04 / ADR-012: what Clerk's user.created webhook can actually record today.
--
-- The story asks the webhook to create the student row. It cannot, and this table is
-- the honest alternative rather than a silent redefinition of the requirement:
--
--   * `student` has seven NOT NULL columns a Clerk user event does not carry --
--     resident_city, resident_state, grade, major, university_id, and first/last name
--     (Clerk allows both to be null). Creating the row from an event means inventing
--     five values for a real person.
--   * university_id needs the domain-to-school mapping from S1-03, which does not exist
--     and is itself blocked on the unvoted D-SCHOOLS decision. Guessing a school is
--     exactly what that issue forbids.
--
-- So the webhook records the *identity* -- the verified Clerk subject and the address it
-- signed up with -- and the directory row is still completed by POST /student, which has
-- bound clerk_user_id since S1-02. That is the "pending profile" state: an account that
-- exists in Clerk and here, but has no directory row yet.
--
-- last_event_at makes the handler idempotent and order-safe without a separate ledger:
-- the upsert only advances a row when the incoming event is newer, so a Clerk retry is a
-- no-op and an out-of-order delivery cannot roll an address back. last_event_id is kept
-- for tracing a row back to the delivery that last wrote it.
--
-- No foreign key to student on purpose: the identity legitimately exists before the
-- directory row does, which is the whole point of the pending state.

CREATE TABLE clerk_identity (
    clerk_user_id varchar(255) PRIMARY KEY,
    email         varchar(255)               NOT NULL,
    last_event_id varchar(255)               NOT NULL,
    last_event_at timestamp(6) with time zone NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL DEFAULT now(),
    updated_at    timestamp(6) with time zone NOT NULL DEFAULT now()
);

-- Finding the identity behind an address during profile completion.
CREATE INDEX idx_clerk_identity_email ON clerk_identity (email);
