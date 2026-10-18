package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    List<Timetable> findAllByOrderByCreatedAtDesc();

    /** Content uploaded by one faculty member (dashboard counts). */
    long countByCreatedBy(Long createdBy);
}
