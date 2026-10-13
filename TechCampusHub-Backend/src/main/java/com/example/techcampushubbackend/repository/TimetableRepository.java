package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    List<Timetable> findAllByOrderByCreatedAtDesc();

    List<Timetable> findByCourseIgnoreCaseOrderByCreatedAtDesc(String course);

    List<Timetable> findByCourseBundleIdIsNullOrderByCreatedAtDesc();

    List<Timetable> findByCourseBundleIdOrderByCreatedAtDesc(Long courseId);
}
