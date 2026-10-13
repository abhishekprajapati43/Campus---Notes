package com.example.techcampushubbackend.config;

import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the bootstrap ADMIN account on first start so the API is usable
 * out of the box. Credentials come from app.admin.* (see ENVIRONMENT.md).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final AppProperties appProperties;

    @Bean
    @Transactional
    public CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder passwordEncoder) {
        return args -> {
            String email = appProperties.getAdmin().getEmail();
            if (users.existsByEmail(email)) {
                return;
            }
            User admin = User.builder()
                    .name(appProperties.getAdmin().getName())
                    .email(email.toLowerCase())
                    .password(passwordEncoder.encode(appProperties.getAdmin().getPassword()))
                    .role(Role.ADMIN)
                    .emailVerified(true)
                    .enabled(true)
                    .build();
            users.save(admin);
            log.info("Seeded admin account '{}'", email);
        };
    }
}
