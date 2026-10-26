package com.hirehub.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests — no Spring context, no database, milliseconds to run.
 *
 * <p>The transition rules are the part of the domain most likely to be broken by a later
 * change and the cheapest to test, so they are tested exhaustively here rather than
 * incidentally through a service test.</p>
 */
class StatusTransitionTest {

    @Nested
    @DisplayName("JobStatus")
    class JobStatusTest {

        @Test
        @DisplayName("a draft can be published or abandoned")
        void draftTransitions() {
            assertThat(JobStatus.DRAFT.canTransitionTo(JobStatus.PUBLISHED)).isTrue();
            assertThat(JobStatus.DRAFT.canTransitionTo(JobStatus.CLOSED)).isTrue();
            assertThat(JobStatus.DRAFT.canTransitionTo(JobStatus.EXPIRED)).isFalse();
        }

        @Test
        @DisplayName("a closed job can never be reopened")
        void closedIsTerminal() {
            assertThat(JobStatus.CLOSED.isTerminal()).isTrue();
            assertThat(JobStatus.CLOSED.canTransitionTo(JobStatus.PUBLISHED)).isFalse();
            assertThat(JobStatus.CLOSED.canTransitionTo(JobStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("only a published job accepts applications")
        void onlyPublishedIsOpen() {
            assertThat(JobStatus.PUBLISHED.isOpenForApplications()).isTrue();
            assertThat(JobStatus.DRAFT.isOpenForApplications()).isFalse();
            assertThat(JobStatus.EXPIRED.isOpenForApplications()).isFalse();
            assertThat(JobStatus.CLOSED.isOpenForApplications()).isFalse();
        }
    }

    @Nested
    @DisplayName("ApplicationStatus")
    class ApplicationStatusTest {

        @Test
        @DisplayName("the happy path advances one stage at a time")
        void happyPath() {
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.UNDER_REVIEW)).isTrue();
            assertThat(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.SHORTLISTED)).isTrue();
            assertThat(ApplicationStatus.SHORTLISTED.canTransitionTo(ApplicationStatus.INTERVIEW)).isTrue();
            assertThat(ApplicationStatus.INTERVIEW.canTransitionTo(ApplicationStatus.SELECTED)).isTrue();
        }

        @Test
        @DisplayName("stages cannot be skipped")
        void stagesCannotBeSkipped() {
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.SHORTLISTED)).isFalse();
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.SELECTED)).isFalse();
            assertThat(ApplicationStatus.SHORTLISTED.canTransitionTo(ApplicationStatus.SELECTED)).isFalse();
        }

        @Test
        @DisplayName("rejection is reachable from every active stage")
        void rejectionIsAlwaysReachable() {
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.REJECTED)).isTrue();
            assertThat(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.REJECTED)).isTrue();
            assertThat(ApplicationStatus.SHORTLISTED.canTransitionTo(ApplicationStatus.REJECTED)).isTrue();
            assertThat(ApplicationStatus.INTERVIEW.canTransitionTo(ApplicationStatus.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("terminal outcomes cannot be undone")
        void terminalStatesAreFinal() {
            assertThat(ApplicationStatus.SELECTED.isTerminal()).isTrue();
            assertThat(ApplicationStatus.REJECTED.isTerminal()).isTrue();
            assertThat(ApplicationStatus.WITHDRAWN.isTerminal()).isTrue();
            assertThat(ApplicationStatus.REJECTED.canTransitionTo(ApplicationStatus.SHORTLISTED)).isFalse();
            assertThat(ApplicationStatus.WITHDRAWN.canTransitionTo(ApplicationStatus.APPLIED)).isFalse();
        }

        @Test
        @DisplayName("withdrawal belongs to the candidate, not the recruiter")
        void recruitersCannotWithdraw() {
            assertThat(ApplicationStatus.WITHDRAWN.isAssignableByRecruiter()).isFalse();
            assertThat(ApplicationStatus.SHORTLISTED.isAssignableByRecruiter()).isTrue();
            assertThat(ApplicationStatus.REJECTED.isAssignableByRecruiter()).isTrue();
        }
    }
}
