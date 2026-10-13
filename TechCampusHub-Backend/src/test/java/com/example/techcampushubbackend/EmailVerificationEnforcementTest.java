package com.example.techcampushubbackend;

import com.example.techcampushubbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that REQUIRE_EMAIL_VERIFICATION=true blocks login for unverified
 * accounts and allows it once the e-mail is verified. Runs in its own Spring
 * context because it overrides a property.
 */
@SpringBootTest(properties = "app.security.require-email-verification=true")
@AutoConfigureMockMvc
class EmailVerificationEnforcementTest {

    private static final String EMAIL = "verify.enforce@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void unverifiedUserIsBlockedUntilTokenConfirms() throws Exception {
        // 1. register - not verified yet
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Verify Me","email":"%s","password":"Secret@123"}
                                """.formatted(EMAIL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        // 2. login is refused while verification is pending -> 403 (not 401)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret@123"}
                                """.formatted(EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Email not verified")));

        // 3. confirm with the token stored at registration
        String token = userRepository.findByEmail(EMAIL).orElseThrow().getVerificationToken();
        assertThat(token).isNotBlank();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        // 4. login now succeeds and reports the verified flag
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret@123"}
                                """.formatted(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }
}
