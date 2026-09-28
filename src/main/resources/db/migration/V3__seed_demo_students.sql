-- Demo directory data: the 33 students that used to live in h2-data.sql.
--
-- Resolves Open Question 9. Without this a fresh database has an empty directory and
-- objective 5 (partial-match search across schools) has nothing to match, so the
-- feature cannot be demonstrated. These are fabricated people, present so the
-- directory, messaging and listing flows have something to exercise.
--
-- REMOVE THIS BEFORE THE PLATFORM CARRIES REAL ACCOUNTS. Delete with a later
-- migration, never by editing this file — V3 has already been applied.
--
-- Two deliberate differences from the h2-data.sql original:
--   * schools are matched by name rather than a hardcoded id, so this survives any
--     future change to the order of V2__seed_schools.sql;
--   * app_user.password is left NULL. The original seeded bcrypt hashes of a shared
--     password "passw0rd!"; Clerk owns credentials now (decision 013) and this
--     application stores none, so seeding password hashes would reintroduce exactly
--     the thing the Clerk migration removed.

INSERT INTO student (first_name, last_name, resident_city, resident_state,
                     university_id, grade, major, email, social_media_link)
SELECT v.first_name, v.last_name, v.resident_city, v.resident_state,
       u.id, v.grade, v.major, v.email, v.social_media_link
FROM (VALUES
    ('Sarah', 'Johnson', 'Cincinnati', 'OH', 'University of Cincinnati', 'Junior', 'Computer Science', 'sarah.johnson@mail.uc.edu', 'https://linkedin.com/in/sarahjohnson'),
    ('Michael', 'Chen', 'Mason', 'OH', 'University of Cincinnati', 'Senior', 'Electrical Engineering', 'michael.chen@mail.uc.edu', 'https://twitter.com/mchen_uc'),
    ('Emily', 'Rodriguez', 'Blue Ash', 'OH', 'University of Cincinnati', 'Freshman', 'Biology', 'emily.rodriguez@mail.uc.edu', NULL),
    ('James', 'Williams', 'Norwood', 'OH', 'University of Cincinnati', 'Sophomore', 'Business Administration', 'james.williams@mail.uc.edu', 'https://instagram.com/jameswilliams'),
    ('Jessica', 'Brown', 'Hyde Park', 'OH', 'University of Cincinnati', 'Senior', 'Mechanical Engineering', 'jessica.brown@mail.uc.edu', 'https://linkedin.com/in/jessicabrown'),
    ('David', 'Martinez', 'Clifton', 'OH', 'University of Cincinnati', 'Junior', 'Psychology', 'david.martinez@mail.uc.edu', NULL),
    ('Ashley', 'Taylor', 'Florence', 'KY', 'Northern Kentucky University', 'Sophomore', 'Marketing', 'taylora1@nku.edu', 'https://linkedin.com/in/ashleytaylor'),
    ('Brandon', 'Anderson', 'Covington', 'KY', 'Northern Kentucky University', 'Freshman', 'Information Technology', 'andersonb2@nku.edu', NULL),
    ('Christopher', 'Jackson', 'Fort Thomas', 'KY', 'Northern Kentucky University', 'Junior', 'Criminal Justice', 'jacksonc4@nku.edu', 'https://instagram.com/chrisjackson'),
    ('Amanda', 'White', 'Highland Heights', 'KY', 'Northern Kentucky University', 'Sophomore', 'Nursing', 'whitea5@nku.edu', NULL),
    ('Ryan', 'Harris', 'Norwood', 'OH', 'Xavier University', 'Junior', 'Finance', 'harrisr@xavier.edu', 'https://linkedin.com/in/ryanharris'),
    ('Lauren', 'Martin', 'Cincinnati', 'OH', 'Xavier University', 'Senior', 'Communications', 'martinl@xavier.edu', 'https://twitter.com/laurenmartin'),
    ('Daniel', 'Thompson', 'Oakley', 'OH', 'Xavier University', 'Freshman', 'Philosophy', 'thompsond@xavier.edu', NULL),
    ('Nicole', 'Garcia', 'Mount Lookout', 'OH', 'Xavier University', 'Sophomore', 'English', 'garcian@xavier.edu', 'https://instagram.com/nicolegarcia'),
    ('Kevin', 'Lee', 'Evanston', 'OH', 'Xavier University', 'Junior', 'Political Science', 'leek@xavier.edu', NULL),
    ('Samantha', 'Wilson', 'Oxford', 'OH', 'Miami University', 'Sophomore', 'Architecture', 'wilsons2@miamioh.edu', 'https://linkedin.com/in/samanthawilson'),
    ('Tyler', 'Moore', 'Hamilton', 'OH', 'Miami University', 'Senior', 'Accounting', 'mooret3@miamioh.edu', 'https://twitter.com/tylermoore'),
    ('Rachel', 'Davis', 'Fairfield', 'OH', 'Miami University', 'Freshman', 'Chemistry', 'davisr4@miamioh.edu', NULL),
    ('Andrew', 'Miller', 'West Chester', 'OH', 'Miami University', 'Junior', 'History', 'millera5@miamioh.edu', 'https://instagram.com/andrewmiller'),
    ('Olivia', 'Clark', 'Liberty Township', 'OH', 'Miami University', 'Senior', 'International Studies', 'clarko6@miamioh.edu', 'https://linkedin.com/in/oliviaclark'),
    ('Matthew', 'Lewis', 'Crestview Hills', 'KY', 'Thomas More University', 'Freshman', 'Education', 'lewism@thomasmore.edu', NULL),
    ('Brittany', 'Walker', 'Erlanger', 'KY', 'Thomas More University', 'Sophomore', 'Biology', 'walkerb@thomasmore.edu', 'https://twitter.com/brittanywalker'),
    ('Joshua', 'Hall', 'Independence', 'KY', 'Thomas More University', 'Junior', 'Sports Management', 'hallj@thomasmore.edu', 'https://linkedin.com/in/joshuahall'),
    ('Kayla', 'Young', 'Burlington', 'KY', 'Thomas More University', 'Senior', 'Theatre Arts', 'youngk@thomasmore.edu', NULL),
    ('Eric', 'King', 'Clifton', 'OH', 'Cincinnati State Technical and Community College', 'Freshman', 'Information Technology', 'eric.king@cincinnatistate.edu', 'https://instagram.com/ericking'),
    ('Stephanie', 'Wright', 'Northside', 'OH', 'Cincinnati State Technical and Community College', 'Sophomore', 'Graphic Design', 'stephanie.wright@cincinnatistate.edu', NULL),
    ('Justin', 'Lopez', 'Price Hill', 'OH', 'Cincinnati State Technical and Community College', 'Freshman', 'Culinary Arts', 'justin.lopez@cincinnatistate.edu', 'https://twitter.com/justinlopez'),
    ('Melissa', 'Hill', 'Delhi Township', 'OH', 'Mount St. Joseph University', 'Junior', 'Social Work', 'melissa.hill@msj.edu', 'https://linkedin.com/in/melissahill'),
    ('Nathan', 'Scott', 'Green Township', 'OH', 'Mount St. Joseph University', 'Senior', 'Art Therapy', 'nathan.scott@msj.edu', NULL),
    ('Victoria', 'Green', 'Cheviot', 'OH', 'Mount St. Joseph University', 'Sophomore', 'Music Education', 'victoria.green@msj.edu', 'https://instagram.com/victoriagreen'),
    ('Alexander', 'Adams', 'Madeira', 'OH', 'Cincinnati Christian University', 'Freshman', 'Biblical Studies', 'alexander.adams@ccuniversity.edu', NULL),
    ('Hannah', 'Baker', 'Kenwood', 'OH', 'Cincinnati Christian University', 'Junior', 'Ministry', 'hannah.baker@ccuniversity.edu', 'https://twitter.com/hannahbaker'),
    ('Zachary', 'Nelson', 'Montgomery', 'OH', 'Cincinnati Christian University', 'Senior', 'Youth Ministry', 'zachary.nelson@ccuniversity.edu', 'https://linkedin.com/in/zacharynelson')
) AS v (first_name, last_name, resident_city, resident_state, university_name,
        grade, major, email, social_media_link)
JOIN university u ON u.name = v.university_name
ON CONFLICT (email) DO NOTHING;

-- insertNewStudent keeps one app_user row per student; the seed holds to the same
-- invariant so a seeded email is not treated as an unclaimed profile.
INSERT INTO app_user (role, email)
SELECT 'USER', s.email FROM student s
ON CONFLICT (email) DO NOTHING;
