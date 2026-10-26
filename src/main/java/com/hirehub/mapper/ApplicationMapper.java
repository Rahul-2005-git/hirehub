package com.hirehub.mapper;

import com.hirehub.dto.application.ApplicationResponse;
import com.hirehub.dto.application.ApplicationStatusHistoryResponse;
import com.hirehub.dto.application.JobSummaryResponse;
import com.hirehub.entity.Application;
import com.hirehub.entity.ApplicationStatusHistory;
import com.hirehub.entity.Job;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApplicationMapper {

    private final UserMapper userMapper;

    public ApplicationMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public ApplicationResponse toResponse(Application application) {
        List<String> candidateSkills = java.util.Collections.emptyList();
        Long candidateProfileId = null;

        if (application.getCandidate() != null) {
            candidateProfileId = application.getCandidate().getId();
            try {
                if (application.getCandidate().getSkills() != null) {
                    candidateSkills = application.getCandidate().getSkills().stream()
                            .map(cs -> cs.getSkill().getName())
                            .toList();
                }
            } catch (Exception ignored) {
                // If lazy initialization or not fetched, gracefully fallback to empty
            }
        }

        return new ApplicationResponse(
                application.getId(),
                toJobSummary(application.getJob()),
                userMapper.toSummary(application.getCandidate().getUser()),
                application.getStatus(),
                application.getCoverLetter(),
                application.getResumeUrl(),
                application.getAppliedAt(),
                application.getUpdatedAt(),
                candidateProfileId,
                candidateSkills
        );
    }

    public ApplicationStatusHistoryResponse toHistoryResponse(ApplicationStatusHistory entry) {
        return new ApplicationStatusHistoryResponse(
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedBy() == null ? null : entry.getChangedBy().getFullName(),
                entry.getNote(),
                entry.getChangedAt()
        );
    }

    private JobSummaryResponse toJobSummary(Job job) {
        return new JobSummaryResponse(job.getId(), job.getTitle(), job.getCompany().getName(), job.getStatus());
    }
}
