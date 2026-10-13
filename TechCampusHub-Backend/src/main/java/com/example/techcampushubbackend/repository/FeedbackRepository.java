package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Feedback> findAllByOrderByCreatedAtDesc();
}
