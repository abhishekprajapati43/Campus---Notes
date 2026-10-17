package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.CourseDtos.CourseRequest;
import com.example.techcampushubbackend.dto.CourseDtos.CourseResponse;
import com.example.techcampushubbackend.entity.Course;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.FeedbackRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Course catalog: public browsing plus admin create/edit/delete. Each
 * response carries the live average rating / review count of the course.
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final FeedbackRepository feedbackRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<CourseResponse> listPublic() {
        return withRatings(courseRepository.findAllByActiveTrueOrderBySortOrderAscTitleAsc());
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listAll() {
        return withRatings(courseRepository.findAllByOrderBySortOrderAscTitleAsc());
    }

    /**
     * Faculty workspace listing: only the courses the caller launched
     * (admins see every course, mirroring the admin list).
     */
    @Transactional(readOnly = true)
    public List<CourseResponse> listOwn() {
        User user = currentUserService.requireUser();
        return withRatings(courseRepository.findAllByOrderBySortOrderAscTitleAsc().stream()
                .filter(course -> user.getRole() == Role.ADMIN
                        || (course.getCreatedBy() != null
                            && course.getCreatedBy().equals(user.getId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public CourseResponse get(Long id) {
        return toResponse(require(id), ratingFor(id));
    }

    /**
     * Ownership-checked lookup used by the faculty "Course" detail view: a
     * faculty member may only open a course they launched (admins may open any).
     * Non-owners get Spring's AccessDeniedException -> 403; missing ids -> 404.
     */
    @Transactional(readOnly = true)
    public CourseResponse getOwned(Long id) {
        User user = currentUserService.requireUser();
        Course course = require(id);
        if (user.getRole() != Role.ADMIN
                && (course.getCreatedBy() == null
                    || !course.getCreatedBy().equals(user.getId()))) {
            throw new AccessDeniedException("You can only view content for your own courses");
        }
        return toResponse(course, ratingFor(id));
    }

    /** One batched query for the catalog's rating badges (no N+1). */
    private List<CourseResponse> withRatings(List<Course> courses) {
        Map<Long, double[]> ratings = new HashMap<>();
        for (Object[] row : feedbackRepository.findAllCourseRatingSummaries()) {
            ratings.put((Long) row[0],
                    new double[]{(Double) row[1], ((Number) row[2]).doubleValue()});
        }
        return courses.stream()
                .map(course -> {
                    double[] rating = ratings.get(course.getId());
                    return toResponse(course, rating);
                })
                .toList();
    }

    /** Rating summary for a single course: [average, count]. */
    private double[] ratingFor(Long courseId) {
        List<Object[]> rows = feedbackRepository.findCourseRatingSummary(courseId);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0)[0] == null) {
            return new double[]{0.0, 0};
        }
        Object[] row = rows.get(0);
        return new double[]{(Double) row[0], ((Number) row[1]).longValue()};
    }

    /** Loads a course entity or throws 404 - used by other services. */
    @Transactional(readOnly = true)
    public Course require(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    @Transactional
    public CourseResponse create(CourseRequest request) {
        return doCreate(request, null);
    }

    /**
     * Faculty/admin create that stamps the uploader so the faculty workspace can
     * list the courses a staff member launched. The plain admin path leaves
     * {@code createdBy} untouched.
     */
    @Transactional
    public CourseResponse createAsOwner(CourseRequest request, Long ownerId) {
        return doCreate(request, ownerId);
    }

    private CourseResponse doCreate(CourseRequest request, Long ownerId) {
        Course course = Course.builder()
                .title(request.title().trim())
                .code(trimToNull(request.code()))
                .category(trimToNull(request.category()))
                .description(request.description())
                .priceInr(request.priceInr() == null ? BigDecimal.ZERO : request.priceInr())
                .mrpInr(request.mrpInr())
                .thumbnailUrl(trimToNull(request.thumbnailUrl()))
                .validityDays(request.validityDays() == null ? 365 : request.validityDays())
                .slug(uniqueSlug(request.slug(), request.title(), null))
                .highlights(joinHighlights(request.highlights()))
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .active(request.active() == null || request.active())
                .createdBy(ownerId)
                .build();
        Course saved = courseRepository.save(course);
        return toResponse(saved, ratingFor(saved.getId()));
    }

    @Transactional
    public CourseResponse update(Long id, CourseRequest request) {
        Course course = require(id);
        return doUpdate(course, request);
    }

    /**
     * Faculty workspace update: a faculty member may only edit (and therefore
     * publish/unpublish via {@code active}) a course they launched; admins may
     * edit any course. Non-owners get Spring's AccessDeniedException -> 403.
     */
    @Transactional
    public CourseResponse updateOwned(Long id, CourseRequest request) {
        User user = currentUserService.requireUser();
        Course course = require(id);
        if (user.getRole() != Role.ADMIN
                && (course.getCreatedBy() == null
                    || !course.getCreatedBy().equals(user.getId()))) {
            throw new AccessDeniedException("You can only edit courses you launched");
        }
        return doUpdate(course, request);
    }

    private CourseResponse doUpdate(Course course, CourseRequest request) {
        course.setTitle(request.title().trim());
        if (request.code() != null) course.setCode(trimToNull(request.code()));
        if (request.category() != null) course.setCategory(trimToNull(request.category()));
        if (request.description() != null) course.setDescription(request.description());
        if (request.priceInr() != null) course.setPriceInr(request.priceInr());
        // Always applied (unlike the other optional fields): sending an explicit
        // mrpInr of null clears a previous discount so the card stops showing one.
        course.setMrpInr(request.mrpInr());
        if (request.thumbnailUrl() != null) course.setThumbnailUrl(trimToNull(request.thumbnailUrl()));
        if (request.validityDays() != null) course.setValidityDays(request.validityDays());
        if (request.slug() != null) course.setSlug(uniqueSlug(request.slug(), course.getTitle(), course.getId()));
        if (request.highlights() != null) course.setHighlights(joinHighlights(request.highlights()));
        if (request.sortOrder() != null) course.setSortOrder(request.sortOrder());
        if (request.active() != null) course.setActive(request.active());
        Course saved = courseRepository.save(course);
        return toResponse(saved, ratingFor(saved.getId()));
    }

    /**
     * Ownership-checked bundle lookup for content uploads: faculty may only
     * attach notes/TNP/PYQs/timetables/resources to <b>their own</b> courses;
     * admins may attach to any course. Missing ids -> 404, foreign ids -> 403.
     */
    @Transactional(readOnly = true)
    public Course requireOwnedBundle(Long courseId) {
        if (courseId == null) {
            return null;
        }
        Course course = require(courseId);
        User user = currentUserService.requireUser();
        if (user.getRole() != Role.ADMIN
                && (course.getCreatedBy() == null
                    || !course.getCreatedBy().equals(user.getId()))) {
            throw new AccessDeniedException("You can only attach content to your own courses");
        }
        return course;
    }

    /** Soft delete: the course is deactivated so existing purchases keep working. */
    @Transactional
    public void delete(Long id) {
        Course course = require(id);
        course.setActive(false);
        courseRepository.save(course);
    }

    /** @param rating optional [average, count] pair; null means unrated. */
    public static CourseResponse toResponse(Course course, double[] rating) {
        double average = rating == null ? 0.0 : Math.round(rating[0] * 10.0) / 10.0;
        long count = rating == null ? 0 : (long) rating[1];
        return new CourseResponse(
                course.getId(), course.getTitle(), course.getCode(), course.getCategory(),
                course.getDescription(), course.getPriceInr(), course.getMrpInr(),
                course.getThumbnailUrl(),
                course.getValidityDays(), course.getSlug(), splitHighlights(course.getHighlights()),
                course.getSortOrder(), course.isActive(), average, count, course.getCreatedAt());
    }

    /** Highlights round-trip as newline-separated text in the database. */
    private static String joinHighlights(List<String> highlights) {
        if (highlights == null) {
            return null;
        }
        return highlights.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static List<String> splitHighlights(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(stored.split("\n"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String slugify(String value) {
        String slug = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.length() <= 80 ? slug : slug.substring(0, 80);
    }

    /** Generates a slug from the title when none is supplied and keeps it unique. */
    private String uniqueSlug(String requested, String title, Long excludeId) {
        String base = slugify(requested == null || requested.isBlank() ? title : requested);
        if (base.isEmpty()) {
            base = "course";
        }
        String candidate = base;
        int counter = 2;
        while (courseRepository.findBySlugIgnoreCase(candidate)
                .filter(existing -> !existing.getId().equals(excludeId))
                .isPresent()) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }
}
