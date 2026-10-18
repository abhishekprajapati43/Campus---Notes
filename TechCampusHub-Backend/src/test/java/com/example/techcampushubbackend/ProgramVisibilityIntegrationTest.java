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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Program (department) scoping for PYQs and timetables, exercised through
 * the full HTTP stack with real JWTs: an admin uploads one paper per
 * program, and two students - one B.Tech, one MCA - must each only ever
 * see their own program's content, never the other's.
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

    private long btechPyqId;
    private long mcaPyqId;
    private long btechTimetableId;
    private long mcaTimetableId;

    @BeforeAll
    void uploadOnePaperPerProgram() throws Exception {
        adminToken = login("admin@test.com", "Admin@1234");

        registerStudent("Program BTech Student", "prog.btech@example.com", "B.Tech", "CSE");
        btechToken = login("prog.btech@example.com", "Student@123");

        registerStudent("Program MCA Student", "prog.mca@example.com", "MCA", "CA");
        mcaToken = login("prog.mca@example.com", "Student@123");

        btechPyqId = createPyq("B.Tech DBMS PYQ", "B.Tech");
        mcaPyqId = createPyq("MCA Java PYQ", "MCA");
        btechTimetableId = createTimetable("B.Tech");
        mcaTimetableId = createTimetable("MCA");
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
    void btechStudentSeesOnlyBtechPapers() throws Exception {
        mockMvc.perform(get("/api/pyqs").header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaPyqId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/timetables").header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechTimetableId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaTimetableId + ")]", hasSize(0)));
    }

    @Test
    void mcaStudentSeesOnlyMcaPapers() throws Exception {
        mockMvc.perform(get("/api/pyqs").header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + mcaPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechPyqId + ")]", hasSize(0)));

        mockMvc.perform(get("/api/timetables").header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + mcaTimetableId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + btechTimetableId + ")]", hasSize(0)));
    }

    // -------------------------------------------------- cross-program direct id

    @Test
    void crossProgramDetailAndDownloadAre404() throws Exception {
        // B.Tech student probing MCA rows by id: metadata AND bytes.
        mockMvc.perform(get("/api/pyqs/" + mcaPyqId)
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pyqs/" + mcaPyqId + "/download")
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/timetables/" + mcaTimetableId)
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isNotFound());

        // MCA student probing a B.Tech row.
        mockMvc.perform(get("/api/pyqs/" + btechPyqId)
                        .header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/timetables/" + btechTimetableId + "/download")
                        .header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isNotFound());

        // Own program's rows stay readable (regression guard).
        mockMvc.perform(get("/api/pyqs/" + btechPyqId)
                        .header("Authorization", "Bearer " + btechToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/timetables/" + mcaTimetableId)
                        .header("Authorization", "Bearer " + mcaToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------- staff unaffected

    @Test
    void adminStillSeesEveryProgram() throws Exception {
        mockMvc.perform(get("/api/admin/pyqs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + btechPyqId + ")]", hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.id == " + mcaPyqId + ")]", hasSize(1)));
    }
}
