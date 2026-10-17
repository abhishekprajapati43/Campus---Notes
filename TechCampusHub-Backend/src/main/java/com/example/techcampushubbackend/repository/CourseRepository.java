package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByActiveTrueOrderBySortOrderAscTitleAsc();

    List<Course> findAllByOrderBySortOrderAscTitleAsc();

    Optional<Course> findBySlugIgnoreCase(String slug);

    /** Courses launched by one faculty member (dashboard counts). */
    long countByCreatedBy(Long createdBy);
}
