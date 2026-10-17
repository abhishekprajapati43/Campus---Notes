package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.FacultyResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacultyResourceRepository extends JpaRepository<FacultyResource, Long> {

    List<FacultyResource> findAllByCreatedByOrderByCreatedAtDesc(Long createdBy);

    long countByCreatedBy(Long createdBy);
}
