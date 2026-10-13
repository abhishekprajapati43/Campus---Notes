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
                .message(request.message().trim())
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

    public static FeedbackResponse toResponse(Feedback f) {
        return new FeedbackResponse(
                f.getId(),
                f.getUser().getId(),
                f.getUser().getName(),
                f.getCourse() == null ? null : f.getCourse().getId(),
                f.getCourse() == null ? null : f.getCourse().getTitle(),
                f.getRating(),
                f.getMessage(),
                f.getCreatedAt());
    }
}
