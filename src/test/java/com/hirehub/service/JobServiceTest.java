package com.hirehub.service;

import com.hirehub.dto.job.CreateJobRequest;
import com.hirehub.dto.job.JobResponse;
import com.hirehub.dto.job.JobSearchCriteria;
import com.hirehub.dto.job.UpdateJobRequest;
import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.CompanyStatus;
import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.entity.enums.JobStatus;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.exception.InvalidStateException;
import com.hirehub.exception.ResourceNotFoundException;
import com.hirehub.mapper.JobMapper;
import com.hirehub.repository.JobRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.security.HireHubUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
 * {@code JobService}'s own collaborators are mocked; {@link Job}, {@link Company} and
 * {@link RecruiterProfile} are real, in-memory entities — mocking domain objects that carry
 * real behaviour ({@code Job.publish()}, the {@code JobStatus} state machine) would mean
 * re-stubbing the very logic under test. This is the project brief's "Job service" unit-test
 * target: the company-level ownership model and the full status lifecycle, in isolation from
 * Spring and the database.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;
    @Mock
    private SkillService skillService;
    @Mock
    private JobMapper jobMapper;

    @InjectMocks
    private JobService jobService;

    private static final long USER_ID = 1L;
    private Company company;
    private RecruiterProfile recruiter;

    @BeforeEach
    void setUp() {
        Role role = new Role(RoleName.RECRUITER);
        User user = new User("recruiter@hirehub.test", "hash", "Test Recruiter", role);
        company = new Company("Acme Corp");
        company.setStatus(CompanyStatus.APPROVED);
        recruiter = new RecruiterProfile(user);
        recruiter.setCompany(company);

        lenient().when(jobMapper.toResponse(any(Job.class))).thenReturn(
                new JobResponse(1L, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null));
    }

    private Job newJob() {
        return new Job(company, recruiter, "Backend Engineer", "Build things", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL);
    }

    private CreateJobRequest createRequest() {
        return new CreateJobRequest("Backend Engineer", "Build things", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL, null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("createJob")
    class CreateJob {

        @Test
        @DisplayName("a recruiter with no company cannot create a job")
        void recruiterWithNoCompanyCannotCreate() {
            RecruiterProfile noCompany = new RecruiterProfile(recruiter.getUser());
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(noCompany));

            assertThatThrownBy(() -> jobService.createJob(USER_ID, createRequest()))
                    .isInstanceOf(InvalidStateException.class);

            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("a recruiter with no profile at all gets a clear not-found, not a NullPointerException")
        void missingRecruiterProfileIsNotFound() {
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> jobService.createJob(USER_ID, createRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("a valid request creates the job attached to the recruiter's own company")
        void createsJobForOwnCompany() {
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));

            jobService.createJob(USER_ID, createRequest());

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getCompany()).isEqualTo(company);
            assertThat(captor.getValue().getTitle()).isEqualTo("Backend Engineer");
        }
    }

    @Nested
    @DisplayName("publishJob")
    class PublishJob {

        @Test
        @DisplayName("cannot publish while the company is still PENDING")
        void cannotPublishUnapprovedCompany() {
            company.setStatus(CompanyStatus.PENDING);
            Job job = newJob();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.publishJob(USER_ID, 1L))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("approved");
        }

        @Test
        @DisplayName("publishing an already-published job is rejected by the state machine")
        void cannotPublishTwice() {
            Job job = newJob();
            job.publish();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.publishJob(USER_ID, 1L)).isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("a recruiter from a different company cannot publish this job")
        void nonOwnerCannotPublish() {
            Job job = newJob();
            RecruiterProfile otherRecruiter = new RecruiterProfile(new User("other@hirehub.test", "h", "Other",
                    new Role(RoleName.RECRUITER)));
            otherRecruiter.setCompany(new Company("Other Co"));
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(otherRecruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.publishJob(USER_ID, 1L)).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("a valid publish succeeds and the job's status becomes PUBLISHED")
        void publishSucceeds() {
            Job job = newJob();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            jobService.publishJob(USER_ID, 1L);

            assertThat(job.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        }
    }

    @Nested
    @DisplayName("updateJob")
    class UpdateJob {

        private UpdateJobRequest updateRequest(String title) {
            return new UpdateJobRequest(title, "d2", "Mumbai", EmploymentType.PART_TIME,
                    ExperienceLevel.ENTRY_LEVEL, null, null, null, null, null, null);
        }

        @Test
        @DisplayName("cannot edit a CLOSED job")
        void cannotEditClosedJob() {
            Job job = newJob();
            job.publish();
            job.close();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.updateJob(USER_ID, 1L, updateRequest("New Title")))
                    .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("can edit a DRAFT job, and the new values are applied")
        void canEditDraftJob() {
            Job job = newJob();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            jobService.updateJob(USER_ID, 1L, updateRequest("New Title"));

            assertThat(job.getTitle()).isEqualTo("New Title");
            assertThat(job.getLocation()).isEqualTo("Mumbai");
        }

        @Test
        @DisplayName("can edit a PUBLISHED job")
        void canEditPublishedJob() {
            Job job = newJob();
            job.publish();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            jobService.updateJob(USER_ID, 1L, updateRequest("New Title"));

            assertThat(job.getTitle()).isEqualTo("New Title");
        }
    }

    @Nested
    @DisplayName("deleteJob")
    class DeleteJob {

        @Test
        @DisplayName("a DRAFT job can be deleted")
        void draftJobCanBeDeleted() {
            Job job = newJob();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            jobService.deleteJob(USER_ID, 1L);

            verify(jobRepository).deleteById(job.getId());
        }

        @Test
        @DisplayName("a PUBLISHED job cannot be deleted")
        void publishedJobCannotBeDeleted() {
            Job job = newJob();
            job.publish();
            when(recruiterProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(recruiter));
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.deleteJob(USER_ID, 1L)).isInstanceOf(InvalidStateException.class);
            verify(jobRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("getById - DRAFT visibility")
    class GetByIdVisibility {

        @Test
        @DisplayName("an anonymous viewer (null principal) cannot see a DRAFT job")
        void anonymousCannotSeeDraft() {
            Job job = newJob();
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> jobService.getById(1L, null)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("a recruiter from a different company cannot see a DRAFT job")
        void otherCompanyRecruiterCannotSeeDraft() {
            Job job = newJob();
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));
            HireHubUserDetails viewer = viewerWithRole(RoleName.RECRUITER);
            RecruiterProfile otherRecruiter = new RecruiterProfile(viewer.getUser());
            otherRecruiter.setCompany(new Company("Other Co"));
            when(recruiterProfileRepository.findByUserId(any())).thenReturn(Optional.of(otherRecruiter));

            assertThatThrownBy(() -> jobService.getById(1L, viewer)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("an admin can see a DRAFT job")
        void adminCanSeeDraft() {
            Job job = newJob();
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));
            HireHubUserDetails admin = viewerWithRole(RoleName.ADMIN);

            jobService.getById(1L, admin);

            verify(jobMapper).toResponse(job);
        }

        @Test
        @DisplayName("a published job is visible to anyone, including anonymous")
        void publishedJobIsAlwaysVisible() {
            Job job = newJob();
            job.publish();
            when(jobRepository.findWithDetailsById(1L)).thenReturn(Optional.of(job));

            jobService.getById(1L, null);

            verify(jobMapper).toResponse(job);
        }

        private HireHubUserDetails viewerWithRole(RoleName roleName) {
            User user = new User("viewer@hirehub.test", "hash", "Viewer", new Role(roleName));
            return new HireHubUserDetails(user) {
                @Override
                public Collection<? extends GrantedAuthority> getAuthorities() {
                    return List.of(new SimpleGrantedAuthority("ROLE_" + roleName.name()));
                }
            };
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("an invalid sort field is silently dropped rather than reaching the repository")
        void invalidSortFieldIsDropped() {
            when(jobRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            Pageable pageable = PageRequest.of(0, 10, Sort.by("thisFieldDoesNotExist"));
            jobService.search(new JobSearchCriteria(null, null, null, null, null, null, null), pageable);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(jobRepository).findAll(any(Specification.class), captor.capture());
            // Falls back to the default sort since the only requested field was stripped.
            assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
            assertThat(captor.getValue().getSort().getOrderFor("thisFieldDoesNotExist")).isNull();
        }
    }
}
