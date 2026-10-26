-- =============================================================================
-- V2__seed_reference_data.sql
-- Reference data only: values the application logic depends on, not demo content.
--
-- Roles must exist before any user can register, so they belong in a migration
-- rather than in a CommandLineRunner that may or may not have executed.
-- Demo/dev fixtures (sample companies, jobs, candidates) are kept out of migrations
-- on purpose - they would then be inserted into production too.
--
-- ON CONFLICT DO NOTHING keeps the migration idempotent if it is ever re-run against
-- a database that was partially seeded by hand.
-- =============================================================================

INSERT INTO roles (name) VALUES
    ('ADMIN'),
    ('RECRUITER'),
    ('CANDIDATE')
ON CONFLICT (name) DO NOTHING;

-- A small starter vocabulary so candidates and recruiters are not typing into an empty
-- skill table on day one. Names are lower case, matching ck_skills_name_lowercase.
INSERT INTO skills (name) VALUES
    ('java'),
    ('spring boot'),
    ('spring security'),
    ('hibernate'),
    ('sql'),
    ('postgresql'),
    ('mysql'),
    ('rest api'),
    ('microservices'),
    ('docker'),
    ('kubernetes'),
    ('git'),
    ('maven'),
    ('junit'),
    ('python'),
    ('javascript'),
    ('typescript'),
    ('react'),
    ('node.js'),
    ('aws'),
    ('linux'),
    ('redis'),
    ('kafka'),
    ('data structures'),
    ('system design')
ON CONFLICT (name) DO NOTHING;
