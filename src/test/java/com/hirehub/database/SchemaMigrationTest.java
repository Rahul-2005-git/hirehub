package com.hirehub.database;

import com.hirehub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the Flyway migrations produced the schema the application expects, and
 * that the constraints which guarantee correctness are actually enforced by PostgreSQL —
 * not merely written down in a migration file.
 *
 * <p>These are the checks that would catch a migration silently failing to apply, or a
 * constraint being dropped during a future refactor.</p>
 */
class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("all migrations applied successfully")
    void migrationsApplied() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("every expected table exists in the public schema")
    void allTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);

        assertThat(tables).contains(
                "roles", "users", "companies", "recruiter_profiles", "candidate_profiles",
                "skills", "candidate_skills", "jobs", "job_skills", "applications",
                "application_status_history");
    }

    @Test
    @DisplayName("reference data is seeded: three roles")
    void rolesAreSeeded() {
        List<String> roles = jdbcTemplate.queryForList("SELECT name FROM roles", String.class);
        assertThat(roles).containsExactlyInAnyOrder("ADMIN", "RECRUITER", "CANDIDATE");
    }

    @Test
    @DisplayName("the duplicate-application guard exists as a unique constraint")
    void duplicateApplicationConstraintExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_name = 'uq_applications_job_candidate' "
                        + "AND constraint_type = 'UNIQUE'", Integer.class);

        assertThat(count)
                .as("applications(job_id, candidate_profile_id) must be UNIQUE — this is the "
                        + "only thing that stops a duplicate application under concurrency")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the job feed and search indexes exist")
    void searchIndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

        assertThat(indexes).contains(
                "idx_jobs_status_created_at",
                "idx_jobs_location",
                "idx_jobs_title_trgm",
                "idx_applications_candidate_status",
                "idx_applications_job_status");
    }

    @Test
    @DisplayName("skill names are rejected by the database unless lower case")
    void skillNameCheckConstraintIsEnforced() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO skills (name) VALUES ('JAVA')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("duplicate skill names are rejected by the database")
    void skillNameUniquenessIsEnforced() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO skills (name) VALUES ('java')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
