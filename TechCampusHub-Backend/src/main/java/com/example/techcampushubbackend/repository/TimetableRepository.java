package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Timetable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    List<Timetable> findAllByOrderByCreatedAtDesc();

    /** Content uploaded by one faculty member (dashboard counts). */
    long countByCreatedBy(Long createdBy);

    /** Free timetables plus timetables of the given course bundles. */
    @Query("select count(t) from Timetable t "
            + "where (t.courseBundle is null "
            + "or t.courseBundle.id in (:courseIds))")
    long countAccessible(@Param("courseIds") Collection<Long> courseIds);
}
