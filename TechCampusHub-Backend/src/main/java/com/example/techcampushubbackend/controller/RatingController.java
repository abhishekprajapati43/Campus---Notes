package com.example.techcampushubbackend.controller;

import com.example.techcampushubbackend.dto.ApiResponse;
import com.example.techcampushubbackend.dto.EngagementDtos.RatingSummaryResponse;
import com.example.techcampushubbackend.dto.EngagementDtos.ReviewRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.ReviewResponse;
import com.example.techcampushubbackend.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public rating summaries (homepage + course cards) and the purchase-gated
 * review endpoints. Review submission requires an authenticated student who
 * actually bought the course; the average is always recomputed from the
 * stored reviews.
 */
@Tag(name = "Ratings & reviews", description = "Course and platform ratings")
@RestController
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @Operation(summary = "Platform-wide average rating (public)")
    @GetMapping("/api/ratings/overall")
    public ResponseEntity<ApiResponse<RatingSummaryResponse>> overall() {
        return ResponseEntity.ok(ApiResponse.ok(ratingService.overallSummary()));
    }

    @Operation(summary = "Average rating for one course (public)")
    @GetMapping("/api/courses/{id}/rating")
    public ResponseEntity<ApiResponse<RatingSummaryResponse>> courseRating(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(ratingService.courseSummary(id)));
    }

    @Operation(summary = "Reviews left on a course (public)")
    @GetMapping("/api/courses/{id}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> courseReviews(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(ratingService.listCourseReviews(id)));
    }

    @Operation(summary = "Submit or update your course review",
            description = "Only verified purchasers may rate; one review per course per student.")
    @PostMapping("/api/courses/{id}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> submit(
            @PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Review saved", ratingService.submit(id, request)));
    }
}
