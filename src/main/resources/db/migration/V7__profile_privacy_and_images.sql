-- S1-08 dependencies. Existing profiles have no privacy row and default private.
CREATE TABLE profile_privacy (
    student_id bigint PRIMARY KEY REFERENCES student(id) ON DELETE CASCADE,
    show_major boolean NOT NULL DEFAULT false,
    show_graduation_year boolean NOT NULL DEFAULT false,
    show_bio boolean NOT NULL DEFAULT false,
    show_photo boolean NOT NULL DEFAULT false
);

-- Record the provider id BEFORE upload, so even ambiguous/time-out outcomes can
-- be cleaned up. No provider credentials are stored here.
CREATE TABLE image_asset (
    id varchar(36) PRIMARY KEY,
    owner_subject varchar(255) NOT NULL,
    public_id varchar(255) NOT NULL UNIQUE,
    url varchar(255) UNIQUE,
    student_id bigint REFERENCES student(id) ON DELETE SET NULL,
    delete_after timestamp with time zone
);
CREATE INDEX idx_image_asset_cleanup ON image_asset(delete_after);
CREATE INDEX idx_image_asset_student ON image_asset(student_id);
