package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findAllByOrderByCreatedAtDesc();

    /** Notes that are free (no course bundle) or belong to the given bundle. */
    List<Note> findByCourseBundleIdOrderByCreatedAtDesc(Long courseId);

    List<Note> findByCourseIgnoreCaseOrderByCreatedAtDesc(String course);

    List<Note> findByCourseBundleIsNullOrderByCreatedAtDesc();

    List<Note> findByCourseBundleIdIsNullOrderByCreatedAtDesc();
}
