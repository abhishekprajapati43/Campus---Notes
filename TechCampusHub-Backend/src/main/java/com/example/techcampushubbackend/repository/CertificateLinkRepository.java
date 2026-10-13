package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.CertificateLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateLinkRepository extends JpaRepository<CertificateLink, Long> {

    List<CertificateLink> findByActiveTrueOrderBySortOrderAscCreatedAtAsc();

    List<CertificateLink> findAllByOrderBySortOrderAscCreatedAtAsc();
}
