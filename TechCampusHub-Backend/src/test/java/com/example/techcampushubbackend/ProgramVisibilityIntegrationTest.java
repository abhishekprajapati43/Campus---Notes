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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Program (department) scoping for notes, PYQs and timetables, exercised
 * through the full HTTP stack with real JWTs: an admin uploads one item per
 * program (MCA / B.Tech / BCA), and three students - one per program - must
 * each only ever see their own program's content, never another's. TNP
 * announcements stay shared across every program, and favorites inherit the
 * same scoping (a BCA student cannot even favorite an MCA note).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgramVisibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String btechToken;
    private String mcaToken;
    private String bcaToken;

    private long btechNoteId;
    private long mcaNoteId;
    private long bcaNoteId;
    private long btechPyqId;
    private long mcaPyqId;
    private long bcaPyqId;
    private long btechTimetableId;
    private long mcaTimetableId;
    private long bcaTimetableId;

    @BeforeAll
    void uploadOneItemPerProgram() throws Exception {
        adminToken = login("admin@test.com", "Admin@1234");

        registerStudent("Program BTech Student", "prog.btech@example.com", "B.Tech", "CSE");
        btechToken = login("prog.btech@example.com", "Student@123");

        registerStudent("Program MCA Student", "prog.mca@example.com", "MCA", "CA");
        mcaToken = login("prog.mca@example.com", "Student@123");

        registerStudent("Program BCA Student", "prog.bca@example.com", "BCA", "CA");
        bcaToken = login("prog.bca@example.com", "Student@123");

        btechNoteId = createNote("B.Tech DBMS Notes", "B.Tech");
        mcaNoteId = createNote("MCA Java Notes", "MCA");
        bcaNoteId = createNote("BCA Python Notes", "BCA");

        btechPyqId = createPyq("B.Tech DBMS PYQ", "B.Tech");
        mcaPyqId = createPyq("MCA Java PYQ", "MCA");
        bcaPyqId = createPyq("BCA Python PYQ", "BCA");

        btechTimetableId = createTimetable("B.Tech");
        mcaTimetableId = createTimetable("MCA");
        bcaTimetableId = createTimetable("BCA");
    }

    private void registerStudent(String name, String email, String course, String branch)
            throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"Student@123",
                                 "course":"%s","branch":"%s","year":1,"semester":2}
                                """.formatted(name, email, course, branch)))
                .andExpect(status().isCreated());
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

    private long createNote(String title, String program) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","subject":"Subject","course":"%s",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/bundle.pdf"}
                                """.formatted(title, program)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createPyq(String title, String program) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/pyqs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","subject":"Subject","course":"%s",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/bundle.pdf"}
                                """.formatted(title, program)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createTimetable(String program) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/timetables")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"course":"%s","examType":"End Sem","examYear":2026,
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/bundle.pdf"}
                                """.formatted(program)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    // ------------------------------------------------------- upload contract

    @Test
    void pyqUploadWithoutProgramIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/pyqs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No Program Paper","subject":"X",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/bundle.pdf"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timetableUploadWithoutProgramIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/timetables")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"examType":"Mid Sem","examYear":2026,
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/bundle.pdf"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------- student listings

    @Test
    void btechStudentSeesOnlyBtechContent() throws Exception {
        mockMvc.perform(get("/api/notes").header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechNoteId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaNoteId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaNoteId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/pyqs").header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaPyqId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaPyqId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/timetables").header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechTimetableId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaTimetableId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaTimetableId + ")]", hasSize(0)));
    }

    @Test
    void mcaStudentSeesOnlyMcaContent() throws Exception {
        mockMvc.perform(get("/api/notes").header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + mcaNoteId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechNoteId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaNoteId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/pyqs").header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + mcaPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechPyqId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaPyqId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/timetables").header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + mcaTimetableId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechTimetableId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaTimetableId + ")]", hasSize(0)));
    }

    @Test
    void bcaStudentSeesOnlyBcaContent() throws Exception {
        mockMvc.perform(get("/api/notes").header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + bcaNoteId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechNoteId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaNoteId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/pyqs").header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + bcaPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechPyqId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaPyqId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/timetables").header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + bcaTimetableId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechTimetableId + ")]", hasSize(0)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaTimetableId + ")]", hasSize(0)));
    }

    // -------------------------------------------------- cross-program direct id

    @Test
    void crossProgramDetailAndDownloadAre404() throws Exception {
        // B.Tech student probing MCA/BCA rows by id: metadata AND bytes.
        mockMvc.perform(get("/api/notes/" + mcaNoteId)
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pyqs/" + bcaPyqId)
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/timetables/" + bcaTimetableId + "/download")
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isNotFound());

        // MCA student probing a B.Tech/BCA row.
        mockMvc.perform(get("/api/notes/" + bcaNoteId)
                        .header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pyqs/" + btechPyqId)
                        .header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isNotFound());

        // BCA student probing an MCA/B.Tech row.
        mockMvc.perform(get("/api/notes/" + mcaNoteId)
                        .header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pyqs/" + btechPyqId)
                        .header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/timetables/" + mcaTimetableId)
                        .header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isNotFound());

        // Own program's rows stay readable (regression guard).
        mockMvc.perform(get("/api/notes/" + btechNoteId)
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notes/" + bcaNoteId)
                        .header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/timetables/" + mcaTimetableId)
                        .header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- TNP shared

    @Test
    void tnpContentIsSharedAcrossAllPrograms() throws Exception {
        // One TNP announcement, uploaded with no program at all - every
        // program's student must see it (TNP is never course-scoped).
        long tnpId = createTnpNote("Campus Drive Announcement");

        for (String token : new String[] {btechToken, mcaToken, bcaToken}) {
            mockMvc.perform(get("/api/tnp-notes").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == " + tnpId + ")]", hasSize(1)));
        }
    }

    private long createTnpNote(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/tnp-notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","topic":"Placements",
                                 "fileUrl":"https://res.cloudinary.com/demo/raw/upload/bundle.pdf"}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    // ------------------------------------------------------------ favorites

    @Test
    void favoritesRespectProgramScoping() throws Exception {
        // A BCA student can favorite their own note...
        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + bcaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"NOTE","itemId":%d}
                                """.formatted(bcaNoteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.itemTitle").value("BCA Python Notes"));

        // ...but cannot favorite another program's note, PYQ or timetable
        // (same 404 as a missing id, so ids cannot be probed).
        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + bcaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"NOTE","itemId":%d}
                                """.formatted(mcaNoteId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + bcaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"PYQ","itemId":%d}
                                """.formatted(btechPyqId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + bcaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"TIMETABLE","itemId":%d}
                                """.formatted(mcaTimetableId)))
                .andExpect(status().isNotFound());

        // The BCA favorites list shows only the BCA item.
        mockMvc.perform(get("/api/favorites").header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.itemTitle == 'BCA Python Notes')]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.itemTitle == 'MCA Java Notes')]", hasSize(0)));

        // Clean up so re-runs and other tests are unaffected.
        mockMvc.perform(delete("/api/favorites/NOTE/" + bcaNoteId)
                        .header("Authorization", "Bearer " + bcaToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------- staff unaffected

    @Test
    void adminStillSeesEveryProgram() throws Exception {
        mockMvc.perform(get("/api/admin/notes").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechNoteId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaNoteId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaNoteId + ")]", hasSize(1)));

        mockMvc.perform(get("/api/admin/pyqs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + bcaPyqId + ")]", hasSize(1)));
    }
}
