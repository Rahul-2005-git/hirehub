package com.hirehub.service;

import com.hirehub.dto.candidate.AddCandidateSkillRequest;
import com.hirehub.dto.candidate.CandidateProfileResponse;
import com.hirehub.dto.candidate.CandidateSkillResponse;
import com.hirehub.dto.candidate.UpdateCandidateProfileRequest;
import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.CandidateSkill;
import com.hirehub.entity.Skill;
import com.hirehub.exception.DuplicateResourceException;
import com.hirehub.exception.ResourceNotFoundException;
import com.hirehub.mapper.CandidateProfileMapper;
import com.hirehub.repository.CandidateProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * All "my profile" methods take a {@code userId}, never a profile id supplied by the
 * client. This is the same structural-prevention pattern used for
 * {@code RegistrableRole} in Phase 4B, applied to ownership instead of role: there is no
 * request parameter a candidate could tamper with to reach someone else's profile through
 * these methods, because the profile is always looked up from the authenticated principal,
 * not from anything the client sent. {@link #getById} is the one method that does take a
 * caller-supplied id — it is deliberately open to any recruiter or admin (see
 * {@code CandidateProfileController}), matching the "recruiters can view candidate
 * profiles" requirement, so there is no ownership check to bypass there in the first place.
 */
import org.springframework.web.multipart.MultipartFile;

@Service
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final SkillService skillService;
    private final CandidateProfileMapper mapper;
    private final FileStorageService fileStorageService;

    public CandidateProfileService(CandidateProfileRepository candidateProfileRepository,
                                    SkillService skillService, CandidateProfileMapper mapper,
                                    FileStorageService fileStorageService) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.skillService = skillService;
        this.mapper = mapper;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getMyProfile(Long userId) {
        return mapper.toResponse(loadByUserId(userId));
    }

    @Transactional(readOnly = true)
    public List<CandidateSkillResponse> getMySkills(Long userId) {
        return loadByUserId(userId).getSkills().stream()
                .sorted((a, b) -> a.getSkill().getName().compareTo(b.getSkill().getName()))
                .map(mapper::toSkillResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getById(Long candidateProfileId) {
        CandidateProfile profile = candidateProfileRepository.findByIdWithSkills(candidateProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found: " + candidateProfileId));
        return mapper.toResponse(profile);
    }

    @Transactional
    public CandidateProfileResponse updateMyProfile(Long userId, UpdateCandidateProfileRequest request) {
        // findByUserIdWithSkills, not the plain findByUserId: the response includes skills,
        // and loading them now avoids a second query when the mapper reads them below.
        CandidateProfile profile = candidateProfileRepository.findByUserIdWithSkills(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found for the current user"));

        profile.setHeadline(blankToNull(request.headline()));
        profile.setSummary(blankToNull(request.summary()));
        // Unlike every other field here, this is NOT unconditionally overwritten with the
        // request value: experience_years is NOT NULL at the database level (V1__init_schema.sql),
        // so setting it to null on an omitted field would fail at flush with a constraint
        // violation instead of the graceful "field just wasn't updated" the client expects.
        // Every other field on this entity is nullable, so full-replace is safe for them.
        if (request.experienceYears() != null) {
            profile.setExperienceYears(request.experienceYears());
        }
        profile.setCurrentLocation(blankToNull(request.currentLocation()));
        profile.setExpectedSalary(request.expectedSalary());
        profile.setResumeUrl(blankToNull(request.resumeUrl()));
        profile.setResumeFileName(blankToNull(request.resumeFileName()));
        profile.setLinkedinUrl(blankToNull(request.linkedinUrl()));
        profile.setGithubUrl(blankToNull(request.githubUrl()));

        // No explicit save() call: `profile` is a managed entity loaded in this same
        // transaction, so Hibernate's dirty checking flushes the changes on commit. Calling
        // save() here would be a no-op that only obscures that this is how JPA already works.
        return mapper.toResponse(profile);
    }

    @Transactional
    public CandidateProfileResponse addSkill(Long userId, AddCandidateSkillRequest request) {
        CandidateProfile profile = candidateProfileRepository.findByUserIdWithSkills(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found for the current user"));

        Skill skill = skillService.findOrCreateSkill(request.skillName());

        boolean alreadyHasSkill = profile.getSkills().stream()
                .anyMatch(candidateSkill -> candidateSkill.getSkill().getId().equals(skill.getId()));
        if (alreadyHasSkill) {
            // The database's composite PK on candidate_skills backs this up under a race,
            // same two-layer pattern as everywhere else in the app: this check gives the
            // friendly message, the constraint is what actually holds if two requests to
            // add the same skill land at once.
            throw new DuplicateResourceException("You have already added this skill to your profile");
        }

        profile.addSkill(new CandidateSkill(skill, request.proficiency(), request.yearsOfExperience()));

        return mapper.toResponse(profile);
    }

    @Transactional
    public void removeSkill(Long userId, Long skillId) {
        CandidateProfile profile = candidateProfileRepository.findByUserIdWithSkills(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found for the current user"));

        CandidateSkill match = profile.getSkills().stream()
                .filter(candidateSkill -> candidateSkill.getSkill().getId().equals(skillId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("This skill is not on your profile"));

        // orphanRemoval = true on CandidateProfile.skills (Phase 2B) means removing it from
        // the in-memory Set is what deletes the row - no separate repository.delete() call.
        profile.removeSkill(match);
    }

    @Transactional
    public CandidateProfileResponse uploadResume(Long userId, MultipartFile file) {
        CandidateProfile profile = loadByUserId(userId);

        // Delete existing uploaded resume if stored locally
        if (profile.getResumeUrl() != null && profile.getResumeUrl().startsWith("/api/resumes/")) {
            String oldFile = profile.getResumeUrl().substring("/api/resumes/".length());
            fileStorageService.deleteResume(oldFile);
        }

        String storedFileName = fileStorageService.storeResume(file);
        String resumeUrl = "/api/resumes/" + storedFileName;
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : storedFileName;

        profile.setResumeUrl(resumeUrl);
        profile.setResumeFileName(originalName);

        return mapper.toResponse(profile);
    }

    @Transactional
    public CandidateProfileResponse deleteResume(Long userId) {
        CandidateProfile profile = loadByUserId(userId);

        if (profile.getResumeUrl() != null && profile.getResumeUrl().startsWith("/api/resumes/")) {
            String oldFile = profile.getResumeUrl().substring("/api/resumes/".length());
            fileStorageService.deleteResume(oldFile);
        }

        profile.setResumeUrl(null);
        profile.setResumeFileName(null);

        return mapper.toResponse(profile);
    }

    private CandidateProfile loadByUserId(Long userId) {
        return candidateProfileRepository.findByUserIdWithSkills(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found for the current user"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
