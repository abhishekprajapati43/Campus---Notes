package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.CourseDtos.CourseRequest;
import com.example.techcampushubbackend.dto.CourseDtos.CourseResponse;
import com.example.techcampushubbackend.entity.Course;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Course catalog: public browsing plus admin create/edit/delete.
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<CourseResponse> listPublic() {
        return courseRepository.findAllByActiveTrueOrderBySortOrderAscTitleAsc().stream()
                .map(CourseService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listAll() {
        return courseRepository.findAllByOrderBySortOrderAscTitleAsc().stream()
                .map(CourseService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(Long id) {
        return toResponse(require(id));
    }

    /** Loads a course entity or throws 404 - used by other services. */
    @Transactional(readOnly = true)
    public Course require(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    @Transactional
    public CourseResponse create(CourseRequest request) {
        Course course = Course.builder()
                .title(request.title().trim())
                .code(trimToNull(request.code()))
                .category(trimToNull(request.category()))
                .description(request.description())
                .priceInr(request.priceInr() == null ? BigDecimal.ZERO : request.priceInr())
                .thumbnailUrl(trimToNull(request.thumbnailUrl()))
                .validityDays(request.validityDays() == null ? 365 : request.validityDays())
                .slug(uniqueSlug(request.slug(), request.title(), null))
                .highlights(joinHighlights(request.highlights()))
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .active(request.active() == null || request.active())
                .build();
        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse update(Long id, CourseRequest request) {
        Course course = require(id);
        course.setTitle(request.title().trim());
        if (request.code() != null) course.setCode(trimToNull(request.code()));
        if (request.category() != null) course.setCategory(trimToNull(request.category()));
        if (request.description() != null) course.setDescription(request.description());
        if (request.priceInr() != null) course.setPriceInr(request.priceInr());
        if (request.thumbnailUrl() != null) course.setThumbnailUrl(trimToNull(request.thumbnailUrl()));
        if (request.validityDays() != null) course.setValidityDays(request.validityDays());
        if (request.slug() != null) course.setSlug(uniqueSlug(request.slug(), course.getTitle(), course.getId()));
        if (request.highlights() != null) course.setHighlights(joinHighlights(request.highlights()));
        if (request.sortOrder() != null) course.setSortOrder(request.sortOrder());
        if (request.active() != null) course.setActive(request.active());
        return toResponse(courseRepository.save(course));
    }

    /** Soft delete: the course is deactivated so existing purchases keep working. */
    @Transactional
    public void delete(Long id) {
        Course course = require(id);
        course.setActive(false);
        courseRepository.save(course);
    }

    public static CourseResponse toResponse(Course course) {
        return new CourseResponse(
                course.getId(), course.getTitle(), course.getCode(), course.getCategory(),
                course.getDescription(), course.getPriceInr(), course.getThumbnailUrl(),
                course.getValidityDays(), course.getSlug(), splitHighlights(course.getHighlights()),
                course.getSortOrder(), course.isActive(), course.getCreatedAt());
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
