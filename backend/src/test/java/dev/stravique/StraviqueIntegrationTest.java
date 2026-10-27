package dev.stravique;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StraviqueIntegrationTest {

    private static final Path SAMPLE = Path.of(System.getProperty("user.dir"))
            .getParent().resolve("sample-projects/legacy-web-demo").normalize();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE analysis_project, audit_event CASCADE");
    }

    @Test
    void scansFixtureWithTraceableEvidenceAndExportsPersistedRows() throws Exception {
        Map<String, Snapshot> before = snapshot(SAMPLE);
        String projectBody = objectMapper.writeValueAsString(Map.of(
                "name", "Legacy Integration Fixture",
                "description", "Automated inventory fixture",
                "sourcePath", SAMPLE.toString()
        ));
        String projectResponse = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourcePath").value(SAMPLE.toRealPath().toString()))
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();
        mockMvc.perform(put("/api/v1/projects/{id}/path-overrides", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"relativePath":"web-app/views","role":"JSP_ROOT"}]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("JSP_ROOT"));

        String scanResponse = mockMvc.perform(post("/api/v1/projects/{id}/scans", projectId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String scanId = objectMapper.readTree(scanResponse).get("id").asText();
        JsonNode completed = awaitScan(scanId);

        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("fileCount").asInt()).isGreaterThanOrEqualTo(20);
        assertThat(completed.get("moduleCount").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(completed.get("parseFailureCount").asInt()).isEqualTo(1);
        assertThat(completed.get("skippedCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(completed.get("technologyCount").asInt()).isGreaterThanOrEqualTo(10);
        String databaseVersion = jdbcTemplate.queryForObject("SELECT version()", String.class);
        assertThat(databaseVersion).contains("PostgreSQL");
        Integer scanAuditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_event WHERE entity_id = ? AND action IN ('SCAN_QUEUED', 'SCAN_COMPLETED')",
                Integer.class,
                scanId
        );
        assertThat(scanAuditCount).isEqualTo(2);
        mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
                        .param("pathPrefix", "web-app/views")
                        .param("fileType", "JSP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].structureRole").value("JSP_ROOT"));
        mockMvc.perform(get("/api/v1/projects/{id}/module-dependencies", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fromModulePath == 'web-app' "
                        + "&& @.toModulePath == 'domain' "
                        + "&& @.dependencyType == 'MAVEN_MODULE')]").isNotEmpty())
                .andExpect(jsonPath("$[0].evidencePath").isNotEmpty())
                .andExpect(jsonPath("$[0].lineStart").isNumber());

        mockMvc.perform(get("/api/v1/projects/{id}/frameworks/SASTRUTS", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technology.status").value("ANALYZED"))
                .andExpect(jsonPath("$.evidence[?(@.certainty == 'ANALYZED')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/projects/{id}/reviews", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.objectKey == 'DYNAMIC_CLASS_LOADING')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.csv", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("path", "legacy-broken.xml"));

        mockMvc.perform(get("/api/v1/projects/{id}/reports/TECHNOLOGY.json", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                    assertThat(rows.isArray()).isTrue();
                    assertThat(rows.size()).isEqualTo(completed.get("technologyCount").asInt());
                });
        mockMvc.perform(get("/api/v1/projects/{id}/audit-events", projectId)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'PROJECT_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action == 'SCAN_QUEUED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action == 'SCAN_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.action == 'REPORT_EXPORTED')]").isNotEmpty());

        assertThat(snapshot(SAMPLE)).isEqualTo(before);
    }

    @Test
    void returnsStableLocalizedErrorKeys() throws Exception {
        for (String language : new String[]{"en-US", "zh-CN", "ja-JP"}) {
            mockMvc.perform(get("/api/v1/projects/{id}", "00000000-0000-0000-0000-000000000000")
                            .header("Accept-Language", language))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.messageKey").value("error.project.notFound"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
    }

    @Test
    void rejectsSourceOutsideAllowedRoots() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Forbidden Path",
                                "sourcePath", "/private/etc"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_PATH_FORBIDDEN"));
    }

    @Test
    void appliesConfigurationOnlyAnalysisScope() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Configuration Scope",
                                "sourcePath", SAMPLE.toString(),
                                "analysisScope", "CONFIGURATION_ONLY"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();

        String scanResponse = mockMvc.perform(post("/api/v1/projects/{id}/scans", projectId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode completed = awaitScan(objectMapper.readTree(scanResponse).get("id").asText());
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");

        mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
                        .param("fileType", "JAVA")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode content = objectMapper.readTree(
                            result.getResponse().getContentAsByteArray()).get("content");
                    assertThat(content).isNotEmpty();
                    content.forEach(file ->
                            assertThat(file.get("scanStatus").asText()).isEqualTo("SKIPPED"));
                });
        mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
                        .param("fileType", "DICON")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode content = objectMapper.readTree(
                            result.getResponse().getContentAsByteArray()).get("content");
                    assertThat(content).isNotEmpty();
                    content.forEach(file ->
                            assertThat(file.get("scanStatus").asText()).isEqualTo("ANALYZED"));
                });
    }

    private JsonNode awaitScan(String scanId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            String response = mockMvc.perform(get("/api/v1/scans/{id}", scanId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            last = objectMapper.readTree(response);
            String status = last.get("status").asText();
            if (status.equals("COMPLETED") || status.equals("FAILED") || status.equals("CANCELLED")) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Scan did not finish: " + last);
    }

    private Map<String, Snapshot> snapshot(Path root) throws Exception {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                byte[] content = Files.readAllBytes(path);
                String hash = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(content));
                String relative = root.relativize(path).toString().replace('\\', '/');
                result.put(relative, new Snapshot(hash,
                        Files.getLastModifiedTime(path).toMillis(), content.length));
            }
        }
        return result;
    }

    private record Snapshot(String hash, long modifiedAt, long size) {
    }
}
