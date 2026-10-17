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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Faculty registration rules and the faculty RBAC contract, exercised through
 * the full HTTP stack with real JWTs: faculty create/read/update their own
 * notes and courses, deletion stays exclusively admin-only, and every
 * {@code /api/admin/**} route is closed to faculty and students.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FacultyAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String facultyToken;
    private String otherFacultyToken;
    private String studentToken;

    @BeforeAll
    void issueTokens() throws Exception {
        adminToken = login("admin@test.com", "Admin@1234");

        // Faculty #1 - lowercase role value on purpose (case-insensitive parsing).
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Faculty One","email":"faculty.one@example.com",
                                 "password":"Faculty@123","phone":"9876543210","role":"faculty",
                                 "aadhaarNumber":"123456789012",
                                 "address":"Dept of CSE, TechCampus Hub","declarationAccepted":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("FACULTY"))
                .andExpect(jsonPath("$.data.registrationStatus").value("PENDING"));

        // A pending faculty sign-up cannot log in - the approval gate fires first.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"faculty.one@example.com","password":"Faculty@123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("pending approval")));
        approveFaculty("faculty.one@example.com");

        facultyToken = login("faculty.one@example.com", "Faculty@123");

        // Faculty #2 - used as "another user" for ownership checks.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Faculty Two","email":"faculty.two@example.com",
                                 "password":"Faculty@123","phone":"9876543211","role":"FACULTY",
                                 "aadhaarNumber":"123456789013",
                                 "address":"Dept of ECE, TechCampus Hub","declarationAccepted":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.registrationStatus").value("PENDING"));
        approveFaculty("faculty.two@example.com");
        otherFacultyToken = login("faculty.two@example.com", "Faculty@123");

        // Plain student for the negative-path tests.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Faculty Test Student","email":"faculty.student@example.com",
                                 "password":"Student@123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                // Students are never gated by the approval workflow.
                .andExpect(jsonPath("$.data.registrationStatus").value("APPROVED"));
        studentToken = login("faculty.student@example.com", "Student@123");
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

    /** Resolves a user id through the admin Users panel listing. */
    private long userIdByEmail(String email) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");
        for (JsonNode user : data) {
            if (email.equals(user.path("email").asString())) {
                return user.path("id").asLong();
            }
        }
        throw new IllegalStateException("No such user: " + email);
    }

    /** Admin approval action from the Manage Faculty panel. */
    private void approveFaculty(String email) throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + userIdByEmail(email) + "/registration-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationStatus").value("APPROVED"));
    }

    /** Creates a note through the faculty workspace and returns its id. */
    private long createNote(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/faculty/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","subject":"Faculty Subject",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/faculty.pdf"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    /** Creates a note through the legacy admin path (no owner stamped). */
    private long createAdminNote(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/admin.pdf"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private static String validNoteBody(String title) {
        return """
                {"title":"%s","subject":"Faculty Subject",
                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/faculty.pdf"}
                """.formatted(title);
    }

    // ------------------------------------------------- registration rules

    @Test
    void facultyRegistrationRequiresDeclaration() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Decl","email":"faculty.nodecl@example.com",
                                 "password":"Faculty@123","phone":"9876543220","role":"FACULTY",
                                 "aadhaarNumber":"123456789020",
                                 "address":"Somewhere","declarationAccepted":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("declaration")));

        // Missing declarationAccepted entirely is rejected the same way.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Decl","email":"faculty.nodecl2@example.com",
                                 "password":"Faculty@123","phone":"9876543221","role":"FACULTY",
                                 "aadhaarNumber":"123456789021","address":"Somewhere"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facultyRegistrationRequiresAadhaar() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Aadhaar","email":"faculty.noaadhaar@example.com",
                                 "password":"Faculty@123","phone":"9876543222","role":"FACULTY",
                                 "address":"Somewhere","declarationAccepted":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("aadhaarNumber")));
    }

    @Test
    void facultyRegistrationRequiresPhoneAndAddress() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Phone","email":"faculty.nophone@example.com",
                                 "password":"Faculty@123","role":"FACULTY",
                                 "aadhaarNumber":"123456789022",
                                 "address":"Somewhere","declarationAccepted":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("phone")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"No Address","email":"faculty.noaddr@example.com",
                                 "password":"Faculty@123","phone":"9876543223","role":"FACULTY",
                                 "aadhaarNumber":"123456789023","declarationAccepted":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("address")));
    }

    @Test
    void facultyRegistrationSucceedsAndNeverExposesAadhaar() throws Exception {
        String aadhaar = "999988887777";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Valid Faculty","email":"faculty.valid@example.com",
                                 "password":"Faculty@123","phone":"9876543224","role":"FACULTY",
                                 "aadhaarNumber":"%s","address":"Block A, TechCampus",
                                 "declarationAccepted":true}
                                """.formatted(aadhaar)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("FACULTY"))
                .andExpect(jsonPath("$.data.declarationAccepted").value(true))
                .andExpect(jsonPath("$.data.phone").value("9876543224"))
                .andExpect(jsonPath("$.data.aadhaarNumber").doesNotExist())
                // The raw number and its hash must never appear in any response.
                .andExpect(content().string(not(containsString(aadhaar))))
                .andExpect(content().string(not(containsString("aadhaar"))));
    }

    @Test
    void registeringAsAdminIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Wannabe Admin","email":"faculty.fakeadmin@example.com",
                                 "password":"Faculty@123","role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("role must be student or faculty"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Wannabe Super","email":"faculty.fakesuper@example.com",
                                 "password":"Faculty@123","role":"superadmin"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------- faculty notes

    @Test
    void facultyCanCreateListAndUpdateOwnNote() throws Exception {
        long ownNoteId = createNote(facultyToken, "Owned Note Lifecycle");
        long adminNoteId = createAdminNote("Admin Owned Note");

        // Own note is listed; content created via the admin path is not.
        mockMvc.perform(get("/api/faculty/notes")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) ownNoteId)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) adminNoteId))));

        // Updating the own note succeeds.
        mockMvc.perform(put("/api/faculty/notes/" + ownNoteId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNoteBody("Owned Note Lifecycle (Edited)")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Owned Note Lifecycle (Edited)"));

        // An admin sees everything through the same faculty route.
        mockMvc.perform(get("/api/faculty/notes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) ownNoteId)))
                .andExpect(jsonPath("$.data[*].id", hasItem((int) adminNoteId)));
    }

    @Test
    void facultyCannotEditNotesTheyDoNotOwn() throws Exception {
        long otherFacultyNoteId = createNote(otherFacultyToken, "Other Faculty Note");
        long adminNoteId = createAdminNote("Admin Created Note");

        // Another user's note -> 403.
        mockMvc.perform(put("/api/faculty/notes/" + otherFacultyNoteId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNoteBody("Hijacked Title")))
                .andExpect(status().isForbidden());

        // Content with no recorded owner (admin path) -> 403 for faculty.
        mockMvc.perform(put("/api/faculty/notes/" + adminNoteId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNoteBody("Hijacked Title")))
                .andExpect(status().isForbidden());
    }

    @Test
    void facultyCannotDeleteNotes() throws Exception {
        long ownNoteId = createNote(facultyToken, "Undeletable Note");

        // The only delete route lives under /api/admin/** -> 403 for faculty.
        mockMvc.perform(delete("/api/admin/notes/" + ownNoteId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());

        // The note is untouched.
        mockMvc.perform(get("/api/notes/" + ownNoteId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk());
    }

    @Test
    void facultyCannotUseAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/notes")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked Course","priceInr":0,"validityDays":1}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/courses/1")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","priceInr":1}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminExclusivelyDeletesFacultyNote() throws Exception {
        long noteId = createNote(facultyToken, "Admin Deletable Note");

        mockMvc.perform(delete("/api/admin/notes/" + noteId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes/" + noteId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCannotUseFacultyEndpoints() throws Exception {
        mockMvc.perform(post("/api/faculty/notes")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNoteBody("Student Note")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/faculty/notes")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/faculty/notes/1")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validNoteBody("Student Edit")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/faculty/courses")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Student Course","priceInr":0,"validityDays":1}
                                """))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------- admin auditing

    @Test
    void adminNoteListShowsWhoUploadedEachNote() throws Exception {
        long noteId = createNote(facultyToken, "Auditable Note");

        // Admin list carries the uploader's name + role for moderation.
        MvcResult adminView = mockMvc.perform(get("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode adminData = objectMapper.readTree(adminView.getResponse().getContentAsString())
                .path("data");
        JsonNode audited = null;
        for (JsonNode note : adminData) {
            if (note.path("id").asLong() == noteId) {
                audited = note;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(audited, "note missing from admin list");
        org.junit.jupiter.api.Assertions.assertEquals("Faculty One",
                audited.path("createdByName").asString());
        org.junit.jupiter.api.Assertions.assertEquals("FACULTY",
                audited.path("createdByRole").asString());

        // The student-facing payload stays free of uploader metadata.
        MvcResult studentView = mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(studentView.getResponse().getContentAsString())
                .path("data");
        boolean seen = false;
        for (JsonNode note : data) {
            if (note.path("id").asLong() == noteId) {
                seen = true;
                assertFalse(note.has("createdByName"),
                        "student payload must not expose uploader metadata");
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(seen, "note missing from student list");
    }

    // ------------------------------------------------------- faculty courses

    @Test
    void facultyCanCreateAndListOwnCourses() throws Exception {
        MvcResult facultyCourse = mockMvc.perform(post("/api/faculty/courses")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Faculty Launched Course","priceInr":199.00,
                                 "validityDays":90}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        long ownCourseId = objectMapper.readTree(facultyCourse.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult adminCourse = mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Admin Only Course","priceInr":299.00,"validityDays":90}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long adminCourseId = objectMapper.readTree(adminCourse.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Faculty sees only what they launched...
        mockMvc.perform(get("/api/faculty/courses")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) ownCourseId)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) adminCourseId))));

        // ...admins see everything through the same route.
        mockMvc.perform(get("/api/faculty/courses")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) ownCourseId)))
                .andExpect(jsonPath("$.data[*].id", hasItem((int) adminCourseId)));
    }

    // ------------------------------------------------------------- course MRP

    @Test
    void courseMrpRoundTripsThroughAdminApi() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"MRP Course","priceInr":799.00,"mrpInr":999.00,
                                 "validityDays":180}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priceInr").value(799.00))
                .andExpect(jsonPath("$.data.mrpInr").value(999.00))
                .andReturn();
        long courseId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Public read path echoes it too.
        mockMvc.perform(get("/api/courses/" + courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mrpInr").value(999.00));

        // Omitting mrpInr on update clears it (no discount shown).
        mockMvc.perform(put("/api/admin/courses/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"MRP Course","priceInr":799.00,"validityDays":180}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mrpInr").value(org.hamcrest.Matchers.nullValue()));

        // Negative MRP fails bean validation.
        mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad MRP","priceInr":10.00,"mrpInr":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mrpInr").exists());
    }

    // --------------------------------------------------------------- uploads

    @Test
    void facultyMayUploadFilesButStudentsMayNot() throws Exception {
        var wrongType = new org.springframework.mock.web.MockMultipartFile(
                "file", "notes.txt", "text/plain", "plain text".getBytes());

        // Faculty passes the role gate; the content-type check rejects with 400.
        mockMvc.perform(multipart("/api/faculty/files").file(wrongType)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isBadRequest());

        // Students are blocked before the file is ever inspected.
        mockMvc.perform(multipart("/api/faculty/files").file(wrongType)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // Deleting Cloudinary assets stays admin-only (no faculty delete route).
        mockMvc.perform(delete("/api/admin/files?publicId=some-public-id")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------------- registration approval

    /** Registers a fresh faculty sign-up; always lands as PENDING. */
    private void registerPendingFaculty(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pending Faculty","email":"%s",
                                 "password":"Faculty@123","phone":"9876543900","role":"FACULTY",
                                 "aadhaarNumber":"123456789099","address":"Block B, TechCampus",
                                 "declarationAccepted":true}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.registrationStatus").value("PENDING"));
    }

    @Test
    void rejectedFacultyCannotLoginAndExistingTokensStopWorking() throws Exception {
        registerPendingFaculty("faculty.reject@example.com");
        approveFaculty("faculty.reject@example.com");
        String token = login("faculty.reject@example.com", "Faculty@123");

        // Sanity: the approved account works.
        mockMvc.perform(get("/api/faculty/notes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // The admin rejects the application.
        mockMvc.perform(patch("/api/admin/users/" + userIdByEmail("faculty.reject@example.com")
                                + "/registration-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Faculty application rejected"))
                .andExpect(jsonPath("$.data.registrationStatus").value("REJECTED"));

        // Login is refused with an actionable message...
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"faculty.reject@example.com","password":"Faculty@123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("rejected")));

        // ...and the already-issued JWT stops working immediately.
        mockMvc.perform(get("/api/faculty/notes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsCanApproveOrRejectFaculty() throws Exception {
        registerPendingFaculty("faculty.selfapprove@example.com");
        long id = userIdByEmail("faculty.selfapprove@example.com");

        // Another faculty member may not approve themselves.
        mockMvc.perform(patch("/api/admin/users/" + id + "/registration-status")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        // Neither may a student.
        mockMvc.perform(patch("/api/admin/users/" + id + "/registration-status")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        // Unknown account -> 404 for the admin.
        mockMvc.perform(patch("/api/admin/users/999999/registration-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrationStatusOnlyAcceptsApproveOrRejectForFaculty() throws Exception {
        registerPendingFaculty("faculty.badstatus@example.com");
        long facultyId = userIdByEmail("faculty.badstatus@example.com");

        // PENDING is never an admin-settable value.
        mockMvc.perform(patch("/api/admin/users/" + facultyId + "/registration-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());

        // Unknown enum values fail deserialization with 400.
        mockMvc.perform(patch("/api/admin/users/" + facultyId + "/registration-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());

        // Students never pass through the faculty approval workflow.
        long studentId = userIdByEmail("faculty.student@example.com");
        mockMvc.perform(patch("/api/admin/users/" + studentId + "/registration-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("faculty")));
    }

    // ------------------------------------- faculty TNP / PYQ / timetable upload

    private long createFacultyTnp(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/faculty/tnp-notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","topic":"Aptitude",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/tnp.pdf"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createFacultyPyq(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/faculty/pyqs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","subject":"Maths","course":"MCA","semester":2,
                                 "examYear":2025,
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/pyq.pdf"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createFacultyTimetable(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/faculty/timetables")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","course":"MCA","semester":2,"examType":"Mid Sem",
                                 "examYear":2026,
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/tt.pdf"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void facultyCanCreateListAndUpdateOwnCategoryContent() throws Exception {
        long tnpId = createFacultyTnp(facultyToken, "My TNP");
        long pyqId = createFacultyPyq(facultyToken, "My PYQ");
        long timetableId = createFacultyTimetable(facultyToken, "My Timetable");

        // Admin-path content is not part of a faculty member's own list.
        long adminTnpId = objectMapper.readTree(
                        mockMvc.perform(post("/api/admin/tnp-notes")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"title":"Admin TNP",
                                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/a.pdf"}
                                                """))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/faculty/tnp-notes")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) tnpId)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) adminTnpId))));

        mockMvc.perform(get("/api/faculty/pyqs")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) pyqId)));

        mockMvc.perform(get("/api/faculty/timetables")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) timetableId)));

        // Updates of own content succeed.
        mockMvc.perform(put("/api/faculty/tnp-notes/" + tnpId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My TNP (edited)",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/tnp.pdf"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("My TNP (edited)"));

        mockMvc.perform(put("/api/faculty/pyqs/" + pyqId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My PYQ (edited)",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/pyq.pdf"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("My PYQ (edited)"));

        mockMvc.perform(put("/api/faculty/timetables/" + timetableId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My Timetable (edited)",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/tt.pdf"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("My Timetable (edited)"));
    }

    @Test
    void facultyCannotEditCategoryContentTheyDoNotOwn() throws Exception {
        long foreignTnp = createFacultyTnp(otherFacultyToken, "Other TNP");
        long foreignPyq = createFacultyPyq(otherFacultyToken, "Other PYQ");
        long foreignTimetable = createFacultyTimetable(otherFacultyToken, "Other Timetable");

        mockMvc.perform(put("/api/faculty/tnp-notes/" + foreignTnp)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/x.pdf"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/faculty/pyqs/" + foreignPyq)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/x.pdf"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/faculty/timetables/" + foreignTimetable)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/x.pdf"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void facultyCannotDeleteCategoryContent() throws Exception {
        long tnpId = createFacultyTnp(facultyToken, "Undeletable TNP");
        long pyqId = createFacultyPyq(facultyToken, "Undeletable PYQ");
        long timetableId = createFacultyTimetable(facultyToken, "Undeletable Timetable");

        // No DELETE route exists under /api/faculty/** at all - the paths only
        // answer PUT, so Spring replies 405 Method Not Allowed.
        mockMvc.perform(delete("/api/faculty/tnp-notes/" + tnpId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/faculty/pyqs/" + pyqId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/faculty/timetables/" + timetableId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isMethodNotAllowed());

        // The admin-only delete paths stay closed to faculty.
        mockMvc.perform(delete("/api/admin/tnp-notes/" + tnpId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/pyqs/" + pyqId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/timetables/" + timetableId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());

        // Everything is still there.
        mockMvc.perform(get("/api/faculty/tnp-notes")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) tnpId)));
        mockMvc.perform(get("/api/faculty/pyqs")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) pyqId)));
        mockMvc.perform(get("/api/faculty/timetables")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) timetableId)));

        // And the admin keeps exclusive delete rights.
        mockMvc.perform(delete("/api/admin/tnp-notes/" + tnpId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/pyqs/" + pyqId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/timetables/" + timetableId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotUseFacultyCategoryEndpoints() throws Exception {
        mockMvc.perform(post("/api/faculty/tnp-notes")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Student TNP",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/x.pdf"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/faculty/pyqs")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Student PYQ",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/x.pdf"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/faculty/timetables")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Student Timetable",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/x.pdf"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/faculty/pyqs")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------- profile fields at sign-up

    @Test
    void registrationProfileFieldsRoundTripThroughAuthMe() throws Exception {
        // Student: gender + roll/enrollment number + phone/address are optional
        // at sign-up but must come back from the JWT-protected profile fetch.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Profile Field Student","email":"profile.fields.student@example.com",
                                 "password":"Student@123","phone":"9123456780",
                                 "address":"12, Knowledge Park, Pune","gender":"Female",
                                 "enrollmentNumber":"ENR-2026-042"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.gender").value("Female"))
                .andExpect(jsonPath("$.data.enrollmentNumber").value("ENR-2026-042"))
                .andExpect(jsonPath("$.data.address").value("12, Knowledge Park, Pune"));

        String profileStudentToken = login("profile.fields.student@example.com", "Student@123");
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + profileStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value("Female"))
                .andExpect(jsonPath("$.data.enrollmentNumber").value("ENR-2026-042"))
                .andExpect(jsonPath("$.data.phone").value("9123456780"))
                .andExpect(jsonPath("$.data.address").value("12, Knowledge Park, Pune"));

        // Faculty: department/qualification/experience/gender as shown on the
        // faculty profile page.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Profile Field Faculty","email":"profile.fields.faculty@example.com",
                                 "password":"Faculty@123","phone":"9876543219","role":"FACULTY",
                                 "aadhaarNumber":"123456789015",
                                 "address":"Dept of IT, TechCampus Hub","declarationAccepted":true,
                                 "gender":"Male","department":"Information Technology",
                                 "qualification":"M.Tech","experience":"5 years"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.department").value("Information Technology"))
                .andExpect(jsonPath("$.data.qualification").value("M.Tech"))
                .andExpect(jsonPath("$.data.experience").value("5 years"));
        approveFaculty("profile.fields.faculty@example.com");

        String profileFacultyToken = login("profile.fields.faculty@example.com", "Faculty@123");
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + profileFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.department").value("Information Technology"))
                .andExpect(jsonPath("$.data.qualification").value("M.Tech"))
                .andExpect(jsonPath("$.data.experience").value("5 years"))
                .andExpect(jsonPath("$.data.gender").value("Male"))
                .andExpect(jsonPath("$.data.address").value("Dept of IT, TechCampus Hub"))
                // Registration date drives the "Registration Date" profile row.
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    // --------------------------------------------------- course content view

    /** Creates content of any category linked to a course bundle; returns its id. */
    private long createContentForCourse(String token, String path, String title, long courseId)
            throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","courseId":%d,
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/bundle.pdf"}
                                """.formatted(title, courseId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void facultyCanOpenOwnCourseWithAllUploadedContent() throws Exception {
        MvcResult courseResult = mockMvc.perform(post("/api/faculty/courses")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Content Bundle Course","priceInr":99.00,"validityDays":60}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        long noteId = createContentForCourse(facultyToken, "/api/faculty/notes",
                "Bundle Note", courseId);
        long tnpId = createContentForCourse(facultyToken, "/api/faculty/tnp-notes",
                "Bundle TNP", courseId);
        long pyqId = createContentForCourse(facultyToken, "/api/faculty/pyqs",
                "Bundle PYQ", courseId);
        long timetableId = createContentForCourse(facultyToken, "/api/faculty/timetables",
                "Bundle Timetable", courseId);

        // The owner opens the course and sees every upload category together.
        mockMvc.perform(get("/api/faculty/courses/" + courseId + "/content")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.id").value((int) courseId))
                .andExpect(jsonPath("$.data.notes[*].id", hasItem((int) noteId)))
                .andExpect(jsonPath("$.data.tnpNotes[*].id", hasItem((int) tnpId)))
                .andExpect(jsonPath("$.data.pyqs[*].id", hasItem((int) pyqId)))
                .andExpect(jsonPath("$.data.timetables[*].id", hasItem((int) timetableId)));

        // Another faculty member may not open someone else's course (403)...
        mockMvc.perform(get("/api/faculty/courses/" + courseId + "/content")
                        .header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isForbidden());

        // ...students are excluded at the controller level...
        mockMvc.perform(get("/api/faculty/courses/" + courseId + "/content")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // ...admins can audit any course's content...
        mockMvc.perform(get("/api/faculty/courses/" + courseId + "/content")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes[*].id", hasItem((int) noteId)));

        // ...and an unknown id is a 404 even for its own owner.
        mockMvc.perform(get("/api/faculty/courses/999999/content")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------- faculty dashboard (stats)

    /** Creates a course through the faculty workspace and returns its id. */
    private long createFacultyCourse(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/faculty/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","priceInr":149.00,"validityDays":60}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    /** Registers a plain (always approved) student and returns their JWT. */
    private String registerStudent(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Scoped Student","email":"%s","password":"Student@123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
        return login(email, "Student@123");
    }

    /** Student requests a purchase; admin approves it; returns the purchase id. */
    private long purchaseAndApprove(String studentJwt, long courseId, String utr) throws Exception {
        MvcResult purchase = mockMvc.perform(post("/api/purchases")
                        .header("Authorization", "Bearer " + studentJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"utr":"%s"}
                                """.formatted(courseId, utr)))
                .andExpect(status().isCreated())
                .andReturn();
        long purchaseId = objectMapper.readTree(purchase.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/admin/purchases/%d/approve".formatted(purchaseId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        return purchaseId;
    }

    /** Reads the $.data payload of GET /api/faculty/stats for a caller. */
    private JsonNode dashboardStats(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/faculty/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    void facultyDashboardStatsAreScopedToOwnCourses() throws Exception {
        // Deltas, not absolutes: the suite shares one database, so other tests
        // may already have created courses/students for these accounts.
        JsonNode ownBefore = dashboardStats(facultyToken);
        JsonNode peerBefore = dashboardStats(otherFacultyToken);

        long ownCourse = createFacultyCourse(facultyToken, "Stats Owned Course");
        long otherCourse = createFacultyCourse(otherFacultyToken, "Stats Foreign Course");
        // A brand-new student so the peer's distinct-student delta is exactly +1.
        String statsBuyer = registerStudent("faculty.stats.buyer@example.com");
        purchaseAndApprove(statsBuyer, otherCourse, "UTR-STATS-FOREIGN");

        JsonNode ownAfter = dashboardStats(facultyToken);
        JsonNode peerAfter = dashboardStats(otherFacultyToken);

        // The owner's course count grows by one; nothing about the foreign
        // course or its purchase leaks into their numbers.
        assertEquals(ownBefore.path("totalCourses").asLong() + 1,
                ownAfter.path("totalCourses").asLong(),
                "owner must count their own new course");
        assertEquals(ownBefore.path("totalStudents").asLong(),
                ownAfter.path("totalStudents").asLong(),
                "a purchase of another faculty's course must not count");
        assertEquals(ownBefore.path("recentPurchases").asLong(),
                ownAfter.path("recentPurchases").asLong(),
                "recent purchases are scoped to own courses too");

        // The owning peer sees the new course, its buyer and the recent purchase.
        assertEquals(peerBefore.path("totalCourses").asLong() + 1,
                peerAfter.path("totalCourses").asLong());
        assertEquals(peerBefore.path("totalStudents").asLong() + 1,
                peerAfter.path("totalStudents").asLong());
        assertEquals(peerBefore.path("recentPurchases").asLong() + 1,
                peerAfter.path("recentPurchases").asLong());

        // The owner's stats payload never references the foreign course.
        mockMvc.perform(get("/api/faculty/stats")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentPurchaseList[*].courseId",
                        not(hasItem((int) otherCourse))));
        // The peer's recent list does carry it.
        mockMvc.perform(get("/api/faculty/stats")
                        .header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentPurchaseList[*].courseId",
                        hasItem((int) otherCourse)));

        // Students are locked out of the whole dashboard (URL rule + method security).
        mockMvc.perform(get("/api/faculty/stats")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/faculty/students")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        // Anonymous callers get 401.
        mockMvc.perform(get("/api/faculty/stats"))
                .andExpect(status().isUnauthorized());
        assertFalse(ownCourse == otherCourse, "sanity: distinct course ids");
    }

    @Test
    void facultyStudentTableIsReadOnlyAndScopedToOwnBuyers() throws Exception {
        String buyer = registerStudent("faculty.table.buyer@example.com");
        long ownCourse = createFacultyCourse(facultyToken, "Students Owned Course");
        long otherCourse = createFacultyCourse(otherFacultyToken, "Students Foreign Course");
        long ownPurchase = purchaseAndApprove(buyer, ownCourse, "UTR-STUDENTS-OWN");
        long foreignPurchase = purchaseAndApprove(buyer, otherCourse, "UTR-STUDENTS-FOREIGN");

        // The owner's table contains their buyer row with every required column.
        MvcResult ownerView = mockMvc.perform(get("/api/faculty/students")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(ownerView.getResponse().getContentAsString())
                .path("data");
        JsonNode ownRow = null;
        for (JsonNode row : rows) {
            assertFalse(row.path("purchaseId").asLong() == foreignPurchase,
                    "buyers of another faculty's course must never appear");
            if (row.path("purchaseId").asLong() == ownPurchase) {
                ownRow = row;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(ownRow,
                "the owner's buyer row must be listed");
        assertEquals("Scoped Student", ownRow.path("studentName").asString());
        assertEquals("faculty.table.buyer@example.com", ownRow.path("userEmail").asString());
        assertEquals("Students Owned Course", ownRow.path("courseTitle").asString());
        assertFalse(ownRow.path("purchaseDate").isMissingNode(), "purchaseDate column required");
        assertEquals("Active", ownRow.path("accessStatus").asString());
        assertFalse(ownRow.path("progress").isMissingNode(), "progress column required");

        // The peer faculty member's table does not contain that buyer row.
        mockMvc.perform(get("/api/faculty/students")
                        .header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].purchaseId",
                        not(hasItem((int) ownPurchase))));

        // Read-only: no mutation route exists for these rows at all (no handler
        // under /api/faculty/students/{id} -> 404, never a successful change).
        mockMvc.perform(delete("/api/faculty/students/" + ownPurchase)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/faculty/students/" + ownPurchase)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isNotFound());
        // Role/status changes remain behind the admin gate.
        mockMvc.perform(post("/api/admin/purchases/%d/reject".formatted(ownPurchase))
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"nope\"}"))
                .andExpect(status().isForbidden());
        // The purchase itself is untouched.
        mockMvc.perform(get("/api/purchases/my-courses")
                        .header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].courseId", hasItem((int) ownCourse)));
    }

    // ----------------------------------------------------- faculty resources

    @Test
    void facultyResourcesFollowTheSameOwnershipContract() throws Exception {
        long ownCourse = createFacultyCourse(facultyToken, "Resource Owned Course");
        long otherCourse = createFacultyCourse(otherFacultyToken, "Resource Foreign Course");

        // Create a video resource bundled with an owned course.
        MvcResult created = mockMvc.perform(post("/api/faculty/resources")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Intro Video","description":"Week 1",
                                 "kind":"VIDEO","courseId":%d,
                                 "fileUrl":"https://res.cloudinary.com/demo/video/upload/v1/intro.mp4"}
                                """.formatted(ownCourse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("VIDEO"))
                .andExpect(jsonPath("$.data.courseId").value((int) ownCourse))
                .andReturn();
        long resourceId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Listing carries only the caller's own resources.
        mockMvc.perform(get("/api/faculty/resources")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) resourceId)));
        mockMvc.perform(get("/api/faculty/resources")
                        .header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) resourceId))));

        // Attaching a resource to someone else's course -> 403.
        mockMvc.perform(post("/api/faculty/resources")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","kind":"ASSIGNMENT","courseId":%d,
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/h.pdf"}
                                """.formatted(otherCourse)))
                .andExpect(status().isForbidden());

        // Updating someone else's resource -> 403 (peer faculty + admin-created).
        mockMvc.perform(put("/api/faculty/resources/" + resourceId)
                        .header("Authorization", "Bearer " + otherFacultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","kind":"VIDEO",
                                 "fileUrl":"https://res.cloudinary.com/demo/video/upload/x.mp4"}
                                """))
                .andExpect(status().isForbidden());

        // The owner may edit their own resource (partial update keeps kind).
        mockMvc.perform(put("/api/faculty/resources/" + resourceId)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Intro Video (Edited)"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Intro Video (Edited)"))
                .andExpect(jsonPath("$.data.kind").value("VIDEO"));

        // A bad kind fails with 400; a missing fileUrl too.
        mockMvc.perform(post("/api/faculty/resources")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad Kind","kind":"MALWARE",
                                 "fileUrl":"https://example.com/x.pdf"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/faculty/resources")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No File","kind":"OTHER"}
                                """))
                .andExpect(status().isBadRequest());

        // No DELETE route exists under /api/faculty/resources (405)...
        mockMvc.perform(delete("/api/faculty/resources/" + resourceId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isMethodNotAllowed());

        // ...and students are excluded from the whole collection.
        mockMvc.perform(get("/api/faculty/resources")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/faculty/resources")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Student Resource","kind":"OTHER",
                                 "fileUrl":"https://example.com/s.pdf"}
                                """))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------- faculty courses

    @Test
    void facultyCanEditAndPublishTheirOwnCoursesOnly() throws Exception {
        long ownCourse = createFacultyCourse(facultyToken, "Publish Owned Course");
        long otherCourse = createFacultyCourse(otherFacultyToken, "Publish Foreign Course");

        // Owner edits their course and unpublishes it (active=false).
        mockMvc.perform(put("/api/faculty/courses/" + ownCourse)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Publish Owned Course (Edited)",
                                 "priceInr":499,"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Publish Owned Course (Edited)"))
                .andExpect(jsonPath("$.data.active").value(false));

        // The same edit on a peer's course -> 403, and nothing changes.
        mockMvc.perform(put("/api/faculty/courses/" + otherCourse)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","priceInr":1,"active":false}
                                """))
                .andExpect(status().isForbidden());

        // Re-publishing works the same way.
        mockMvc.perform(put("/api/faculty/courses/" + ownCourse)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Publish Owned Course (Edited)",
                                 "priceInr":499,"active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        // Unknown course -> 404; students are excluded entirely.
        mockMvc.perform(put("/api/faculty/courses/999999")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ghost","priceInr":1}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/faculty/courses/" + ownCourse)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Student Edit","priceInr":1}
                                """))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------ faculty notifications

    @Test
    void facultyNotificationReachesOnlyOwnEnrolledStudents() throws Exception {
        // Fresh students so each audience is exactly this test's data.
        String mine = registerStudent("faculty.notify.mine@example.com");
        String theirs = registerStudent("faculty.notify.theirs@example.com");

        long ownCourse = createFacultyCourse(facultyToken, "Notify Owned Course");
        long otherCourse = createFacultyCourse(otherFacultyToken, "Notify Foreign Course");
        long emptyCourse = createFacultyCourse(facultyToken, "Notify Empty Course");
        purchaseAndApprove(mine, ownCourse, "UTR-NOTIFY-OWN");
        purchaseAndApprove(theirs, otherCourse, "UTR-NOTIFY-FOREIGN");

        // Narrowing to a course the caller does not own -> 403.
        mockMvc.perform(post("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Foreign Broadcast","message":"x","courseId":%d}
                                """.formatted(otherCourse)))
                .andExpect(status().isForbidden());

        // Narrowing to one of my OWN courses with nobody enrolled -> 400:
        // the audience for that course is empty, so there is nothing to send.
        mockMvc.perform(post("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Nobody Yet","message":"x","courseId":%d}
                                """.formatted(emptyCourse)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("enrolled")));

        // Send narrowed to my own course: exactly my enrolled student.
        mockMvc.perform(post("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My Broadcast","message":"hello class",
                                 "kind":"UPDATE","category":"course","courseId":%d}
                                """.formatted(ownCourse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sentCount").value(1));

        // The sender's "sent" list carries it...
        mockMvc.perform(get("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("My Broadcast")));

        // ...my enrolled student sees it...
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("My Broadcast")));

        // ...and the other faculty's student never does.
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + theirs))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", not(hasItem("My Broadcast"))));

        // The peer's broadcast reaches only their own enrolled student.
        mockMvc.perform(post("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + otherFacultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Peer Broadcast","message":"hello",
                                 "courseId":%d}
                                """.formatted(otherCourse)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sentCount").value(1));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + theirs))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("Peer Broadcast")));
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", not(hasItem("Peer Broadcast"))));

        // Each "sent" list is scoped to its own sender - no cross-leak.
        mockMvc.perform(get("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("Peer Broadcast")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("My Broadcast"))));

        // Students may neither send nor read the faculty sent-list.
        mockMvc.perform(post("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Student Spam\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/faculty/notifications")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------- certificates & feedback scope

    @Test
    void facultySeeCertificatesAndFeedbackOnlyForTheirOwnCourses() throws Exception {
        long ownCourse = createFacultyCourse(facultyToken, "Scope Owned Course");
        long otherCourse = createFacultyCourse(otherFacultyToken, "Scope Foreign Course");

        // Admin issues one certificate per course (fileUrl is mandatory).
        long ownCertId = objectMapper.readTree(
                        mockMvc.perform(post("/api/admin/certificates")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"title":"Course Completion",
                                                 "courseId":%d,
                                                 "fileUrl":"https://res.cloudinary.com/demo/image/upload/own-cert.pdf"}
                                                """.formatted(ownCourse)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();
        long otherCertId = objectMapper.readTree(
                        mockMvc.perform(post("/api/admin/certificates")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"title":"Peer Completion",
                                                 "courseId":%d,
                                                 "fileUrl":"https://res.cloudinary.com/demo/image/upload/peer-cert.pdf"}
                                                """.formatted(otherCourse)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Owner sees exactly their own course's certificate.
        mockMvc.perform(get("/api/faculty/certificates")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) ownCertId)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) otherCertId))));

        mockMvc.perform(get("/api/faculty/certificates")
                        .header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem((int) otherCertId)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) ownCertId))));

        // Students cannot reach the faculty certificate list (they use /certificates/mine).
        mockMvc.perform(get("/api/faculty/certificates")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // Feedback: the student reviews both courses.
        mockMvc.perform(post("/api/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"rating":5,"message":"Great course"}
                                """.formatted(ownCourse)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/feedback")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"rating":2,"message":"Not for me"}
                                """.formatted(otherCourse)))
                .andExpect(status().isCreated());

        // Each faculty member sees reviews of their own courses only.
        mockMvc.perform(get("/api/faculty/feedback")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].courseId", hasItem((int) ownCourse)))
                .andExpect(jsonPath("$.data[*].courseId", not(hasItem((int) otherCourse))));

        mockMvc.perform(get("/api/faculty/feedback")
                        .header("Authorization", "Bearer " + otherFacultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].courseId", hasItem((int) otherCourse)))
                .andExpect(jsonPath("$.data[*].courseId", not(hasItem((int) ownCourse))));

        // Students are excluded from the faculty feedback route.
        mockMvc.perform(get("/api/faculty/feedback")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------ profile & logout

    @Test
    void facultyProfileRoundTripsAndFacultyIdStaysReadOnly() throws Exception {
        // Resolve the caller's own profile (id + system-assigned faculty id).
        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("FACULTY"))
                .andReturn();
        JsonNode profile = objectMapper.readTree(me.getResponse().getContentAsString())
                .path("data");
        long myUserId = profile.path("id").asLong();
        // Derive the expected staff id instead of hardcoding a sequence number -
        // the test database is shared, so raw user ids vary per run.
        String expectedFacultyId = String.format(java.util.Locale.ROOT,
                "FAC-%04d", myUserId);
        assertEquals(expectedFacultyId, profile.path("facultyId").asString(),
                "approved faculty get FAC-<zero-padded user id>");

        // ...and can edit the profile fields the Profile page exposes.
        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Faculty One","department":"Computer Science",
                                 "qualification":"Ph.D","experience":"8 years",
                                 "phone":"9876543210",
                                 "profilePhotoUrl":"https://res.cloudinary.com/demo/image/upload/avatar.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.department").value("Computer Science"))
                .andExpect(jsonPath("$.data.qualification").value("Ph.D"))
                .andExpect(jsonPath("$.data.experience").value("8 years"))
                .andExpect(jsonPath("$.data.profilePhotoUrl").value(
                        "https://res.cloudinary.com/demo/image/upload/avatar.png"))
                // The system-assigned id and role never change via profile edits.
                .andExpect(jsonPath("$.data.facultyId").value(expectedFacultyId))
                .andExpect(jsonPath("$.data.role").value("FACULTY"));

        // Email is deliberately read-only on this endpoint: an attempted change
        // is ignored, so the account email stays exactly as registered.
        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Faculty One",
                                 "email":"hijacked@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("faculty.one@example.com"));

        // Change password through the shared auth endpoint, verify the new one
        // works, then change it back so the suite keeps its original secret.
        mockMvc.perform(put("/api/auth/change-password")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Faculty@123","newPassword":"Faculty@456"}
                                """))
                .andExpect(status().isOk());
        String reissued = login("faculty.one@example.com", "Faculty@456");
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + reissued))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("FACULTY"))
                .andExpect(jsonPath("$.data.facultyId").value(expectedFacultyId));

        // A wrong current password is rejected with 400 before anything changes.
        mockMvc.perform(put("/api/auth/change-password")
                        .header("Authorization", "Bearer " + reissued)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongPass1","newPassword":"Faculty@789"}
                                """))
                .andExpect(status().isBadRequest());

        // Restore the original password for the rest of the suite.
        mockMvc.perform(put("/api/auth/change-password")
                        .header("Authorization", "Bearer " + reissued)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Faculty@456","newPassword":"Faculty@123"}
                                """))
                .andExpect(status().isOk());
    }
}
