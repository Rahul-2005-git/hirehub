package com.hirehub.service;

import com.hirehub.dto.application.ApplicationResponse;
import com.hirehub.dto.application.ApplyToJobRequest;
import com.hirehub.dto.application.UpdateApplicationStatusRequest;
import com.hirehub.entity.Application;
import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.ApplicationStatus;
import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.exception.DuplicateResourceException;
import com.hirehub.exception.InvalidStateException;
import com.hirehub.exception.ResourceNotFoundException;
import com.hirehub.mapper.ApplicationMapper;
import com.hirehub.repository.ApplicationRepository;
import com.hirehub.repository.CandidateProfileRepository;
import com.hirehub.repository.JobRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.security.HireHubUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code ApplicationService}'s own collaborators are mocked; {@link Application},
 * {@link Job} and the profile entities are real, in-memory objects — the whole point is to
 * exercise {@code Application.changeStatus()} and {@code ApplicationStatus}'s transition
 * rules for real, not to re-stub them. This is the project brief's "Application service"
 * unit-test target, covering exactly the cases it names: successful operations, duplicate
 * application, unauthorized operations, and invalid (job) status.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private CandidateProfileRepository candidateProfileRepository;
    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ApplicationService applicationService;

    private static final long CANDIDATE_USER_ID = 1L;
    private static final long RECRUITER_USER_ID = 2L;
    private static final long JOB_ID = 10L;

    private Company company;
    private RecruiterProfile recruiter;
    private CandidateProfile candidate;
    private Job job;

    @BeforeEach
    void setUp() throws Exception {
        company = new Company("Acme Corp");
        User recruiterUser = new User("recruiter@hirehub.test", "hash", "Test Recruiter", new Role(RoleName.RECRUITER));
        recruiter = new RecruiterProfile(recruiterUser);
        recruiter.setCompany(company);

        User candidateUser = new User("candidate@hirehub.test", "hash", "Test Candidate", new Role(RoleName.CANDIDATE));
        candidate = new CandidateProfile(candidateUser);
        candidate.setHeadline("Backend Engineer");
        candidate.setResumeUrl("https://files.example.test/cv.pdf");
        setId(candidate, 100L);

        job = new Job(company, recruiter, "Engineer", "Build things", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.MID_LEVEL);
        job.publish();
        setId(job, JOB_ID);

        lenient().when(applicationMapper.toResponse(any(Application.class)))
                .thenReturn(new ApplicationResponse(1L, null, null, null, null, null, null, null));
    }

    /**
     * Entity ids are normally assigned by the database on insert; set directly via
     * reflection here since these objects are never persisted. {@code id} lives on
     * {@code BaseEntity}, so the search walks up the hierarchy to find it.
     */
    private void setId(Object entity, Long id) throws Exception {
        Class<?> type = entity.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        field.setAccessible(true);
        field.set(entity, id);
    }

    private HireHubUserDetails candidateViewer() {
        return viewer(candidate.getUser());
    }

    private HireHubUserDetails recruiterViewer() {
        return viewer(recruiter.getUser());
    }

    private HireHubUserDetails viewer(User user) {
        RoleName roleName = user.getRole().getName();
        return new HireHubUserDetails(user) {
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of(new SimpleGrantedAuthority("ROLE_" + roleName.name()));
            }
        };
    }

    @Nested
    @DisplayName("apply")
    class Apply {

        @Test
        @DisplayName("a complete, eligible candidate applies successfully")
        void appliesSuccessfully() {
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(candidate));
            when(jobRepository.findWithDetailsById(JOB_ID)).thenReturn(Optional.of(job));
            when(applicationRepository.existsByJobIdAndCandidateId(JOB_ID, candidate.getId())).thenReturn(false);

            applicationService.apply(CANDIDATE_USER_ID, JOB_ID, new ApplyToJobRequest("Cover letter"));

            verify(applicationRepository).save(any(Application.class));
        }

        @Test
        @DisplayName("an incomplete profile is rejected before the job is even looked up")
        void incompleteProfileRejected() {
            CandidateProfile incomplete = new CandidateProfile(new User("x@hirehub.test", "h", "X", new Role(RoleName.CANDIDATE)));
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(incomplete));

            assertThatThrownBy(() -> applicationService.apply(CANDIDATE_USER_ID, JOB_ID, null))
                    .isInstanceOf(InvalidStateException.class);

            verify(jobRepository, never()).findWithDetailsById(any());
        }

        @Test
        @DisplayName("applying to the same job twice is a DuplicateResourceException, not a silent success")
        void duplicateApplicationRejected() {
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(candidate));
            when(jobRepository.findWithDetailsById(JOB_ID)).thenReturn(Optional.of(job));
            when(applicationRepository.existsByJobIdAndCandidateId(JOB_ID, candidate.getId())).thenReturn(true);

            assertThatThrownBy(() -> applicationService.apply(CANDIDATE_USER_ID, JOB_ID, null))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(applicationRepository, never()).save(any());
        }

        @Test
        @DisplayName("applying to a closed job is rejected")
        void closedJobRejected() {
            job.close();
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(candidate));
            when(jobRepository.findWithDetailsById(JOB_ID)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> applicationService.apply(CANDIDATE_USER_ID, JOB_ID, null))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("applying to a job that does not exist is a clean 404, not a NullPointerException")
        void missingJobIsNotFound() {
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(candidate));
            when(jobRepository.findWithDetailsById(JOB_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> applicationService.apply(CANDIDATE_USER_ID, JOB_ID, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        private Application applicationFor(Job theJob, CandidateProfile theCandidate) throws Exception {
            Application application = new Application(theJob, theCandidate, null);
            application.recordInitialStatus(theCandidate.getUser());
            setId(application, 500L);
            return application;
        }

        @Test
        @DisplayName("a recruiter from the job's company moves the pipeline forward successfully")
        void recruiterMovesStatusForward() throws Exception {
            Application application = applicationFor(job, candidate);
            when(recruiterProfileRepository.findByUserId(RECRUITER_USER_ID)).thenReturn(Optional.of(recruiter));
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));

            applicationService.updateStatus(RECRUITER_USER_ID, 500L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, "note"));

            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        }

        @Test
        @DisplayName("a recruiter cannot set WITHDRAWN - business rule, not just a transition check")
        void recruiterCannotSetWithdrawn() throws Exception {
            Application application = applicationFor(job, candidate);
            when(recruiterProfileRepository.findByUserId(RECRUITER_USER_ID)).thenReturn(Optional.of(recruiter));
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> applicationService.updateStatus(RECRUITER_USER_ID, 500L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.WITHDRAWN, null)))
                    .isInstanceOf(InvalidStateException.class);

            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        }

        @Test
        @DisplayName("skipping a pipeline stage is rejected by the state machine")
        void skippingStageRejected() throws Exception {
            Application application = applicationFor(job, candidate);
            when(recruiterProfileRepository.findByUserId(RECRUITER_USER_ID)).thenReturn(Optional.of(recruiter));
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> applicationService.updateStatus(RECRUITER_USER_ID, 500L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.SHORTLISTED, null)))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("a recruiter from a different company cannot touch this application - unauthorized operation")
        void differentCompanyRecruiterForbidden() throws Exception {
            Application application = applicationFor(job, candidate);
            RecruiterProfile otherRecruiter = new RecruiterProfile(
                    new User("other@hirehub.test", "h", "Other", new Role(RoleName.RECRUITER)));
            otherRecruiter.setCompany(new Company("Other Co"));
            when(recruiterProfileRepository.findByUserId(RECRUITER_USER_ID)).thenReturn(Optional.of(otherRecruiter));
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> applicationService.updateStatus(RECRUITER_USER_ID, 500L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.UNDER_REVIEW, null)))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("the owning candidate can withdraw")
        void ownerCanWithdraw() throws Exception {
            Application application = new Application(job, candidate, null);
            application.recordInitialStatus(candidate.getUser());
            setId(application, 500L);
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(candidate));
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));

            applicationService.withdraw(CANDIDATE_USER_ID, 500L);

            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("a different candidate cannot withdraw someone else's application - unauthorized operation")
        void differentCandidateForbidden() throws Exception {
            Application application = new Application(job, candidate, null);
            application.recordInitialStatus(candidate.getUser());
            setId(application, 500L);

            CandidateProfile otherCandidate = new CandidateProfile(
                    new User("other-candidate@hirehub.test", "h", "Other", new Role(RoleName.CANDIDATE)));
            setId(otherCandidate, 200L);
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(otherCandidate));
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> applicationService.withdraw(CANDIDATE_USER_ID, 500L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("a terminal application cannot be withdrawn again")
        void terminalApplicationCannotBeWithdrawn() throws Exception {
            Application application = new Application(job, candidate, null);
            application.recordInitialStatus(candidate.getUser());
            application.changeStatus(ApplicationStatus.REJECTED, recruiter.getUser(), null);
            setId(application, 500L);
            when(candidateProfileRepository.findByUserId(CANDIDATE_USER_ID)).thenReturn(Optional.of(candidate));
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));

            assertThatThrownBy(() -> applicationService.withdraw(CANDIDATE_USER_ID, 500L))
                    .isInstanceOf(InvalidStateException.class);
        }
    }

    @Nested
    @DisplayName("getById - view access")
    class GetById {

        @Test
        @DisplayName("the owning candidate can view their application")
        void ownerCandidateCanView() throws Exception {
            Application application = new Application(job, candidate, null);
            application.recordInitialStatus(candidate.getUser());
            setId(application, 500L);
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));
            when(candidateProfileRepository.findByUserId(any())).thenReturn(Optional.of(candidate));

            applicationService.getById(candidateViewer(), 500L);

            verify(applicationMapper).toResponse(application);
        }

        @Test
        @DisplayName("an unrelated candidate cannot view someone else's application")
        void unrelatedCandidateForbidden() throws Exception {
            Application application = new Application(job, candidate, null);
            application.recordInitialStatus(candidate.getUser());
            setId(application, 500L);
            CandidateProfile other = new CandidateProfile(new User("o@hirehub.test", "h", "O", new Role(RoleName.CANDIDATE)));
            setId(other, 999L);
            when(applicationRepository.findWithHistoryById(500L)).thenReturn(Optional.of(application));
            when(candidateProfileRepository.findByUserId(any())).thenReturn(Optional.of(other));
            when(recruiterProfileRepository.findByUserId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> applicationService.getById(viewer(other.getUser()), 500L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
