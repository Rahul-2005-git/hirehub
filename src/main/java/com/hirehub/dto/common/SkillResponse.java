package com.hirehub.dto.common;

/**
 * A skill from the shared vocabulary. Lives in {@code dto.common} rather than under
 * {@code dto.candidate}: it will be reused as-is once job-required skills (Phase 8/9) need
 * the same shape, and duplicating an identical record in two feature packages would just be
 * two things to keep in sync.
 */
public record SkillResponse(Long id, String name) {
}
