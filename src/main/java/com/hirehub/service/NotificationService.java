package com.hirehub.service;

import com.hirehub.dto.notification.NotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service managing real-time Server-Sent Events (SSE) connections for instant notifications.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final Map<Long, List<NotificationDto>> userHistory = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        // Send initial connect handshake
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(NotificationDto.of("Connected", "Real-time notification stream active", "SYSTEM", null)));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    public void sendToUser(Long userId, NotificationDto notification) {
        // Record in history (last 20 notifications per user)
        userHistory.compute(userId, (k, list) -> {
            List<NotificationDto> history = list != null ? list : new ArrayList<>();
            history.add(0, notification);
            if (history.size() > 20) {
                return new ArrayList<>(history.subList(0, 20));
            }
            return history;
        });

        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("NOTIFICATION")
                        .data(notification));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }

    public List<NotificationDto> getRecentNotifications(Long userId) {
        List<NotificationDto> list = userHistory.get(userId);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }
}
