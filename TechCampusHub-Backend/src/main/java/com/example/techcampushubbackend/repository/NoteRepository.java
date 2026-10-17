package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findAllByOrderByCreatedAtDesc();

    /** Content uploaded by one faculty member (dashboard counts). */
    long countByCreatedBy(Long createdBy);

    /**
     * Availability count for one student: free notes (no course bundle)
     * plus notes of the course bundles they may open. The caller supplies
     * the accessible bundle ids; an empty list means "free content only".
     */
    @Query("select count(n) from Note n "
            + "where (n.courseBundle is null "
            + "or n.courseBundle.id in (:courseIds))")
    long countAccessible(@Param("courseIds") Collection<Long> courseIds);
}
