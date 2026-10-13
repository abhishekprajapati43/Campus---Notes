package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.EngagementDtos.NotificationRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.NotificationResponse;
import com.example.techcampushubbackend.entity.Notification;
import com.example.techcampushubbackend.entity.Notification.Kind;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.NotificationRepository;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Notifications: admins send broadcasts (no user id) or direct messages
 * (user id set); students receive broadcasts plus their own direct messages.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine() {
        User user = currentUserService.requireUser();
        return notificationRepository.findVisibleTo(user.getId()).stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listAll() {
        return notificationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        User recipient = null;
        if (request.userId() != null) {
            recipient = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        }
        Notification notification = Notification.builder()
                .title(request.title().trim())
                .message(request.message())
                .kind(parseKind(request.kind()))
                .targetUser(recipient)
                .build();
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public void delete(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notificationRepository.delete(notification);
    }

    private static Kind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return Kind.INFO;
        }
        try {
            return Kind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid kind: " + raw
                    + " (expected INFO, UPDATE, OFFER or ALERT)");
        }
    }

    public static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getTitle(), n.getMessage(), n.getKind().name(),
                n.getTargetUser() == null ? null : n.getTargetUser().getId(),
                n.getTargetUser() == null ? null : n.getTargetUser().getName(),
                n.getTargetUser() == null,
                n.getCreatedAt());
    }
}
