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
        return courseRepository.findAllByActiveTrueOrderByTitleAsc().stream()
                .map(CourseService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listAll() {
        return courseRepository.findAllByOrderByTitleAsc().stream()
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
                course.getValidityDays(), course.isActive(), course.getCreatedAt());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
