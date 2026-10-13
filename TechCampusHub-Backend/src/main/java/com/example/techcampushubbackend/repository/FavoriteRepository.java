package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Favorite> findByUserIdAndItemTypeOrderByCreatedAtDesc(Long userId, Favorite.ItemType itemType);

    Optional<Favorite> findByUserIdAndItemTypeAndItemId(Long userId, Favorite.ItemType itemType, Long itemId);

    void deleteByUserIdAndItemTypeAndItemId(Long userId, Favorite.ItemType itemType, Long itemId);
}
