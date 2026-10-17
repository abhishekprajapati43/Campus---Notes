package com.example.techcampushubbackend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Secure PDF streaming for the in-app viewer
 * (GET /api/content/{category}/{id}/pdf):
 *
 * - JWT required: a bare navigation/iframe (no Authorization header) gets 401,
 *   so the stream URL is never a shareable direct link;
 * - paid bundles require an approved, unexpired purchase - the exact rule the
 *   list endpoints already enforce;
 * - student payloads never carry the storage URL (fileUrl/storagePath are
 *   withheld; the admin list keeps them for the edit/replace/delete flows);
 * - the legacy /download routes relay bytes instead of 302-redirecting.
 *
 * The relay itself is proven hermetically: a loopback HttpServer (allowed via
 * app.content.stream-hosts) serves a tiny PDF, so the assertions on bytes and
 * headers need no internet access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurePdfStreamingIntegrationTest {

    private static final byte[] SAMPLE_PDF = (
            "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                    + "trailer<</Root 1 0 R>>\n%%EOF\n")
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String studentToken;
    private long freeNoteId;
    private String storageBaseUrl;
    private HttpServer storageServer;

    @BeforeAll
    void setup() throws Exception {
        adminToken = login("admin@test.com", "Admin@1234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Stream Student","email":"stream.student@example.com",
                                 "password":"Student@123"}
                                """))
                .andExpect(status().isCreated());
        studentToken = login("stream.student@example.com", "Student@123");

        // Loopback "storage" standing in for Cloudinary: deterministic relay
        // assertions (real bytes, real headers) without a network round trip.
        storageServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        storageServer.createContext("/docs/sample.pdf", exchange -> {
            exchange.sendResponseHeaders(200, SAMPLE_PDF.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(SAMPLE_PDF);
            }
        });
        storageServer.start();
        storageBaseUrl = "http://127.0.0.1:" + storageServer.getAddress().getPort();

        freeNoteId = createId("/api/admin/notes", """
                {"title":"Streamable Note","subject":"DBMS",
                 "fileUrl":"%s/docs/sample.pdf"}
                """.formatted(storageBaseUrl));
    }

    @AfterAll
    void stopStorage() {
        if (storageServer != null) {
            storageServer.stop(0);
        }
    }

    // ------------------------------------------------------------ auth wall

    @Test
    void streamRejectsAnonymousAccess() throws Exception {
        // No JWT: the stream URL opened directly (browser tab, iframe, shared
        // link) must fail - it can never act as a direct PDF link.
        mockMvc.perform(get("/api/content/notes/{id}/pdf", freeNoteId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notes/{id}/download", freeNoteId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCategoryIsNotFound() throws Exception {
        mockMvc.perform(get("/api/content/secrets/{id}/pdf", 1L)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------- relay

    @Test
    void authenticatedStreamRelaysPdfBytesWithSafeHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/content/notes/{id}/pdf", freeNoteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("Location"))
                .andReturn();

        assertEquals("application/pdf", result.getResponse().getContentType());
        assertArrayEquals(SAMPLE_PDF, result.getResponse().getContentAsByteArray());
    }

    @Test
    void downloadRelaysInsteadOfRedirecting() throws Exception {
        mockMvc.perform(get("/api/notes/{id}/download", freeNoteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Content-Disposition", "inline"));
    }

    // ------------------------------------------------------- URL exposure

    @Test
    void nonAllowlistedStorageHostIsRejectedBeforeFetching() throws Exception {
        long noteId = createId("/api/admin/notes", """
                {"title":"Off-allowlist Note","subject":"Security",
                 "fileUrl":"https://evil.example.com/doc.pdf"}
                """);

        // The allowlist guard runs before any upstream fetch: the relay
        // answers bad gateway and never proxies bytes from elsewhere.
        mockMvc.perform(get("/api/content/notes/{id}/pdf", noteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isBadGateway())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void studentPayloadsNeverExposeStorageUrls() throws Exception {
        MvcResult list = mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rows = objectMapper.readTree(list.getResponse().getContentAsString())
                .path("data");
        boolean sawOwnRow = false;
        for (JsonNode row : rows) {
            assertTrue(!row.has("fileUrl") || row.get("fileUrl").isNull(),
                    "student rows must not carry fileUrl");
            assertTrue(!row.has("storagePath") || row.get("storagePath").isNull(),
                    "student rows must not carry storagePath");
            if (row.path("id").asLong() == freeNoteId) {
                sawOwnRow = true;
            }
        }
        assertTrue(sawOwnRow, "the free note should be visible to the student");

        // The single-item student read is sanitized the same way.
        mockMvc.perform(get("/api/notes/{id}", freeNoteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.storagePath")
                        .value(org.hamcrest.Matchers.nullValue()));

        // Admins keep the real URL - the edit/replace/delete flows depend on it.
        MvcResult adminList = mockMvc.perform(get("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode adminRows = objectMapper.readTree(adminList.getResponse().getContentAsString())
                .path("data");
        String expectedUrl = storageBaseUrl + "/docs/sample.pdf";
        boolean adminSawUrl = false;
        for (JsonNode row : adminRows) {
            if (row.path("id").asLong() == freeNoteId) {
                adminSawUrl = expectedUrl.equals(row.path("fileUrl").asString());
            }
        }
        assertTrue(adminSawUrl, "admin payloads must keep the real storage URL");
    }

    // --------------------------------------------------- purchase gating

    @Test
    void paidBundleStreamRequiresActiveSubscription() throws Exception {
        long paidCourseId = createId("/api/admin/courses", """
                {"title":"Secure Stream Bundle","priceInr":299.00,"validityDays":90}
                """);
        long paidNoteId = createId("/api/admin/notes", """
                {"title":"Premium Stream Note","subject":"Cloud",
                 "fileUrl":"%s/docs/sample.pdf","courseId":%d}
                """.formatted(storageBaseUrl, paidCourseId));

        // No purchase yet: the stream and the legacy download are invisible.
        mockMvc.perform(get("/api/content/notes/{id}/pdf", paidNoteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notes/{id}/download", paidNoteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        // Approve a purchase and the very same stream opens up.
        MvcResult purchase = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"utr":"UTR-STREAM-1"}
                                """.formatted(paidCourseId)))
                .andExpect(status().isCreated())
                .andReturn();
        long purchaseId = objectMapper.readTree(purchase.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/purchases/{id}/approve", purchaseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/content/notes/{id}/pdf", paidNoteId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void staffCanStreamBundleContentWithoutPurchases() throws Exception {
        // Faculties and admins bypass purchase gates for reading (existing
        // canAccess rule); the stream honours the same rule.
        long paidCourseId = createId("/api/admin/courses", """
                {"title":"Staff Stream Bundle","priceInr":199.00,"validityDays":60}
                """);
        long paidNoteId = createId("/api/admin/notes", """
                {"title":"Staff Stream Note","subject":"AI",
                 "fileUrl":"%s/docs/sample.pdf","courseId":%d}
                """.formatted(storageBaseUrl, paidCourseId));

        mockMvc.perform(get("/api/content/notes/{id}/pdf", paidNoteId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------- utils

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asString();
    }

    private long createId(String path, String body) throws Exception {
        MvcResult created = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }
}
