package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.EngagementDtos.RatingSummaryResponse;
import com.example.techcampushubbackend.dto.EngagementDtos.ReviewRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.ReviewResponse;
import com.example.techcampushubbackend.entity.Course;
import com.example.techcampushubbackend.entity.Feedback;
import com.example.techcampushubbackend.entity.PurchaseStatus;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ApiException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.FeedbackRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Course ratings & reviews. Only verified purchasers may rate a course, and
 * each purchaser holds at most one review per course (a new submission updates
 * the existing one). Averages are always computed from the stored reviews, so
 * they never drift out of sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final FeedbackRepository feedbackRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;

    /** Platform-wide average across every course review. */
    @Transactional(readOnly = true)
    public RatingSummaryResponse overallSummary() {
        return toSummary(feedbackRepository.findOverallRatingSummary());
    }

    /** Average + count for a single course. */
    @Transactional(readOnly = true)
    public RatingSummaryResponse courseSummary(Long courseId) {
        requireCourse(courseId);
        return toSummary(feedbackRepository.findCourseRatingSummary(courseId));
    }

    /** Reviews left on a course, newest first. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listCourseReviews(Long courseId) {
        requireCourse(courseId);
        return feedbackRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(RatingService::toReviewResponse)
                .toList();
    }

    /**
     * Submit (or update) the caller's review of a course. Purchases that were
     * rejected do not grant the right to review.
     */
    @Transactional
    public ReviewResponse submit(Long courseId, ReviewRequest request) {
        User user = currentUserService.requireUser();
        Course course = requireCourse(courseId);

        if (!feedbackRepository.existsPurchaseWithStatus(
                user.getId(), courseId, PurchaseStatus.APPROVED)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only students who purchased this course can review it");
        }

        // One review per purchaser: a second submission edits the first.
        Feedback review = feedbackRepository
                .findFirstByUserIdAndCourseIdOrderByCreatedAtDesc(user.getId(), courseId)
                .orElseGet(() -> Feedback.builder()
                        .user(user)
                        .course(course)
                        .targetType("course")
                        .targetLabel(course.getTitle())
                        .build());

        review.setRating(request.rating());
        review.setMessage(request.comment() == null ? "" : request.comment().trim());
        review.setTargetType("course");
        review.setTargetLabel(course.getTitle());

        Feedback saved = feedbackRepository.save(review);
        log.info("Course review saved: course={} user={} rating={}",
                courseId, user.getId(), request.rating());
        return toReviewResponse(saved);
    }

    private Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private static RatingSummaryResponse toSummary(List<Object[]> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0)[0] == null) {
            return new RatingSummaryResponse(0.0, 0);
        }
        Object[] row = rows.get(0);
        double average = Math.round((Double) row[0] * 10.0) / 10.0;
        long count = ((Number) row[1]).longValue();
        return new RatingSummaryResponse(average, count);
    }

    public static ReviewResponse toReviewResponse(Feedback f) {
        return new ReviewResponse(
                f.getId(),
                f.getCourse() == null ? null : f.getCourse().getId(),
                f.getCourse() == null ? null : f.getCourse().getTitle(),
                f.getUser().getId(),
                f.getUser().getName(),
                f.getRating(),
                f.getMessage(),
                f.getCreatedAt());
    }
}
