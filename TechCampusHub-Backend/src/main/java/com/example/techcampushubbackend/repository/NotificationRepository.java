package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Broadcasts (targetUser is null) plus notifications addressed to one user. */
    @Query("select n from Notification n where n.targetUser is null or n.targetUser.id = :userId "
            + "order by n.createdAt desc")
    List<Notification> findVisibleTo(@Param("userId") Long userId);

    List<Notification> findAllByOrderByCreatedAtDesc();

    long deleteByTargetUserId(Long userId);
}
