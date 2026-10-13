package com.example.techcampushubbackend.repository;

import com.example.techcampushubbackend.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
}
