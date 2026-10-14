package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Feedback;
import com.example.techcampushubbackend.entity.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    long deleteByUserId(Long userId);

    // ------------------------------------------------------------- reviews

    /** Latest review a user left on a course (used for upsert). */
    Optional<Feedback> findFirstByUserIdAndCourseIdOrderByCreatedAtDesc(
            Long userId, Long courseId);

    List<Feedback> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    /**
     * Average + total count of the reviews left on one course. Course reviews
     * are the rows that carry a course_id. Returns a single row as a list so
     * Spring Data does not double-wrap the aggregate columns.
     */
    @Query("select avg(f.rating), count(f) from Feedback f where f.course.id = :courseId")
    List<Object[]> findCourseRatingSummary(@Param("courseId") Long courseId);

    /** Platform-wide summary: every rating left on any course. */
    @Query("select avg(f.rating), count(f) from Feedback f where f.course is not null")
    List<Object[]> findOverallRatingSummary();

    /**
     * One row per reviewed course: courseId, average rating, review count.
     * Powers the rating badges on the course catalog in a single query.
     */
    @Query("select f.course.id, avg(f.rating), count(f) from Feedback f "
            + "where f.course is not null group by f.course.id")
    List<Object[]> findAllCourseRatingSummaries();

    /** True while the user holds an approved purchase of the course. */
    @Query("select count(p) > 0 from Purchase p where p.user.id = :userId "
            + "and p.course.id = :courseId and p.status = :status")
    boolean existsPurchaseWithStatus(@Param("userId") Long userId,
                                     @Param("courseId") Long courseId,
                                     @Param("status") PurchaseStatus status);
}
