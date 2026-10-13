package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByActiveTrue();

    List<Course> findAllByActiveTrueOrderByTitleAsc();

    List<Course> findAllByOrderByTitleAsc();

    boolean existsByCode(String code);
}
