package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Pyq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PyqRepository extends JpaRepository<Pyq, Long> {

    List<Pyq> findAllByOrderByCreatedAtDesc();

    /** Content uploaded by one faculty member (dashboard counts). */
    long countByCreatedBy(Long createdBy);

    /** Free PYQs plus PYQs of the given course bundles. */
    @Query("select count(p) from Pyq p "
            + "where (p.courseBundle is null "
            + "or p.courseBundle.id in (:courseIds))")
    long countAccessible(@Param("courseIds") Collection<Long> courseIds);
}
