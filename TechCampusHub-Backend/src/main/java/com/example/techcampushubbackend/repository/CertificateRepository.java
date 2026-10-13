package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByCertificateNo(String certificateNo);

    List<Certificate> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Certificate> findAllByOrderByCreatedAtDesc();

    long deleteByUserId(Long userId);
}
