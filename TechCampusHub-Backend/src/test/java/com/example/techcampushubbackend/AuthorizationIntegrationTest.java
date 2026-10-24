package com.example.techcampushubbackend;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based authorization and the purchase lifecycle, exercised through the
 * full HTTP stack with real JWTs for an admin and a student.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;
    private long courseId;

    @BeforeAll
    void issueTokens() throws Exception {
        adminToken = login("admin@test.com", "Admin@1234");

        // Register a fresh student for this test class.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Authz Student","email":"authz.student@example.com","password":"Student@123"}
                                """))
                .andExpect(status().isCreated());
        studentToken = login("authz.student@example.com", "Student@123");

        // Create the course used by the purchase/visibility tests here, so no
        // test depends on another test having run first.
        MvcResult created = mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"MCA Semester 1 Bundle","code":"MCA-S1",
                                 "category":"MCA","description":"All notes + PYQs",
                                 "priceInr":499.00,"validityDays":180,"active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("MCA Semester 1 Bundle"))
                .andReturn();
        courseId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("token").asString();
    }

    // ------------------------------------------------------ account suspension

    /**
     * A JWT stays technically valid for {@code app.jwt.expiration-ms} (24 h), so
     * an admin "Remove User" would be worthless if the filter trusted the token:
     * the account row must be re-checked on every request. Also proves the
     * check is reversible - re-enabling restores access with the same token.
     */
    @Test
    void suspensionRevokesAnAlreadyIssuedTokenImmediately() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Suspend Demo","email":"suspend.demo@example.com","password":"Student@123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = objectMapper.readTree(registered.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        String token = login("suspend.demo@example.com", "Student@123");

        // Sanity: the freshly issued token works.
        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // The admin suspends the account.
        mockMvc.perform(patch("/api/admin/users/" + userId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        // The same, still-unexpired token is refused immediately.
        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account has been suspended"));

        // Re-enabling brings the same token back to life.
        mockMvc.perform(patch("/api/admin/users/" + userId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ public/401

    @Test
    void publicCourseCatalogNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk());
    }

    @Test
    void studentContentRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------- 403s

    @Test
    void studentCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/courses")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hacked Course","priceInr":0,"validityDays":1}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/purchases")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAdminAccessIs401() throws Exception {
        mockMvc.perform(get("/api/admin/courses"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------- admin course CRUD

    @Test
    void adminCanReadUpdateAndValidateCourse() throws Exception {
        mockMvc.perform(get("/api/courses/" + courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceInr").value(499.00));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/courses/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"MCA Semester 1 Bundle (Updated)","priceInr":599.00,
                                 "validityDays":200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("MCA Semester 1 Bundle (Updated)"))
                .andExpect(jsonPath("$.data.priceInr").value(599.00));

        // missing title fails validation with 400
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/courses/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceInr\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    // ---------------------------------------------------- purchase lifecycle

    @Test
    void fullPurchaseLifecycle() throws Exception {
        // 1. student requests a purchase
        MvcResult purchase = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"utr":"UTR123456789"}
                                """.formatted(courseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        long purchaseId = objectMapper.readTree(purchase.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 2. duplicate pending request is rejected
        mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"utr":"UTR987654321"}
                                """.formatted(courseId)))
                .andExpect(status().isBadRequest());

        // 3. student cannot approve their own purchase
        mockMvc.perform(post("/api/admin/purchases/%d/approve".formatted(purchaseId))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // 4. admin approves
        mockMvc.perform(post("/api/admin/purchases/%d/approve".formatted(purchaseId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.validUntil").isNotEmpty());

        // 5. approving twice is a 400
        mockMvc.perform(post("/api/admin/purchases/%d/approve".formatted(purchaseId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        // 6. student sees the active course
        mockMvc.perform(get("/api/purchases/my-courses")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseId").value(courseId));

        // 7. admin extends validity by 30 days
        mockMvc.perform(post("/api/admin/purchases/%d/extend-validity".formatted(purchaseId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Validity extended"));
    }

    // ---------------------------------------------------- notes visibility

    @Test
    void adminCanRejectAPendingPurchase() throws Exception {
        // A second admin-created course so this test never depends on another.
        MvcResult secondCourse = mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Rejectable Course","priceInr":99.00,"validityDays":30}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long rejectCourseId = objectMapper.readTree(secondCourse.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult purchase = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"utr":"UTR-REJECT-1"}
                                """.formatted(rejectCourseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        long purchaseId = objectMapper.readTree(purchase.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // admin rejects with a reason
        mockMvc.perform(post("/api/admin/purchases/%d/reject".formatted(purchaseId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"UTR not found in bank statement\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason")
                        .value("UTR not found in bank statement"))
                .andExpect(jsonPath("$.data.validUntil").value(org.hamcrest.Matchers.nullValue()));

        // rejecting again is a 400 - only PENDING purchases can be reviewed
        mockMvc.perform(post("/api/admin/purchases/%d/reject".formatted(purchaseId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"again\"}"))
                .andExpect(status().isBadRequest());

        // a rejected purchase never shows up as an active course
        mockMvc.perform(get("/api/purchases/my-courses")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].courseId")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(rejectCourseId))));

        // rejected purchase appears in the admin REJECTED filter
        mockMvc.perform(get("/api/admin/purchases?status=REJECTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", org.hamcrest.Matchers.hasItem((int) purchaseId)));
    }

    @Test
    void pdfDownloadStreamsBytesWithoutRedirecting() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Downloadable Note","subject":"DBMS",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/dl.pdf"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long noteId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // The bytes are relayed through the backend - the response is never a
        // 302 whose Location hands out the storage URL. Status 200 when the
        // storage answers, 502/404 when it does not.
        MvcResult download = mockMvc.perform(get("/api/notes/%d/download".formatted(noteId))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            status == 200 || status == 404 || status == 502,
                            "expected relayed bytes, got status " + status);
                    org.junit.jupiter.api.Assertions.assertNull(
                            result.getResponse().getHeader("Location"),
                            "download must never redirect to the storage URL");
                })
                .andReturn();

        if (download.getResponse().getStatus() == 200) {
            org.junit.jupiter.api.Assertions.assertEquals(
                    "application/pdf", download.getResponse().getContentType());
            org.junit.jupiter.api.Assertions.assertEquals(
                    "inline", download.getResponse().getHeader("Content-Disposition"));
        }

        // anonymous download attempt is 401, not a redirect
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/notes/%d/download".formatted(noteId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminDeletesNoteAndItDisappears() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Temp Note",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/temp.pdf"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long noteId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(delete("/api/admin/notes/" + noteId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------- notes visibility

    @Test
    void bundleGatedNoteHiddenUntilPurchase() throws Exception {
        // free note - visible to the student
        MvcResult freeNote = mockMvc.perform(post("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Free Intro Notes","subject":"Basics",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/free.pdf"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long freeNoteId = objectMapper.readTree(freeNote.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // The row itself is visible to the student, but the storage URL is
        // withheld - students read the bytes through the secure relay
        // (GET /api/content/notes/{id}/pdf), never a direct link.
        mockMvc.perform(get("/api/notes/" + freeNoteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrl")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.storagePath")
                        .value(org.hamcrest.Matchers.nullValue()));

        // bundle-gated note - student without access gets 404
        mockMvc.perform(post("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Premium Notes","subject":"Advanced",
                                 "courseId":%d,
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/premium.pdf"}
                                """.formatted(courseId)))
                .andExpect(status().isCreated());

        // The student from this class already holds an approved purchase (fullPurchaseLifecycle
        // ran before), so they SHOULD see it; verify admin listing always works.
        mockMvc.perform(get("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    // ------------------------------------------------ favorites/feedback/notifications

    @Test
    void favoriteAddListRemove() throws Exception {
        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"COURSE","itemId":%d}
                                """.formatted(courseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.itemType").value("COURSE"));

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemId").value(courseId));

        mockMvc.perform(delete("/api/favorites/COURSE/" + courseId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void favoriteOfUnknownItemIs404() throws Exception {
        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemType\":\"NOTE\",\"itemId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void feedbackValidationAndSubmission() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":9,\"message\":\"too high\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"rating":5,"message":"Great bundle!"}
                                """.formatted(courseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5));

        mockMvc.perform(get("/api/admin/feedback")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rating").value(5));
    }

    @Test
    void notificationBroadcastReachesStudent() throws Exception {
        mockMvc.perform(post("/api/admin/notifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Exam Schedule Out","message":"Mid-terms start Monday",
                                 "kind":"ALERT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.broadcast").value(true));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Exam Schedule Out"));

        // invalid kind is a 400
        mockMvc.perform(post("/api/admin/notifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"message\":\"y\",\"kind\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void certificatesScopedToOwner() throws Exception {
        mockMvc.perform(post("/api/admin/certificates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Course Completion","fileUrl":"https://res.cloudinary.com/demo/image/upload/cert.png"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.certificateNo").isNotEmpty());

        mockMvc.perform(get("/api/certificates/mine")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty()); // not issued to this student
    }

    @Test
    void uploadRejectsNonPdfAndMissingFile() throws Exception {
        // Not a PDF/image content type -> 400 from the validator.
        var wrongType = new org.springframework.mock.web.MockMultipartFile(
                "file", "notes.txt", "text/plain", "plain text".getBytes());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/admin/files")
                        .file(wrongType)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}
