package com.example.techcampushubbackend.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Cloudinary SDK configuration. Credentials come from the environment
 * (CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET).
 * Only the resulting secure URL is ever persisted in PostgreSQL.
 */
@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(AppProperties properties) {
        AppProperties.Cloudinary cfg = properties.getCloudinary();
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cfg.getCloudName() == null ? "" : cfg.getCloudName(),
                "api_key", cfg.getApiKey() == null ? "" : cfg.getApiKey(),
                "api_secret", cfg.getApiSecret() == null ? "" : cfg.getApiSecret(),
                "secure", true));
    }

    @Bean
    public Map<String, Object> cloudinaryUploadDefaults(AppProperties properties) {
        return ObjectUtils.asMap("folder", properties.getCloudinary().getFolder());
    }
}
