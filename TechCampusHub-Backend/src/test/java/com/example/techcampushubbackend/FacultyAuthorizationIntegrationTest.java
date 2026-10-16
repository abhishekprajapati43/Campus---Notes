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
}
