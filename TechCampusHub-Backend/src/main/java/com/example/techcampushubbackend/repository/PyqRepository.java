package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Pyq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PyqRepository extends JpaRepository<Pyq, Long> {

    List<Pyq> findAllByOrderByCreatedAtDesc();

    /** Content uploaded by one faculty member (dashboard counts). */
    long countByCreatedBy(Long createdBy);
}
