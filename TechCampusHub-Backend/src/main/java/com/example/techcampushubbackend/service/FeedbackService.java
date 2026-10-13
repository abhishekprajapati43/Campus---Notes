package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.EngagementDtos.FeedbackRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.FeedbackResponse;
import com.example.techcampushubbackend.entity.Feedback;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.FeedbackRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Feedback: students submit 1-5 star reviews, admins read them all. */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public FeedbackResponse submit(FeedbackRequest request) {
        User user = currentUserService.requireUser();
        var course = request.courseId() == null ? null
                : courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", request.courseId()));

        Feedback feedback = Feedback.builder()
                .user(user)
                .course(course)
                .rating(request.rating())
                .message(request.message() == null ? "" : request.message().trim())
                .targetType(normalizeTargetType(request.targetType()))
                .targetLabel(normalizeTargetLabel(request.targetLabel(), request.targetType()))
                .build();
        return toResponse(feedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listMine() {
        User user = currentUserService.requireUser();
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(FeedbackService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> listAll() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(FeedbackService::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback", id));
        feedbackRepository.delete(feedback);
    }

    private static String normalizeTargetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "website";
        }
        String trimmed = raw.trim().toLowerCase();
        return switch (trimmed) {
            case "website", "course", "note" -> trimmed;
            default -> "website";
        };
    }

    private static String normalizeTargetLabel(String label, String targetType) {
        String trimmed = label == null ? "" : label.trim();
        if (!trimmed.isEmpty()) {
            return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
        }
        return "website".equalsIgnoreCase(targetType) ? "Website" : "Untitled";
    }

    public static FeedbackResponse toResponse(Feedback f) {
        return new FeedbackResponse(
                f.getId(),
                f.getUser().getId(),
                f.getUser().getName(),
                f.getUser().getEmail(),
                f.getCourse() == null ? null : f.getCourse().getId(),
                f.getCourse() == null ? null : f.getCourse().getTitle(),
                f.getRating(),
                f.getMessage(),
                f.getTargetType() == null ? "website" : f.getTargetType(),
                f.getTargetLabel() == null ? "Website" : f.getTargetLabel(),
                f.getCreatedAt());
    }
}
