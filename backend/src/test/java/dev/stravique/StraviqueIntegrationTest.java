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

import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    private static final String FILE_CSV_HEADER = "\"id\",\"path\",\"type\",\"moduleId\",\"sizeBytes\","
            + "\"encoding\",\"lastModifiedAt\",\"scanStatus\",\"parseStatus\",\"parseError\","
            + "\"frameworkApiCount\",\"generated\",\"changeType\",\"structureRole\",\"sha256\"";

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
        mockMvc.perform(get("/api/v1/projects/{id}/relations/overview", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(20)))
                .andExpect(jsonPath("$.byType.JAVA_IMPORT").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.byType.SEASAR_BINDING").value(
                        org.hamcrest.Matchers.greaterThan(0)));
        mockMvc.perform(get("/api/v1/projects/{id}/relations", projectId)
                        .param("relationType", "JAVA_IMPORT")
                        .param("certainty", "ANALYZED")
                        .param("q", "OrderService"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.content[0].sourceFilePath").isNotEmpty())
                .andExpect(jsonPath("$.content[0].targetFilePath").isNotEmpty());
        mockMvc.perform(get("/api/v1/projects/{id}/relations/graph", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationCount").value(
                        org.hamcrest.Matchers.greaterThan(20)))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.limit").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.nodes").isNotEmpty())
                .andExpect(jsonPath("$.edges").isNotEmpty());
        mockMvc.perform(get("/api/v1/projects/{id}/relations/graph", projectId)
                        .param("relationType", "JAVA_IMPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.relationCount").value(
                        org.hamcrest.Matchers.greaterThan(0)));
        mockMvc.perform(get("/api/v1/projects/{id}/relations/graph", projectId)
                        .param("relationType", "NOT_A_RELATION_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        String overviewResponse = mockMvc.perform(get("/api/v1/projects/{id}/overview", projectId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode overview = objectMapper.readTree(overviewResponse);
        int relationCount = overview.get("relationCount").asInt();
        assertThat(relationCount).isGreaterThan(20);
        assertThat(overview.get("linkedFileCount").asInt()).isGreaterThan(0);
        mockMvc.perform(get("/api/v1/projects/{id}/reports/RELATIONS.json", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                    assertThat(rows.size()).isEqualTo(relationCount);
                });

        String filesResponse = mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
                        .param("fileType", "JAVA")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String actionFileId = fileIdByPath(filesResponse,
                "web-app/src/main/java/com/example/legacy/action/OrderAction.java");
        mockMvc.perform(get("/api/v1/files/{id}", actionFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'JAVA_IMPORT' "
                        + "&& @.targetObjectKey == 'com.example.legacy.service.OrderService')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'JAVA_INHERITANCE' "
                        + "&& @.targetObjectKey == 'com.example.legacy.framework.BaseWorkflowController')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'SEASAR_BINDING' "
                        + "&& @.targetObjectKey == 'com.example.legacy.service.OrderService')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'MESSAGE_PRODUCTION' "
                        + "&& @.targetObjectKey == 'error.order.save')]").isNotEmpty())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'STRUTS_ACTION_TO_JSP')]")
                        .isNotEmpty());
        mockMvc.perform(get("/api/v1/files/{id}", actionFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'SASTRUTS_ACTION_TO_JSP' "
                        + "&& @.targetObjectKey == "
                        + "'web-app/src/main/webapp/WEB-INF/jsp/order/list.jsp')]").isNotEmpty());

        String daoFileId = fileIdByPath(filesResponse,
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java");
        mockMvc.perform(get("/api/v1/files/{id}", daoFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'DAO_SQL_REFERENCE' "
                        + "&& @.targetObjectKey == 'web-app/src/main/resources/sql/orders.sql')]")
                        .isNotEmpty());

        String jspResponse = mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
                        .param("fileType", "JSP")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jspFileId = fileIdByPath(jspResponse,
                "web-app/src/main/webapp/WEB-INF/jsp/order/list.jsp");
        mockMvc.perform(get("/api/v1/files/{id}", jspFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'JSP_FORM_TO_ACTION' "
                        + "&& @.certainty == 'INFERRED')]").isNotEmpty())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'MESSAGE_DISPLAY' "
                        + "&& @.targetObjectKey == 'common.search')]").isNotEmpty())
                .andExpect(jsonPath("$.findings[?(@.ruleId == 'JSP-FORM-FIELD-001')]").isNotEmpty());
        String editJspId = fileIdByPath(jspResponse,
                "web-app/src/main/webapp/WEB-INF/jsp/order/edit.jsp");
        mockMvc.perform(get("/api/v1/files/{id}", editJspId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'JSP_FORM_TO_ACTION' "
                        + "&& @.targetObjectKey == 'com.example.legacy.action.OrderAction')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.findings[?(@.ruleId == 'JSP-FORM-FIELD-001')]").isNotEmpty());

        String diconResponse = mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
                        .param("fileType", "DICON")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String diconFileId = fileIdByPath(diconResponse,
                "web-app/src/main/webapp/WEB-INF/dicon/app.dicon");
        mockMvc.perform(get("/api/v1/files/{id}", diconFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'SEASAR_DICON_INJECTION' "
                        + "&& @.targetObjectKey == 'com.example.legacy.dao.OrderDao')]")
                        .isNotEmpty());

        String propertiesResponse = mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
                        .param("fileType", "PROPERTIES")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String propertiesFileId = fileIdByPath(propertiesResponse,
                "web-app/src/main/resources/config/classes.properties");
        mockMvc.perform(get("/api/v1/files/{id}", propertiesFileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relations[?(@.relationType == 'CONFIG_CLASS_REFERENCE' "
                        + "&& @.targetObjectKey == 'com.example.legacy.action.OrderAction')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.relations[0].certainty").value("INFERRED"));

        mockMvc.perform(get("/api/v1/projects/{id}/frameworks/SASTRUTS", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technology.status").value("ANALYZED"))
                .andExpect(jsonPath("$.evidence[?(@.certainty == 'ANALYZED')]").isNotEmpty());
        mockMvc.perform(get("/api/v1/projects/{id}/frameworks/STRUTS1", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[?(@.ruleId == 'STRUTS-CONFIG-ACTION-001')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.evidence[?(@.ruleId == 'STRUTS-CONFIG-FORWARD-001')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.evidence[?(@.ruleId == 'STRUTS-CONFIG-MESSAGES-001')]")
                        .isNotEmpty());
        mockMvc.perform(get("/api/v1/projects/{id}/frameworks/SEASAR2", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence[?(@.ruleId == 'DICON-COMPONENT-001')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.evidence[?(@.ruleId == 'DICON-LIFECYCLE-001')]")
                        .isNotEmpty());

        mockMvc.perform(get("/api/v1/projects/{id}/reviews", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.objectKey == 'DYNAMIC_CLASS_LOADING')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.csv", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertThat(body[0]).isEqualTo((byte) 0xEF);
                    assertThat(body[1]).isEqualTo((byte) 0xBB);
                    assertThat(body[2]).isEqualTo((byte) 0xBF);
                    String csv = decode(body);
                    assertThat(csv.lines().findFirst().orElse("")).isEqualTo(FILE_CSV_HEADER);
                    assertThat(csv).contains("legacy-broken.xml");
                });

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.csv", projectId)
                        .param("lang", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String csv = decode(result.getResponse().getContentAsByteArray());
                    assertThat(csv.lines().findFirst().orElse("")).isEqualTo(FILE_CSV_HEADER);
                    assertThat(csv).doesNotContain("路径");
                });

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.csv", projectId)
                        .header("Accept-Language", "ja-JP"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(decode(result.getResponse().getContentAsByteArray())
                        .lines().findFirst().orElse("")).isEqualTo(FILE_CSV_HEADER));

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.markdown", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String markdown = decode(result.getResponse().getContentAsByteArray());
                    assertThat(markdown).startsWith("# File inventory");
                    assertThat(markdown).contains("| Path |");
                });

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.markdown", projectId)
                        .param("lang", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String markdown = decode(result.getResponse().getContentAsByteArray());
                    assertThat(markdown).startsWith("# 文件清单");
                    assertThat(markdown).contains("| 路径 |");
                    assertThat(markdown).doesNotContain("| Path |");
                });

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.markdown", projectId)
                        .param("lang", "ja-JP"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(decode(
                        result.getResponse().getContentAsByteArray())).startsWith("# ファイル棚卸"));

        mockMvc.perform(get("/api/v1/projects/{id}/reports/TECHNOLOGY.json", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                    assertThat(rows.isArray()).isTrue();
                    assertThat(rows.size()).isEqualTo(completed.get("technologyCount").asInt());
                });

        mockMvc.perform(get("/api/v1/projects/{id}/reports/TECHNOLOGY.json", projectId)
                        .param("lang", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                    assertThat(rows.get(0).has("technologyKey")).isTrue();
                    assertThat(rows.get(0).get("technologyKey").isTextual()).isTrue();
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
    void exportsHeaderOnlyCsvWhenNoScanHasRun() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Empty Report Project",
                                "sourcePath", SAMPLE.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.csv", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(decode(
                        result.getResponse().getContentAsByteArray()).strip()).isEqualTo(FILE_CSV_HEADER));

        mockMvc.perform(get("/api/v1/projects/{id}/reports/FILES.markdown", projectId)
                        .param("lang", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(decode(result.getResponse().getContentAsByteArray()))
                        .contains("# 文件清单", "该报告暂无持久化数据行。"));

        mockMvc.perform(get("/api/v1/projects/{id}/reports/TECHNOLOGY.json", projectId))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(objectMapper.readTree(
                        result.getResponse().getContentAsByteArray()).isEmpty()).isTrue());
    }

    @Test
    void generatesTraceableMigrationPlanWithRealDependenciesAndScopeGuard() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Migration Fixture",
                                "sourcePath", SAMPLE.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();
        String scanResponse = mockMvc.perform(post("/api/v1/projects/{id}/scans", projectId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode completed = awaitScan(objectMapper.readTree(scanResponse).get("id").asText());
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");

        String planResponse = mockMvc.perform(post("/api/v1/projects/{id}/migration-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfile\":\"SPRING_BOOT_MVC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.targetProfile").value("SPRING_BOOT_MVC"))
                .andExpect(jsonPath("$.summary.sourceProfile").value(
                        org.hamcrest.Matchers.containsString("SASTRUTS")))
                .andExpect(jsonPath("$.summary.sourceProfile").value(
                        org.hamcrest.Matchers.containsString("STRUTS1")))
                .andExpect(jsonPath("$.summary.itemCount").value(
                        org.hamcrest.Matchers.greaterThan(8)))
                .andExpect(jsonPath("$.summary.stale").value(false))
                .andExpect(jsonPath("$.summary.phaseCount").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.phases").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode plan = objectMapper.readTree(planResponse);
        String planId = plan.at("/summary/id").asText();
        int planItemCount = plan.at("/summary/itemCount").asInt();

        String itemsResponse = mockMvc.perform(
                        get("/api/v1/projects/{id}/migration-plans/{plan}/items", projectId, planId)
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(itemsResponse).get("content");
        assertThat(items.size()).isEqualTo(planItemCount);
        java.util.Set<String> types = new java.util.HashSet<>();
        java.util.Set<String> statuses = new java.util.HashSet<>();
        for (JsonNode item : items) {
            types.add(item.get("itemType").asText());
            statuses.add(item.get("status").asText());
        }
        assertThat(types).contains("ACTION_CONTROLLER", "COMPONENT_BEAN", "DATA_ACCESS",
                "MESSAGE_FLOW", "MESSAGE_RESOURCE", "JSP_VIEW", "CONFIGURATION", "REVIEW");
        assertThat(statuses).contains("READY", "REVIEW_REQUIRED", "BLOCKED");

        JsonNode actionItem = null;
        for (JsonNode item : items) {
            if (item.get("itemType").asText().equals("ACTION_CONTROLLER")
                    && item.get("title").asText().contains("OrderAction")) {
                actionItem = item;
                break;
            }
        }
        assertThat(actionItem).isNotNull();
        String actionItemId = actionItem.get("id").asText();
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                        projectId, planId, actionItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings").isNotEmpty())
                .andExpect(jsonPath("$.item.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.dependencies").isNotEmpty())
                .andExpect(jsonPath("$.dependencies[?(@.fromItemId == '"
                        + actionItemId + "')]").isNotEmpty());

        String migrationGraphResponse = mockMvc.perform(
                        get("/api/v1/projects/{id}/migration-plans/{plan}/graph", projectId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isNotEmpty())
                .andExpect(jsonPath("$.edges").isNotEmpty())
                .andExpect(jsonPath("$.itemLimit").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.edgeLimit").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.truncated").value(false))
                .andReturn().getResponse().getContentAsString();

        String dependentsItemId = null;
        String leafItemId = null;
        for (JsonNode item : items) {
            if (item.get("dependentCount").asInt() > 0 && dependentsItemId == null) {
                dependentsItemId = item.get("id").asText();
            }
            if (item.get("dependentCount").asInt() == 0 && item.get("included").asBoolean()) {
                leafItemId = item.get("id").asText();
            }
        }
        assertThat(dependentsItemId).isNotNull();
        assertThat(leafItemId).isNotNull();
        mockMvc.perform(patch("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                        projectId, planId, dependentsItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"included\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MIGRATION_DEPENDENCY_CONFLICT"));
        mockMvc.perform(patch("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                        projectId, planId, leafItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"included\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.included").value(false));
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}", projectId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.includedCount").value(
                        org.hamcrest.Matchers.lessThan(planItemCount)));
        mockMvc.perform(patch("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                        projectId, planId, leafItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"included\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.included").value(true));

        JsonNode migrationGraph = objectMapper.readTree(migrationGraphResponse);
        java.util.List<String> actionDependents = new java.util.ArrayList<>();
        for (JsonNode edge : migrationGraph.get("edges")) {
            if (edge.get("source").asText().equals(actionItemId)) {
                actionDependents.add(edge.get("target").asText());
            }
        }
        assertThat(actionDependents).isNotEmpty();
        for (String dependentId : actionDependents) {
            mockMvc.perform(patch("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                            projectId, planId, dependentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"included\":false}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(patch("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                        projectId, planId, actionItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"included\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                        projectId, planId, actionDependents.getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"included\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MIGRATION_PREREQUISITE_EXCLUDED"));

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfile\":\"UNSUPPORTED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MIGRATION_TARGET_UNSUPPORTED"));
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}",
                        projectId, "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MIGRATION_PLAN_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planId));
    }

    @Test
    void previewsAppliesAndRestoresAVettedTransformationOnACopy() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build", "transformation-fixture")
                .toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        Path transformedFile = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java");
        byte[] originalBytes = Files.readAllBytes(transformedFile);
        byte[] fixtureBytes = Files.readAllBytes(SAMPLE.resolve(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java"));

        String projectResponse = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Transformation Copy",
                                "sourcePath", tempProject.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();
        String scanResponse = mockMvc.perform(post("/api/v1/projects/{id}/scans", projectId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode scan = awaitScan(objectMapper.readTree(scanResponse).get("id").asText());
        assertThat(scan.get("status").asText()).isEqualTo("COMPLETED");

        String planResponse = mockMvc.perform(post("/api/v1/projects/{id}/migration-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfile\":\"SPRING_BOOT_MVC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String planId = objectMapper.readTree(planResponse).at("/summary/id").asText();
        String itemsResponse = mockMvc.perform(
                        get("/api/v1/projects/{id}/migration-plans/{plan}/items", projectId, planId)
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(itemsResponse).get("content");
        JsonNode component = null;
        JsonNode action = null;
        for (JsonNode item : items) {
            if (item.get("itemType").asText().equals("COMPONENT_BEAN")
                    && item.get("title").asText().equals("OrderService.java")) {
                component = item;
            }
            if (item.get("itemType").asText().equals("ACTION_CONTROLLER")) {
                action = item;
            }
        }
        assertThat(component).isNotNull();
        assertThat(action).isNotNull();
        String componentId = component.get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                        + "transformation-proposals", projectId, planId,
                        action.get("id").asText()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_UNSUPPORTED"));

        String proposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, componentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("org.springframework")))
                .andReturn().getResponse().getContentAsString();
        JsonNode proposal = objectMapper.readTree(proposalResponse);
        String proposalId = proposal.get("id").asText();
        String appliedSha = proposal.get("proposedSha256").asText();

        mockMvc.perform(post("/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, proposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_CONFIRMATION_REQUIRED"));
        String runResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, proposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedSha256").value(appliedSha))
                .andReturn().getResponse().getContentAsString();
        String runId = objectMapper.readTree(runResponse).get("id").asText();
        byte[] appliedBytes = Files.readAllBytes(transformedFile);
        assertThat(appliedBytes).isNotEqualTo(originalBytes);
        assertThat(new String(appliedBytes, StandardCharsets.UTF_8))
                .contains("org.springframework.stereotype.Component")
                .contains("org.springframework.beans.factory.annotation.Autowired")
                .doesNotContain("org.seasar.framework");
        assertThat(Files.readAllBytes(SAMPLE.resolve(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java")))
                .isEqualTo(fixtureBytes);
        try (Stream<Path> backups = Files.walk(Path.of(System.getProperty("user.dir"),
                "build", "test-transformation-backups"))) {
            assertThat(backups.anyMatch(Files::isRegularFile)).isTrue();
        }

        mockMvc.perform(post("/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, proposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_ALREADY_APPLIED"));
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(transformedFile)).isEqualTo(originalBytes);
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                        + "transformation-runs", projectId, planId, componentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESTORED"));

        String secondProposal = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, componentId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Files.writeString(transformedFile, new String(originalBytes, StandardCharsets.UTF_8) + "\n");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, objectMapper.readTree(secondProposal).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCE_CHANGED"));

        deleteRecursively(tempProject);
    }

    private void copyRecursively(Path source, Path target) throws Exception {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }

    private void deleteRecursively(Path target) throws Exception {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(target)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void requiresACompletedScanBeforePlanning() throws Exception {
        String projectResponse = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "No Scan Migration Project",
                                "sourcePath", SAMPLE.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MIGRATION_SCAN_REQUIRED"));
    }

    @Test
    void returnsStableLocalizedErrorKeys() throws Exception {
        Map<String, String> expected = Map.of(
                "en-US", "The requested project was not found.",
                "zh-CN", "未找到指定的分析项目。",
                "ja-JP", "指定された分析プロジェクトが見つかりません。");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            mockMvc.perform(get("/api/v1/projects/{id}", "00000000-0000-0000-0000-000000000000")
                            .header("Accept-Language", entry.getKey()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.messageKey").value("error.project.notFound"))
                    .andExpect(jsonPath("$.message").value(entry.getValue()))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
    }

    @Test
    void configuredDefaultLocaleDrivesErrorsWithoutHeader() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("The requested project was not found."));
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

    private String fileIdByPath(String response, String path) throws Exception {
        for (JsonNode file : objectMapper.readTree(response).get("content")) {
            if (path.equals(file.get("relativePath").asText())) {
                return file.get("id").asText();
            }
        }
        throw new AssertionError("File not found in scan response: " + path);
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

    private static String decode(byte[] body) {
        int offset = body.length >= 3
                && body[0] == (byte) 0xEF && body[1] == (byte) 0xBB && body[2] == (byte) 0xBF ? 3 : 0;
        return new String(body, offset, body.length - offset, StandardCharsets.UTF_8);
    }

    private record Snapshot(String hash, long modifiedAt, long size) {
    }
}
