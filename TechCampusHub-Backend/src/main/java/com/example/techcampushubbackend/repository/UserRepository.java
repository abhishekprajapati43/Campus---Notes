package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String token);

    Optional<User> findByResetToken(String resetToken);

    long countByRole(Role role);

    /** Enabled admins only - used to protect the last active admin account. */
    long countByRoleAndEnabledTrue(Role role);

    List<User> findAllByOrderByCreatedAtDesc();

    /**
     * Reads a user with {@code SELECT ... FOR UPDATE} and holds the row lock
     * until the surrounding transaction commits.
     *
     * <p>Purchase requests use it to serialize per-student decisions (duplicate
     * guard + 1+1 discount), which are otherwise read-modify-write races.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
