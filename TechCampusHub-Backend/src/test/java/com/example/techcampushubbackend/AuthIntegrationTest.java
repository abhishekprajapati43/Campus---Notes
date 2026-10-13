package com.example.techcampushubbackend;

import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flows against the real filter chain (H2 + JWT).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String EMAIL = "student.demo@example.com";
    private static final String PASSWORD = "Student@123";
    private static String loginToken;

    @Test
    @Order(1)
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @Order(2)
    void registerCreatesStudentAndReturns201() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Demo Student","email":"%s","password":"%s","phone":"+91 9876543210"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));
    }

    @Test
    @Order(3)
    void duplicateEmailIsRejectedWith409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Again","email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    void invalidBodyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @Order(5)
    void loginWrongPasswordIs401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void loginReturnsJwtAndProfile() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        loginToken = node.path("data").path("token").asText();
        assertThat(loginToken).isNotBlank();
    }

    @Test
    @Order(7)
    void meEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    @Order(8)
    void invalidTokenIsTreatedAsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer totally.bogus.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    void emailVerificationFlowCompletes() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getVerificationToken()).isNotBlank();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(user.getVerificationToken())))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    @Order(10)
    void forgotAndResetPasswordFlow() throws Exception {
        // 1. start the flow (unknown and known emails behave identically)
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        String resetToken = user.getResetToken();
        assertThat(resetToken).isNotBlank();

        // 2. reset with the token
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"BrandNew@456"}
                                """.formatted(resetToken)))
                .andExpect(status().isOk());

        // 3. old password no longer works, new one does
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"BrandNew@456"}
                                """.formatted(EMAIL)))
                .andExpect(status().isOk());

        // 4. token is single-use
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"Another@789"}
                                """.formatted(resetToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    void bootstrapAdminExistsAndCanLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"Admin@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }
}
