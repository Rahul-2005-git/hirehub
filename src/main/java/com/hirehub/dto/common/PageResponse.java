package com.hirehub.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standard paged-response shape — see API.md's paging convention. Spring Data's own
 * {@code Page<T>} is never returned directly from a controller: it serializes with extra
 * Spring-internal fields (`pageable`, `sort`, etc.) that leak implementation detail into the
 * API contract and would change if the pagination library ever did. This is the stable,
 * intentional shape instead.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    /** For a page whose content has already been mapped to a DTO, e.g. entity → response. */
    public static <T> PageResponse<T> of(Page<?> page, List<T> mappedContent) {
        return new PageResponse<>(
                mappedContent,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
