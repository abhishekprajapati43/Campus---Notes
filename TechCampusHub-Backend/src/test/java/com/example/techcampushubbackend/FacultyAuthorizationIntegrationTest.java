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
                .andExpect(jsonPath("$.data.role").value("FACULTY"));
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
                .andExpect(status().isCreated());
        otherFacultyToken = login("faculty.two@example.com", "Faculty@123");

        // Plain student for the negative-path tests.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Faculty Test Student","email":"faculty.student@example.com",
                                 "password":"Student@123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
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
}
