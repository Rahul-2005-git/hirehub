package com.hirehub.dto.notification;

import java.time.Instant;

public record NotificationDto(
        String id,
        String title,
        String message,
        String type, // "STATUS_CHANGE", "NEW_APPLICATION", "COMPANY_APPROVAL"
        Instant timestamp,
        Long targetId // e.g. applicationId, jobId
) {
    public static NotificationDto of(String title, String message, String type, Long targetId) {
        return new NotificationDto(java.util.UUID.randomUUID().toString(), title, message, type, Instant.now(), targetId);
    }
}
