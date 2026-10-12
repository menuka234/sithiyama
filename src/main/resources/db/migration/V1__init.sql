-- ===========================================================================
-- SITHIYAMA backend - initial schema (PostgreSQL + PostGIS)
--
-- The ORM (Hibernate) treats Flyway as the single source of truth for the
-- schema (ddl-auto: none). Column names follow Hibernate's default
-- CamelCase -> snake_case physical naming strategy.
--
-- Geometry strategy
--   Entities carry the portable, readable representation of a path
--   (`path_wkt`, `trail_wkt`) plus plain latitude/longitude columns. PostGIS
--   `geometry` columns are maintained automatically by triggers and indexed
--   with GIST, so radius ("near") searches run in the database while the
--   application stays portable (an in-memory H2 profile is also possible).
-- ===========================================================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      varchar(64)  NOT NULL,
    email         varchar(160) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name  varchar(120) NOT NULL,
    phone         varchar(32),
    locale        varchar(16)  NOT NULL DEFAULT 'en',
    role          varchar(32)  NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------------
-- Community route library
-- ---------------------------------------------------------------------------
CREATE TABLE routes (
    id                         uuid PRIMARY KEY,
    name                       varchar(160) NOT NULL,
    description                text,
    region                     varchar(120),
    difficulty                 varchar(32)  NOT NULL,
    visibility                 varchar(32)  NOT NULL DEFAULT 'PUBLIC',
    distance_meters            double precision NOT NULL DEFAULT 0,
    ascent_meters              double precision NOT NULL DEFAULT 0,
    estimated_duration_minutes integer      NOT NULL DEFAULT 0,
    start_latitude             double precision NOT NULL,
    start_longitude            double precision NOT NULL,
    end_latitude               double precision,
    end_longitude              double precision,
    min_latitude               double precision NOT NULL,
    min_longitude              double precision NOT NULL,
    max_latitude               double precision NOT NULL,
    max_longitude              double precision NOT NULL,
    path_wkt                   text         NOT NULL,
    creator_id                 uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    updated_at                 timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_routes_creator    ON routes (creator_id);
CREATE INDEX idx_routes_region     ON routes (region);
CREATE INDEX idx_routes_difficulty ON routes (difficulty);
CREATE INDEX idx_routes_visibility ON routes (visibility);
CREATE INDEX idx_routes_bbox       ON routes (min_latitude, max_latitude, min_longitude, max_longitude);

-- Free-text tags attached to a route.
CREATE TABLE route_tags (
    route_id uuid        NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    tag      varchar(48) NOT NULL
);
CREATE INDEX idx_route_tags_route ON route_tags (route_id);

CREATE TABLE route_waypoints (
    id               uuid PRIMARY KEY,
    route_id         uuid         NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    sequence_number  integer      NOT NULL,
    name             varchar(160),
    type             varchar(32)  NOT NULL DEFAULT 'CHECKPOINT',
    latitude         double precision NOT NULL,
    longitude        double precision NOT NULL,
    elevation_meters double precision,
    note             varchar(500),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_route_waypoints_route ON route_waypoints (route_id, sequence_number);

-- ---------------------------------------------------------------------------
-- Recorded GPS tracks (app recordings and Companion Node "black box" logs)
-- ---------------------------------------------------------------------------
CREATE TABLE track_recordings (
    id               uuid PRIMARY KEY,
    user_id          uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    route_id         uuid         REFERENCES routes (id) ON DELETE SET NULL,
    name             varchar(160) NOT NULL,
    status           varchar(32)  NOT NULL DEFAULT 'COMPLETED',
    source           varchar(32)  NOT NULL DEFAULT 'APP',
    started_at       timestamptz  NOT NULL,
    ended_at         timestamptz,
    distance_meters  double precision NOT NULL DEFAULT 0,
    point_count      integer      NOT NULL DEFAULT 0,
    min_latitude     double precision,
    min_longitude    double precision,
    max_latitude     double precision,
    max_longitude    double precision,
    path_wkt         text,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_track_recordings_user  ON track_recordings (user_id);
CREATE INDEX idx_track_recordings_route ON track_recordings (route_id);

CREATE TABLE track_points (
    id                       uuid PRIMARY KEY,
    recording_id             uuid         NOT NULL REFERENCES track_recordings (id) ON DELETE CASCADE,
    sequence_number          integer      NOT NULL,
    latitude                 double precision NOT NULL,
    longitude                double precision NOT NULL,
    elevation_meters         double precision,
    accuracy_meters          double precision,
    speed_meters_per_second  double precision,
    battery_percent          integer,
    recorded_at              timestamptz  NOT NULL
);
CREATE INDEX idx_track_points_recording ON track_points (recording_id, sequence_number);

-- ---------------------------------------------------------------------------
-- Trip plans ("tell someone your plan") and check-ins
-- ---------------------------------------------------------------------------
CREATE TABLE trip_plans (
    id                        uuid PRIMARY KEY,
    organizer_id              uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    route_id                  uuid         REFERENCES routes (id) ON DELETE SET NULL,
    title                     varchar(160) NOT NULL,
    notes                     text,
    planned_start_at          timestamptz  NOT NULL,
    planned_end_at            timestamptz,
    status                    varchar(32)  NOT NULL DEFAULT 'PLANNED',
    group_size                integer      NOT NULL DEFAULT 1,
    check_in_interval_minutes integer,
    emergency_contact_name    varchar(120),
    emergency_contact_phone   varchar(32),
    last_check_in_at          timestamptz,
    next_check_in_due_at      timestamptz,
    created_at                timestamptz  NOT NULL DEFAULT now(),
    updated_at                timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_trip_plans_organizer ON trip_plans (organizer_id);
CREATE INDEX idx_trip_plans_status    ON trip_plans (status);

CREATE TABLE trip_members (
    id         uuid PRIMARY KEY,
    trip_id    uuid         NOT NULL REFERENCES trip_plans (id) ON DELETE CASCADE,
    user_id    uuid         REFERENCES users (id) ON DELETE SET NULL,
    name       varchar(120) NOT NULL,
    phone      varchar(32),
    team_lead  boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_trip_members_trip ON trip_members (trip_id);

CREATE TABLE check_ins (
    id              uuid PRIMARY KEY,
    trip_id         uuid         NOT NULL REFERENCES trip_plans (id) ON DELETE CASCADE,
    user_id         uuid         REFERENCES users (id) ON DELETE SET NULL,
    source          varchar(32)  NOT NULL DEFAULT 'APP',
    latitude        double precision,
    longitude       double precision,
    battery_percent integer,
    message         varchar(500),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_check_ins_trip ON check_ins (trip_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- SOS alerts (raised by the app, the Companion Node or a Basecamp station)
-- ---------------------------------------------------------------------------
CREATE TABLE sos_alerts (
    id                 uuid PRIMARY KEY,
    user_id            uuid REFERENCES users (id) ON DELETE SET NULL,
    trip_id            uuid REFERENCES trip_plans (id) ON DELETE SET NULL,
    source             varchar(32)  NOT NULL DEFAULT 'APP',
    status             varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    latitude           double precision NOT NULL,
    longitude          double precision NOT NULL,
    battery_percent    integer,
    message            varchar(500),
    trail_wkt          text,
    acknowledged_by_id uuid REFERENCES users (id) ON DELETE SET NULL,
    acknowledged_at    timestamptz,
    resolved_at        timestamptz,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_sos_alerts_status  ON sos_alerts (status);
CREATE INDEX idx_sos_alerts_created ON sos_alerts (created_at DESC);
CREATE INDEX idx_sos_alerts_user    ON sos_alerts (user_id);

-- ---------------------------------------------------------------------------
-- Companion Node registry
-- ---------------------------------------------------------------------------
CREATE TABLE node_devices (
    id                uuid PRIMARY KEY,
    owner_id          uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_code       varchar(64)  NOT NULL,
    label             varchar(120),
    firmware_version  varchar(32),
    battery_percent   integer,
    last_seen_at      timestamptz,
    last_latitude     double precision,
    last_longitude    double precision,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_node_devices_code UNIQUE (device_code)
);
CREATE INDEX idx_node_devices_owner ON node_devices (owner_id);

-- ---------------------------------------------------------------------------
-- PostGIS: auto-maintained geometry columns + spatial indexes
-- ---------------------------------------------------------------------------
ALTER TABLE routes            ADD COLUMN geom geometry (LineString, 4326);
ALTER TABLE track_recordings  ADD COLUMN geom geometry (LineString, 4326);
ALTER TABLE sos_alerts        ADD COLUMN geom geometry (LineString, 4326);
ALTER TABLE route_waypoints   ADD COLUMN geom geometry (Point, 4326);

-- Sync a WKT LINESTRING text column into a typed PostGIS geometry.
CREATE OR REPLACE FUNCTION sithiyama_sync_linestring() RETURNS trigger AS $fn$
BEGIN
    IF NEW.path_wkt IS NULL OR length(NEW.path_wkt) = 0 THEN
        NEW.geom := NULL;
    ELSE
        NEW.geom := ST_GeomFromText(NEW.path_wkt, 4326);
    END IF;
    RETURN NEW;
END;
$fn$ LANGUAGE plpgsql;

-- Same, for the sos_alerts.trail_wkt column.
CREATE OR REPLACE FUNCTION sithiyama_sync_sos_trail() RETURNS trigger AS $fn$
BEGIN
    IF NEW.trail_wkt IS NULL OR length(NEW.trail_wkt) = 0 THEN
        NEW.geom := NULL;
    ELSE
        NEW.geom := ST_GeomFromText(NEW.trail_wkt, 4326);
    END IF;
    RETURN NEW;
END;
$fn$ LANGUAGE plpgsql;

-- Sync a longitude/latitude pair into a typed PostGIS point.
CREATE OR REPLACE FUNCTION sithiyama_sync_point() RETURNS trigger AS $fn$
BEGIN
    IF NEW.latitude IS NULL OR NEW.longitude IS NULL THEN
        NEW.geom := NULL;
    ELSE
        NEW.geom := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326);
    END IF;
    RETURN NEW;
END;
$fn$ LANGUAGE plpgsql;

CREATE TRIGGER trg_routes_geom
    BEFORE INSERT OR UPDATE OF path_wkt ON routes
    FOR EACH ROW EXECUTE FUNCTION sithiyama_sync_linestring();

CREATE TRIGGER trg_track_recordings_geom
    BEFORE INSERT OR UPDATE OF path_wkt ON track_recordings
    FOR EACH ROW EXECUTE FUNCTION sithiyama_sync_linestring();

CREATE TRIGGER trg_sos_alerts_geom
    BEFORE INSERT OR UPDATE OF trail_wkt ON sos_alerts
    FOR EACH ROW EXECUTE FUNCTION sithiyama_sync_sos_trail();

CREATE TRIGGER trg_route_waypoints_geom
    BEFORE INSERT OR UPDATE OF latitude, longitude ON route_waypoints
    FOR EACH ROW EXECUTE FUNCTION sithiyama_sync_point();

CREATE INDEX idx_routes_geom           ON routes           USING GIST (geom);
CREATE INDEX idx_track_recordings_geom ON track_recordings USING GIST (geom);
CREATE INDEX idx_sos_alerts_geom       ON sos_alerts       USING GIST (geom);
CREATE INDEX idx_route_waypoints_geom  ON route_waypoints  USING GIST (geom);
