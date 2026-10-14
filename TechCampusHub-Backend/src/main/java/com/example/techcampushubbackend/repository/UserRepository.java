package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String token);

    Optional<User> findByResetToken(String resetToken);

    List<User> findByRole(Role role);

    long countByRole(Role role);

    /** Enabled admins only - used to protect the last active admin account. */
    long countByRoleAndEnabledTrue(Role role);

    List<User> findAllByOrderByCreatedAtDesc();
}
