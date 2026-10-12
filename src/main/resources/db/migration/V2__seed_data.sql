-- ===========================================================================
-- SITHIYAMA backend - demo seed data (PostgreSQL / PostGIS only)
--
-- Creates three accounts and three well-known Sri Lankan trail routes so the
-- route library is useful the moment the stack comes up.
--
--   admin  / Password123!   (ROLE_ADMIN)
--   guide  / Password123!   (ROLE_GUIDE)
--   ranger / Password123!   (ROLE_RANGER)
--
-- Passwords are hashed with pgcrypto's bcrypt, which is compatible with
-- Spring Security's BCryptPasswordEncoder.
-- ===========================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, username, email, password_hash, display_name, phone, locale, role, enabled)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'admin', 'admin@sithiyama.lk',
     crypt('Password123!', gen_salt('bf')), 'SITHIYAMA Admin', NULL, 'en', 'ROLE_ADMIN', true),
    ('22222222-2222-2222-2222-222222222222', 'guide', 'guide@sithiyama.lk',
     crypt('Password123!', gen_salt('bf')), 'Knuckles Trail Guide', '+94770000001', 'si', 'ROLE_GUIDE', true),
    ('33333333-3333-3333-3333-333333333333', 'ranger', 'ranger@sithiyama.lk',
     crypt('Password123!', gen_salt('bf')), 'Riverston Ranger Post', '+94770000002', 'en', 'ROLE_RANGER', true)
ON CONFLICT (username) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Routes (geometry left to the trigger; fields are the portable representation)
-- ---------------------------------------------------------------------------
INSERT INTO routes (id, name, description, region, difficulty, visibility,
                    distance_meters, ascent_meters, estimated_duration_minutes,
                    start_latitude, start_longitude, end_latitude, end_longitude,
                    min_latitude, min_longitude, max_latitude, max_longitude,
                    path_wkt, creator_id)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001',
     'Dothalugala Summit Trail',
     'A steady climb through cloud forest to the Dothalugala summit in the Knuckles range. Muddy after rain; the final ridge is exposed.',
     'Knuckles', 'MODERATE', 'PUBLIC', 6500, 420, 240,
     7.4640, 80.7780, 7.4462, 80.7935,
     7.4462, 80.7780, 7.4640, 80.7935,
     'LINESTRING(80.7780 7.4640, 80.7810 7.4615, 80.7845 7.4588, 80.7880 7.4550, 80.7910 7.4505, 80.7935 7.4462)',
     '22222222-2222-2222-2222-222222222222'),

    ('aaaaaaaa-0000-0000-0000-000000000002',
     'Riverston Knife Edge',
     'The classic Knuckles ridge walk from Riverston. Long, hot and unforgiving, with strong wind on the knife edge.',
     'Knuckles', 'HARD', 'PUBLIC', 9200, 780, 330,
     7.5210, 80.7420, 7.5480, 80.7690,
     7.5210, 80.7420, 7.5480, 80.7690,
     'LINESTRING(80.7420 7.5210, 80.7465 7.5255, 80.7510 7.5300, 80.7555 7.5345, 80.7600 7.5390, 80.7645 7.5435, 80.7690 7.5480)',
     '22222222-2222-2222-2222-222222222222'),

    ('aaaaaaaa-0000-0000-0000-000000000003',
     'Horton Plains World''s End Loop',
     'A signposted loop from the Horton Plains visitor centre to World''s End and Baker''s Falls. Early start recommended before the mist closes in.',
     'Horton Plains', 'EASY', 'PUBLIC', 9500, 180, 180,
     6.8120, 80.7990, 6.7880, 80.8270,
     6.7880, 80.7990, 6.8120, 80.8270,
     'LINESTRING(80.7990 6.8120, 80.8030 6.8090, 80.8070 6.8055, 80.8110 6.8020, 80.8150 6.7985, 80.8190 6.7950, 80.8230 6.7915, 80.8270 6.7880)',
     '22222222-2222-2222-2222-222222222222')
ON CONFLICT (id) DO NOTHING;

INSERT INTO route_tags (route_id, tag)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'knuckles'),
    ('aaaaaaaa-0000-0000-0000-000000000001', 'summit'),
    ('aaaaaaaa-0000-0000-0000-000000000001', 'forest'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'knuckles'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'ridge'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'exposed'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'horton-plains'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'loop'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'waterfall');

INSERT INTO route_waypoints (id, route_id, sequence_number, name, type, latitude, longitude, elevation_meters, note)
VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 1, 'Trailhead - Ilukumbura', 'TRAILHEAD', 7.4640, 80.7780, 980, 'Parking and last reliable phone signal.'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 2, 'Cloud forest gate', 'CHECKPOINT', 7.4588, 80.7845, 1240, 'Log in with the forest officer if present.'),
    ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000001', 3, 'Dothalugala summit', 'VIEWPOINT', 7.4462, 80.7935, 1380, 'Exposed ridge; turn back if fog closes in.'),

    ('bbbbbbbb-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000002', 1, 'Riverston gap', 'TRAILHEAD', 7.5210, 80.7420, 1100, 'Start point and tea shop.'),
    ('bbbbbbbb-0000-0000-0000-000000000005', 'aaaaaaaa-0000-0000-0000-000000000002', 2, 'Knife edge start', 'HAZARD', 7.5345, 80.7555, 1420, 'Strong wind; single file, no handrail.'),
    ('bbbbbbbb-0000-0000-0000-000000000006', 'aaaaaaaa-0000-0000-0000-000000000002', 3, 'Falls trail junction', 'WATER', 7.5435, 80.7645, 1180, 'Last water source before the descent.'),

    ('bbbbbbbb-0000-0000-0000-000000000007', 'aaaaaaaa-0000-0000-0000-000000000003', 1, 'Visitor centre', 'TRAILHEAD', 6.8120, 80.7990, 2140, 'Tickets and vehicle parking.'),
    ('bbbbbbbb-0000-0000-0000-000000000008', 'aaaaaaaa-0000-0000-0000-000000000003', 2, 'World''s End', 'VIEWPOINT', 6.7950, 80.8190, 2210, 'Drop of ~870 m; arrives by 09:00 before mist.'),
    ('bbbbbbbb-0000-0000-0000-000000000009', 'aaaaaaaa-0000-0000-0000-000000000003', 3, 'Baker''s Falls', 'WATER', 6.7880, 80.8270, 1980, 'Steep descent to the falls; slippery steps.');
