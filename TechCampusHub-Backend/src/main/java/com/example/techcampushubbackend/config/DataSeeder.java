package com.example.techcampushubbackend.config;

import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.SiteSetting;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.repository.SiteSettingRepository;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.service.SiteSettingService;
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
            warnOnDefaultAdminPassword();
            // Normalize the same way every write path does: a mixed-case
            // ADMIN_EMAIL would otherwise never match the lower-cased row,
            // so every restart re-inserted and hit the unique email
            // constraint, crashing startup.
            String email = String.valueOf(appProperties.getAdmin().getEmail())
                    .trim()
                    .toLowerCase();
            if (users.existsByEmail(email)) {
                return;
            }
            User admin = User.builder()
                    .name(appProperties.getAdmin().getName())
                    .email(email)
                    .password(passwordEncoder.encode(appProperties.getAdmin().getPassword()))
                    .role(Role.ADMIN)
                    .emailVerified(true)
                    .enabled(true)
                    .build();
            users.save(admin);
            log.info("Seeded admin account '{}'", email);
        };
    }

    /**
     * The bootstrap password ships in the repository, so anyone who has read
     * the source knows it. Startup cannot verify the hash of an already
     * existing account, but it can shout while the default is still configured.
     */
    private void warnOnDefaultAdminPassword() {
        String password = appProperties.getAdmin().getPassword();
        if (AppProperties.Admin.DEFAULT_PASSWORD.equals(password)) {
            log.warn("app.admin.password is still the repository default - anyone who knows it can "
                    + "log in as '{}' until ADMIN_PASSWORD is overridden in the environment.",
                    appProperties.getAdmin().getEmail());
        }
    }

    /**
     * Ensures the site_settings row exists so /api/settings never has to
     * fall back to lazily creating it on first read.
     */
    @Bean
    @Transactional
    public CommandLineRunner seedSiteSettings(SiteSettingRepository siteSettings) {
        return args -> {
            if (siteSettings.findById(SiteSetting.SINGLETON_ID).isPresent()) {
                return;
            }
            siteSettings.save(SiteSetting.builder()
                    .id(SiteSetting.SINGLETON_ID)
                    .upiId(SiteSettingService.DEFAULT_UPI_ID)
                    .geminiUrl(SiteSettingService.DEFAULT_GEMINI_URL)
                    .telegramLink(SiteSettingService.DEFAULT_TELEGRAM_LINK)
                    .build());
            log.info("Seeded default site settings");
        };
    }
}
