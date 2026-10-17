package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByCertificateNo(String certificateNo);

    List<Certificate> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Certificate> findAllByOrderByCreatedAtDesc();

    /**
     * Certificates a faculty member may manage: issued for one of their
     * courses, or issued to a student enrolled in any of their courses.
     */
    @Query("select c from Certificate c where "
            + "(c.course is not null and c.course.createdBy = :facultyId) "
            + "or (c.user is not null and c.user.id in "
            + "    (select p.user.id from Purchase p where p.course.createdBy = :facultyId)) "
            + "order by c.createdAt desc")
    List<Certificate> findVisibleToFaculty(@Param("facultyId") Long facultyId);

    long deleteByUserId(Long userId);
}
