package com.hirehub.controller;

import com.hirehub.dto.common.ApiResponse;
import com.hirehub.dto.notification.NotificationDto;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Controller exposing real-time SSE stream and notification history.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Real-time user notification stream")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Subscribe to live Server-Sent Events (SSE) notification stream")
    public SseEmitter subscribe(@AuthenticationPrincipal HireHubUserDetails principal) {
        return notificationService.subscribe(principal.getUserId());
    }

    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get recent notification history for current user")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getRecent(
            @AuthenticationPrincipal HireHubUserDetails principal) {
        var list = notificationService.getRecentNotifications(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.of("Recent notifications", list));
    }
}
