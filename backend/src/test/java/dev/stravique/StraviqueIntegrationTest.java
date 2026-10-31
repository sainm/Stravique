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
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Autowired
    dev.stravique.application.PlanRunInterruptionMarker interruptionMarker;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE analysis_project, audit_event, "
                + "transformation_plan_run, transformation_plan_run_item CASCADE");
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
                "MESSAGE_FLOW", "MESSAGE_RESOURCE", "FORM_MODEL", "VALIDATION",
                "JAKARTA_NAMESPACE", "JSP_VIEW", "CONFIGURATION", "REVIEW");
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
        Path validationFile = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/action/OrderAction.java");
        Path formFile = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/dto/OrderForm.java");
        Path jakartaFile = tempProject.resolve(
                "domain/src/main/java/com/example/legacy/domain/Order.java");
        Path queryDaoFile = tempProject.resolve(
                "domain/src/main/java/com/example/legacy/dao/OrderQueryDao.java");
        Path daoFile = tempProject.resolve(
                "domain/src/main/java/com/example/legacy/dao/OrderDao.java");
        Path messageFile = tempProject.resolve(
                "web-app/src/main/resources/messages.properties");
        Path jspFile = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/jsp/order/edit.jsp");
        Path listJspFile = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/jsp/order/list.jsp");
        Path webXmlFile = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/web.xml");
        Path strutsConfigFile = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/struts-config.xml");
        Path applicationYmlFile = tempProject.resolve(
                "web-app/src/main/resources/application.yml");
        byte[] originalBytes = Files.readAllBytes(transformedFile);
        Set<PosixFilePermission> originalPermissions =
                Files.getPosixFilePermissions(transformedFile);
        byte[] originalValidationBytes = Files.readAllBytes(validationFile);
        byte[] originalFormBytes = Files.readAllBytes(formFile);
        byte[] originalJakartaBytes = Files.readAllBytes(jakartaFile);
        byte[] originalQueryDaoBytes = Files.readAllBytes(queryDaoFile);
        byte[] originalDaoBytes = Files.readAllBytes(daoFile);
        byte[] originalMessageBytes = Files.readAllBytes(messageFile);
        byte[] originalJspBytes = Files.readAllBytes(jspFile);
        byte[] originalListJspBytes = Files.readAllBytes(listJspFile);
        byte[] originalWebXmlBytes = Files.readAllBytes(webXmlFile);
        byte[] originalStrutsConfigBytes = Files.readAllBytes(strutsConfigFile);
        byte[] originalApplicationYmlBytes = Files.readAllBytes(applicationYmlFile);
        byte[] fixtureBytes = Files.readAllBytes(SAMPLE.resolve(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java"));
        byte[] webXmlWithServlet = new String(originalWebXmlBytes, StandardCharsets.UTF_8)
                .replace("    <welcome-file-list>",
                        "    <servlet>\n"
                                + "        <servlet-name>legacy</servlet-name>\n"
                                + "        <servlet-class>javax.servlet.http.HttpServlet</servlet-class>\n"
                                + "    </servlet>\n"
                                + "    <error-page>\n"
                                + "        <exception-type>javax.servlet.ServletException</exception-type>\n"
                                + "        <location>/error.jsp</location>\n"
                                + "    </error-page>\n"
                                + "    <welcome-file-list>")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(webXmlFile, webXmlWithServlet);

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
        JsonNode validation = null;
        JsonNode form = null;
        JsonNode jakarta = null;
        JsonNode queryDao = null;
        JsonNode daoData = null;
        JsonNode transaction = null;
        JsonNode messageFlow = null;
        JsonNode messageResource = null;
        JsonNode jspView = null;
        JsonNode listJspView = null;
        JsonNode webXml = null;
        JsonNode strutsConfig = null;
        for (JsonNode item : items) {
            if (item.get("itemType").asText().equals("COMPONENT_BEAN")
                    && item.get("title").asText().equals("OrderService.java")) {
                component = item;
            }
            if (item.get("itemType").asText().equals("ACTION_CONTROLLER")) {
                action = item;
            }
            if (item.get("itemType").asText().equals("VALIDATION")
                    && item.get("title").asText().equals("OrderAction.java")) {
                validation = item;
            }
            if (item.get("itemType").asText().equals("FORM_MODEL")
                    && item.get("title").asText().equals("OrderForm.java")) {
                form = item;
            }
            if (item.get("itemType").asText().equals("JAKARTA_NAMESPACE")
                    && item.get("title").asText().equals("Order.java")) {
                jakarta = item;
            }
            if (item.get("itemType").asText().equals("DATA_ACCESS")
                    && item.get("title").asText().equals("OrderQueryDao.java")) {
                queryDao = item;
            }
            if (item.get("itemType").asText().equals("DATA_ACCESS")
                    && item.get("title").asText().equals("OrderDao.java")) {
                daoData = item;
            }
            if (item.get("itemType").asText().equals("TRANSACTION")) {
                transaction = item;
            }
            if (item.get("itemType").asText().equals("MESSAGE_FLOW")) {
                messageFlow = item;
            }
            if (item.get("itemType").asText().equals("MESSAGE_RESOURCE")
                    && item.get("title").asText().equals("messages.properties")) {
                messageResource = item;
            }
            if (item.get("itemType").asText().equals("JSP_VIEW")
                    && item.get("title").asText().equals("edit.jsp")) {
                jspView = item;
            }
            if (item.get("itemType").asText().equals("JSP_VIEW")
                    && item.get("title").asText().equals("list.jsp")) {
                listJspView = item;
            }
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("web.xml")) {
                webXml = item;
            }
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("struts-config.xml")) {
                strutsConfig = item;
            }
        }
        assertThat(component).isNotNull();
        assertThat(action).isNotNull();
        assertThat(validation).isNotNull();
        assertThat(form).isNotNull();
        assertThat(jakarta).isNotNull();
        assertThat(queryDao).isNotNull();
        assertThat(daoData).isNotNull();
        assertThat(transaction).isNotNull();
        assertThat(messageFlow).isNotNull();
        assertThat(messageResource).isNotNull();
        assertThat(jspView).isNotNull();
        assertThat(listJspView).isNotNull();
        assertThat(webXml).isNotNull();
        assertThat(strutsConfig).isNotNull();
        String componentId = component.get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                        + "transformation-proposals", projectId, planId,
                        messageFlow.get("id").asText()))
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
                .contains("org.springframework.context.annotation.Scope")
                .contains("@Scope(\"prototype\")")
                .doesNotContain("org.seasar.framework");
        assertThat(Files.readAllBytes(SAMPLE.resolve(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java")))
                .isEqualTo(fixtureBytes);
        assertThat(Files.getPosixFilePermissions(transformedFile))
                .isEqualTo(originalPermissions);
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
        assertThat(Files.getPosixFilePermissions(transformedFile))
                .isEqualTo(originalPermissions);
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                        + "transformation-runs", projectId, planId, componentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESTORED"));

        String validationItemId = validation.get("id").asText();
        String validationProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, validationItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("SASTRUTS-VALIDATION-BEAN"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString(
                                "jakarta.validation.constraints.NotNull")))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString(
                                "jakarta.validation.constraints.Size")))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("@Size(max = 120)")))
                .andReturn().getResponse().getContentAsString();
        String validationProposalId = objectMapper.readTree(validationProposalResponse)
                .get("id").asText();
        String validationRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, validationProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(validationFile), StandardCharsets.UTF_8))
                .contains("@NotNull")
                .contains("@Size(max = 120)")
                .contains("import jakarta.validation.constraints.Size;")
                .doesNotContain("org.seasar.struts.annotation.Required")
                .doesNotContain("org.seasar.struts.annotation.MaxLength");
        byte[] validationAppliedBytes = Files.readAllBytes(validationFile);
        Files.write(validationFile, (new String(validationAppliedBytes, StandardCharsets.UTF_8)
                + "\n").getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId,
                        action.get("id").asText()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCE_CHANGED"));
        Files.write(validationFile, validationAppliedBytes);

        Path sameFileController = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/action/OrderActionController.java");
        String sameFileControllerProposal = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId,
                        action.get("id").asText()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("STRUTS-CONTROLLER-SKELETON"))
                .andReturn().getResponse().getContentAsString();
        String sameFileControllerRun = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, objectMapper.readTree(sameFileControllerProposal).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Files.exists(sameFileController)).isTrue();
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(sameFileControllerRun).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.exists(sameFileController)).isFalse();
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(validationRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(validationFile)).isEqualTo(originalValidationBytes);

        String formItemId = form.get("id").asText();
        String formProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, formItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("STRUTS-ACTIONFORM-MODEL"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("ActionForm")))
                .andReturn().getResponse().getContentAsString();
        String formProposalId = objectMapper.readTree(formProposalResponse).get("id").asText();
        String formRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, formProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(formFile), StandardCharsets.UTF_8))
                .doesNotContain("ActionForm")
                .contains("public class OrderForm");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(formRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(formFile)).isEqualTo(originalFormBytes);

        String jakartaItemId = jakarta.get("id").asText();
        String jakartaProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, jakartaItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("JAVAX-TO-JAKARTA"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("jakarta.persistence")))
                .andReturn().getResponse().getContentAsString();
        String jakartaProposalId = objectMapper.readTree(jakartaProposalResponse).get("id").asText();
        String jakartaRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, jakartaProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(jakartaFile), StandardCharsets.UTF_8))
                .contains("jakarta.persistence")
                .doesNotContain("javax.persistence");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(jakartaRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(jakartaFile)).isEqualTo(originalJakartaBytes);

        String queryDaoItemId = queryDao.get("id").asText();
        String queryProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, queryDaoItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("S2JDBC-QUERY-SPRING-JDBC"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("jdbcManager.query")))
                .andReturn().getResponse().getContentAsString();
        String queryProposalId = objectMapper.readTree(queryProposalResponse).get("id").asText();
        String queryRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, queryProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(queryDaoFile), StandardCharsets.UTF_8))
                .contains("JdbcTemplate")
                .contains("jdbcManager.query")
                .doesNotContain("selectBySql");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(queryRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(queryDaoFile)).isEqualTo(originalQueryDaoBytes);

        String messageItemId = messageResource.get("id").asText();
        String messageProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, messageItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("MESSAGE-RESOURCE-SPRING-UTF8"))
                .andExpect(jsonPath("$.artifactType").value("EDIT_FILE"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("注文一覧")))
                .andReturn().getResponse().getContentAsString();
        String messageProposalId = objectMapper.readTree(messageProposalResponse)
                .get("id").asText();
        String messageRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, messageProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(messageFile), StandardCharsets.UTF_8))
                .contains("order.list.title=注文一覧")
                .doesNotContain("\\u6ce8");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(messageRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(messageFile)).isEqualTo(originalMessageBytes);

        String jspItemId = jspView.get("id").asText();
        String jspProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, jspItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("JSP-BEAN-MESSAGE-JSTL"))
                .andExpect(jsonPath("$.artifactType").value("EDIT_FILE"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("fmt:message")))
                .andReturn().getResponse().getContentAsString();
        String jspProposalId = objectMapper.readTree(jspProposalResponse).get("id").asText();
        String jspRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, jspProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(jspFile), StandardCharsets.UTF_8))
                .contains("<fmt:message key=\"order.list.title\"/>")
                .contains("jakarta.tags.fmt")
                .doesNotContain("<bean:message")
                .doesNotContain("prefix=\"bean\"");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(jspRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(jspFile)).isEqualTo(originalJspBytes);

        String listJspItemId = listJspView.get("id").asText();
        String listJspProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, listJspItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("JSP-BEAN-MESSAGE-JSTL"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("jakarta.tags.core")))
                .andReturn().getResponse().getContentAsString();
        String listJspProposalId = objectMapper.readTree(listJspProposalResponse)
                .get("id").asText();
        String listJspRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, listJspProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(listJspFile), StandardCharsets.UTF_8))
                .contains("jakarta.tags.core")
                .contains("jakarta.tags.fmt")
                .doesNotContain("java.sun.com/jsp/jstl")
                .contains("<c:out value=\"${order.customerName}\"/>")
                .doesNotContain("<bean:write")
                .doesNotContain("prefix=\"bean\"")
                .contains("<html:errors/>")
                .contains("<sq:emptyState message=\"orders.empty\"/>");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(listJspRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(listJspFile)).isEqualTo(originalListJspBytes);

        String webXmlItemId = webXml.get("id").asText();
        String webXmlProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, webXmlItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("WEBXML-JAKARTA-NAMESPACE"))
                .andExpect(jsonPath("$.ruleVersion").value("1.2.0"))
                .andExpect(jsonPath("$.artifactType").value("EDIT_FILE"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("jakarta.ee")))
                .andReturn().getResponse().getContentAsString();
        String webXmlProposalId = objectMapper.readTree(webXmlProposalResponse).get("id").asText();
        String webXmlRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, webXmlProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(webXmlFile), StandardCharsets.UTF_8))
                .contains("https://jakarta.ee/xml/ns/jakartaee")
                .contains("web-app_6_0.xsd")
                .contains("version=\"6.0\"")
                .contains("<servlet-class>jakarta.servlet.http.HttpServlet</servlet-class>")
                .contains("<exception-type>jakarta.servlet.ServletException</exception-type>")
                .contains("<location>/error.jsp</location>")
                .doesNotContain("java.sun.com/xml/ns/javaee")
                .doesNotContain("javax.servlet");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(webXmlRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(webXmlFile)).isEqualTo(webXmlWithServlet);

        String strutsConfigItemId = strutsConfig.get("id").asText();
        String configProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, strutsConfigItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("STRUTS-CONFIG-MESSAGE-SPRING"))
                .andExpect(jsonPath("$.relativePath").value(
                        "web-app/src/main/resources/application.yml"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("spring.messages.basename")))
                .andReturn().getResponse().getContentAsString();
        String configProposalId = objectMapper.readTree(configProposalResponse).get("id").asText();
        Files.write(applicationYmlFile, (new String(originalApplicationYmlBytes,
                StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, configProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCE_CHANGED"));
        Files.write(applicationYmlFile, originalApplicationYmlBytes);
        String configRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, configProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Files.readAllBytes(strutsConfigFile)).isEqualTo(originalStrutsConfigBytes);
        assertThat(new String(Files.readAllBytes(applicationYmlFile), StandardCharsets.UTF_8))
                .contains("spring.messages.basename: \"messages\"");
        JsonNode configRun = objectMapper.readTree(configRunResponse);
        assertThat(configRun.get("originalSha256").asText())
                .isNotEqualTo(configRun.get("targetSha256").asText());
        Path configBackup = Path.of(configRun.get("backupPath").asText());
        assertThat(configBackup.startsWith(tempProject)).isFalse();
        assertThat(Files.readAllBytes(configBackup)).isEqualTo(originalApplicationYmlBytes);
        Files.write(strutsConfigFile, (new String(originalStrutsConfigBytes,
                StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, configRun.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCE_CHANGED"));
        Files.write(strutsConfigFile, originalStrutsConfigBytes);
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, configRun.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(applicationYmlFile)).isEqualTo(originalApplicationYmlBytes);
        assertThat(Files.readAllBytes(strutsConfigFile)).isEqualTo(originalStrutsConfigBytes);

        String actionItemId = action.get("id").asText();
        Path controllerFile = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/action/OrderActionController.java");
        String controllerProposalResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, actionItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("STRUTS-CONTROLLER-SKELETON"))
                .andExpect(jsonPath("$.artifactType").value("CREATE_FILE"))
                .andExpect(jsonPath("$.relativePath").value(
                        "web-app/src/main/java/com/example/legacy/action/OrderActionController.java"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("--- /dev/null")))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("@Controller")))
                .andReturn().getResponse().getContentAsString();
        String controllerProposalId = objectMapper.readTree(controllerProposalResponse)
                .get("id").asText();
        String controllerRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, controllerProposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.artifactType").value("CREATE_FILE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(Files.exists(controllerFile)).isTrue();
        assertThat(new String(Files.readAllBytes(controllerFile), StandardCharsets.UTF_8))
                .contains("@Controller")
                .contains("@RequestMapping(\"/orders\")");
        assertThat(Files.readAllBytes(validationFile)).isEqualTo(originalValidationBytes);
        mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, actionItemId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_TARGET_EXISTS"));
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(controllerRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.exists(controllerFile)).isFalse();

        String daoDataItemId = daoData.get("id").asText();
        String daoDataProposal = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, daoDataItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("S2JDBC-QUERY-SPRING-JDBC"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("Types.BIGINT")))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("Types.DATE")))
                .andReturn().getResponse().getContentAsString();
        String daoDataRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, objectMapper.readTree(daoDataProposal).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(daoFile), StandardCharsets.UTF_8))
                .contains("public JdbcTemplate jdbcManager;")
                .contains("jdbcManager.query(\"SELECT * FROM orders WHERE status = ?\"")
                .contains("jdbcManager.update(\"UPDATE orders SET status = ? WHERE id = ?\"")
                .contains("jdbcManager.update(\"DELETE FROM orders WHERE created < ?\"")
                .contains("new int[]{Types.VARCHAR, Types.BIGINT}")
                .contains("new int[]{Types.DATE}")
                .doesNotContain("selectBySql")
                .doesNotContain("updateBySql");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(daoDataRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(daoFile)).isEqualTo(originalDaoBytes);

        String transactionItemId = transaction.get("id").asText();
        String transactionProposal = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals", projectId, planId, transactionItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("S2TX-SPRING-TRANSACTIONAL"))
                .andExpect(jsonPath("$.ruleVersion").value("1.2.0"))
                .andExpect(jsonPath("$.validationStatus").value("PARSED"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("Propagation.REQUIRES_NEW")))
                .andReturn().getResponse().getContentAsString();
        String transactionRunResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        projectId, objectMapper.readTree(transactionProposal).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(daoFile), StandardCharsets.UTF_8))
                .contains("@Transactional(propagation = Propagation.REQUIRES_NEW)")
                .contains("import org.springframework.transaction.annotation.Propagation;")
                .doesNotContain("org.seasar.extension.tx");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        projectId, objectMapper.readTree(transactionRunResponse).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(daoFile)).isEqualTo(originalDaoBytes);

        java.util.List<String> batchItemIds = java.util.List.of(validationItemId, actionItemId);
        mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-batches/preview",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", batchItemIds))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEWED"))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.items.length()").value(2));
        String batchApplyResponse = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-batches/apply",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", batchItemIds, "confirmed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.applied").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(new String(Files.readAllBytes(validationFile), StandardCharsets.UTF_8))
                .contains("@Size(max = 120)");
        assertThat(Files.exists(controllerFile)).isTrue();
        Integer batchAuditCount = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where project_id = ? "
                        + "and action = 'TRANSFORMATION_BATCH_APPLIED'",
                Integer.class, java.util.UUID.fromString(projectId));
        assertThat(batchAuditCount).isGreaterThan(0);
        java.util.List<String> batchRunIds = new java.util.ArrayList<>();
        for (JsonNode item : objectMapper.readTree(batchApplyResponse).get("items")) {
            batchRunIds.add(item.get("run").get("id").asText());
        }
        mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-batches/restore",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runIds", batchRunIds, "confirmed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"))
                .andExpect(jsonPath("$.restored").value(2));
        assertThat(Files.readAllBytes(validationFile)).isEqualTo(originalValidationBytes);
        assertThat(Files.exists(controllerFile)).isFalse();

        mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-batches/apply",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", java.util.List.of(validationItemId,
                                        messageFlow.get("id").asText(), actionItemId),
                                "confirmed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.restoredOnFailure").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[1].status").value("FAILED"))
                .andExpect(jsonPath("$.items[2].status").value("NOT_ATTEMPTED"));
        assertThat(Files.readAllBytes(validationFile)).isEqualTo(originalValidationBytes);
        assertThat(Files.exists(controllerFile)).isFalse();

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

    @Test
    void previewsPlanCoverageAndRunsEveryEligibleItemOnACopy() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build", "plan-run-fixture")
                .toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        Map<String, Path> files = new LinkedHashMap<>();
        for (String relative : List.of(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java",
                "domain/src/main/java/com/example/legacy/dao/OrderQueryDao.java",
                "web-app/src/main/resources/messages.properties",
                "web-app/src/main/webapp/WEB-INF/jsp/order/list.jsp",
                "web-app/src/main/webapp/WEB-INF/web.xml",
                "web-app/src/main/webapp/WEB-INF/struts-config.xml",
                "web-app/src/main/resources/application.yml")) {
            files.put(relative, tempProject.resolve(relative));
        }
        Map<String, byte[]> originals = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            originals.put(entry.getKey(), Files.readAllBytes(entry.getValue()));
        }
        PlanSetup setup = createPlanSetup(tempProject, "Plan Run Copy");

        String coverageBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-coverage",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.eligible").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.summary.unsupported").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.items[?(@.title == 'OrderService.java'"
                        + " && @.eligible == true)]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.title == 'messages.properties'"
                        + " && @.supported == true)]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.supported == false)]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.title == 'landing.jsp'"
                        + " && @.supported == true && @.eligible == false"
                        + " && @.ineligibleReason == 'TRANSFORMATION_UNSUPPORTED')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode coverage = objectMapper.readTree(coverageBody);
        int eligible = coverage.at("/summary/eligible").asInt();
        assertThat(eligible).isGreaterThan(1);
        assertThat(Files.readAllBytes(files.get(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java")))
                .isEqualTo(originals.get(
                        "web-app/src/main/java/com/example/legacy/service/OrderService.java"));

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_CONFIRMATION_REQUIRED"));

        String startBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode started = objectMapper.readTree(startBody);
        assertThat(started.get("status").asText()).isEqualTo("RUNNING");
        String planRunId = started.get("id").asText();
        JsonNode planRun = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(planRun.get("status").asText()).as(planRun.toString()).isEqualTo("APPLIED");
        assertThat(planRun.get("eligibleCount").asInt()).isEqualTo(eligible);
        assertThat(planRun.get("appliedCount").asInt()).isEqualTo(eligible);
        assertThat(planRun.get("failedCount").asInt()).isZero();
        assertThat(planRun.get("rolledBackCount").asInt()).isZero();
        assertThat(planRun.get("items").size()).isEqualTo(eligible);
        Integer planRunAudit = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where project_id = ? "
                        + "and action = 'TRANSFORMATION_PLAN_RUN_APPLIED'",
                Integer.class, java.util.UUID.fromString(setup.projectId()));
        assertThat(planRunAudit).isGreaterThan(0);
        assertThat(new String(Files.readAllBytes(files.get(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java")),
                StandardCharsets.UTF_8))
                .contains("org.springframework.stereotype.Component")
                .doesNotContain("org.seasar.framework");
        assertThat(new String(Files.readAllBytes(files.get(
                "web-app/src/main/resources/application.yml")), StandardCharsets.UTF_8))
                .contains("spring.messages.basename");

        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planRunId))
                .andExpect(jsonPath("$[0].status").value("APPLIED"));
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}",
                        setup.projectId(), setup.planId(), planRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.status == 'APPLIED')]").isNotEmpty());

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/restore",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"))
                .andExpect(jsonPath("$.restoredCount").value(eligible))
                .andExpect(jsonPath("$.failedCount").value(0));
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            assertThat(Files.readAllBytes(entry.getValue()))
                    .isEqualTo(originals.get(entry.getKey()));
        }
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/restore",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_RUN_NOT_RESTORABLE"));
        deleteRecursively(tempProject);
    }

    @Test
    void rollsBackEarlierPlanItemsWhenALaterItemCannotBeWritten() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "plan-run-failure-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        PlanSetup setup = createPlanSetup(tempProject, "Plan Run Failure Copy");
        String coverageBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-coverage",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.title == 'web.xml'"
                        + " && @.eligible == true)]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> eligible = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(coverageBody).get("items")) {
            if (row.get("eligible").asBoolean()) {
                eligible.add(row);
            }
        }
        assertThat(eligible.size()).isGreaterThan(1);
        Map<String, String> paths = new LinkedHashMap<>();
        for (JsonNode item : setup.items()) {
            paths.put(item.get("title").asText(), item.get("primaryFilePath").asText());
        }
        Path firstFile = tempProject.resolve(paths.get(eligible.get(0).get("title").asText()));
        byte[] firstBytes = Files.readAllBytes(firstFile);
        Path webInf = tempProject.resolve("web-app/src/main/webapp/WEB-INF");
        Set<PosixFilePermission> webInfPermissions = Files.getPosixFilePermissions(webInf);
        JsonNode run;
        try {
            Files.setPosixFilePermissions(webInf, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
            String startBody = mockMvc.perform(post(
                            "/api/v1/projects/{id}/migration-plans/{plan}/"
                                    + "transformation-plan-runs",
                            setup.projectId(), setup.planId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"confirmed\":true}"))
                    .andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            String startedId = objectMapper.readTree(startBody).get("id").asText();
            run = awaitPlanRun(setup.projectId(), setup.planId(), startedId);
        } finally {
            Files.setPosixFilePermissions(webInf, webInfPermissions);
        }
        assertThat(run.get("status").asText()).as(run.toString()).isEqualTo("FAILED");
        assertThat(run.get("eligibleCount").asInt()).isEqualTo(eligible.size());
        int applied = run.get("appliedCount").asInt();
        assertThat(applied).isGreaterThan(0);
        assertThat(run.get("failedCount").asInt()).isEqualTo(1);
        assertThat(run.get("rolledBackCount").asInt()).isEqualTo(applied);
        assertThat(run.get("items").toString()).contains("FAILED").contains("ROLLED_BACK");
        Integer rollbackAudit = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where project_id = ? "
                        + "and action = 'TRANSFORMATION_PLAN_RUN_FAILED'",
                Integer.class, java.util.UUID.fromString(setup.projectId()));
        assertThat(rollbackAudit).isGreaterThan(0);
        assertThat(Files.readAllBytes(firstFile)).isEqualTo(firstBytes);

        String historyBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String planRunId = objectMapper.readTree(historyBody).get(0).get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/restore",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_RUN_NOT_RESTORABLE"));
        deleteRecursively(tempProject);
    }

    @Test
    void recoversAnInterruptedPlanRun() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "plan-run-recovery-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        Map<String, Path> files = new LinkedHashMap<>();
        for (String relative : List.of(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java",
                "web-app/src/main/resources/messages.properties",
                "web-app/src/main/webapp/WEB-INF/web.xml",
                "web-app/src/main/resources/application.yml")) {
            files.put(relative, tempProject.resolve(relative));
        }
        Map<String, byte[]> originals = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            originals.put(entry.getKey(), Files.readAllBytes(entry.getValue()));
        }
        PlanSetup setup = createPlanSetup(tempProject, "Plan Run Recovery Copy");
        String startBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String planRunId = objectMapper.readTree(startBody).get("id").asText();
        JsonNode planRun = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(planRun.get("status").asText()).isEqualTo("APPLIED");
        int eligible = planRun.get("eligibleCount").asInt();

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/recover",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_RUN_NOT_RECOVERABLE"));

        jdbcTemplate.update("update transformation_plan_run set status = 'RUNNING', "
                        + "applied_count = 0, restored_count = 0, completed_at = null "
                        + "where id = ?",
                java.util.UUID.fromString(planRunId));
        interruptionMarker.run(null);
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}",
                        setup.projectId(), setup.planId(), planRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERRUPTED"));
        String recoveredBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/recover",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOVERED"))
                .andExpect(jsonPath("$.appliedCount").value(eligible))
                .andExpect(jsonPath("$.restoredCount").value(eligible))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(recoveredBody).get("items").toString())
                .contains("RESTORED");
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            assertThat(Files.readAllBytes(entry.getValue()))
                    .isEqualTo(originals.get(entry.getKey()));
        }
        Integer recoveryAudit = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where project_id = ? "
                        + "and action = 'TRANSFORMATION_PLAN_RUN_RECOVERED'",
                Integer.class, java.util.UUID.fromString(setup.projectId()));
        assertThat(recoveryAudit).isGreaterThan(0);
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/recover",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_RUN_NOT_RECOVERABLE"));
        deleteRecursively(tempProject);
    }

    @Test
    void cancelsAnOrphanedRunningPlanRunAndRollsBack() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "plan-run-cancel-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        Map<String, Path> files = new LinkedHashMap<>();
        for (String relative : List.of(
                "web-app/src/main/java/com/example/legacy/service/OrderService.java",
                "web-app/src/main/resources/application.yml")) {
            files.put(relative, tempProject.resolve(relative));
        }
        Map<String, byte[]> originals = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            originals.put(entry.getKey(), Files.readAllBytes(entry.getValue()));
        }
        PlanSetup setup = createPlanSetup(tempProject, "Plan Run Cancel Copy");
        String startBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String planRunId = objectMapper.readTree(startBody).get("id").asText();
        JsonNode applied = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(applied.get("status").asText()).isEqualTo("APPLIED");
        int eligible = applied.get("eligibleCount").asInt();

        jdbcTemplate.update("update transformation_plan_run set status = 'RUNNING', "
                        + "applied_count = 0, restored_count = 0, rolled_back_count = 0, "
                        + "completed_at = null where id = ?",
                java.util.UUID.fromString(planRunId));
        String cancelledBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/cancel",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.appliedCount").value(eligible))
                .andExpect(jsonPath("$.rolledBackCount").value(eligible))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(cancelledBody).get("items").toString())
                .contains("ROLLED_BACK");
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            assertThat(Files.readAllBytes(entry.getValue()))
                    .isEqualTo(originals.get(entry.getKey()));
        }
        Integer cancelAudit = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where project_id = ? "
                        + "and action = 'TRANSFORMATION_PLAN_RUN_CANCELLED'",
                Integer.class, java.util.UUID.fromString(setup.projectId()));
        assertThat(cancelAudit).isGreaterThan(0);
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/cancel",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_RUN_NOT_CANCELLABLE"));
        deleteRecursively(tempProject);
    }

    @Test
    void resumesAnInterruptedPlanRunFromRemainingItems() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "plan-run-resume-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        PlanSetup setup = createPlanSetup(tempProject, "Plan Run Resume Copy");
        String startBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String planRunId = objectMapper.readTree(startBody).get("id").asText();
        JsonNode applied = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(applied.get("status").asText()).isEqualTo("APPLIED");
        int eligible = applied.get("eligibleCount").asInt();
        Map<String, String> pathByItemId = new LinkedHashMap<>();
        for (JsonNode item : setup.items()) {
            pathByItemId.put(item.get("id").asText(), item.get("primaryFilePath").asText());
        }
        Map<String, Integer> pathCounts = new LinkedHashMap<>();
        for (JsonNode item : applied.get("items")) {
            pathCounts.merge(pathByItemId.get(item.get("itemId").asText()), 1, Integer::sum);
        }
        JsonNode resumeItem = null;
        for (JsonNode item : applied.get("items")) {
            String path = pathByItemId.get(item.get("itemId").asText());
            if (item.get("ordinal").asInt() > 1 && pathCounts.get(path) == 1) {
                resumeItem = item;
                break;
            }
        }
        assertThat(resumeItem).isNotNull();
        Path resumeFile = tempProject.resolve(
                pathByItemId.get(resumeItem.get("itemId").asText()));
        byte[] transformedBytes = Files.readAllBytes(resumeFile);

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/resume",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_RUN_NOT_RESUMABLE"));

        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        setup.projectId(), resumeItem.get("runId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        jdbcTemplate.update("update transformation_plan_run_item set status = 'NOT_ATTEMPTED', "
                        + "run_id = null where plan_run_id = ? and item_id = ?",
                java.util.UUID.fromString(planRunId),
                java.util.UUID.fromString(resumeItem.get("itemId").asText()));
        jdbcTemplate.update("update transformation_plan_run set status = 'INTERRUPTED', "
                        + "applied_count = ?, completed_at = null where id = ?",
                eligible - 1, java.util.UUID.fromString(planRunId));

        String resumeBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/resume",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(resumeBody).get("status").asText()).isEqualTo("RUNNING");
        JsonNode resumed = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(resumed.get("status").asText()).isEqualTo("APPLIED");
        assertThat(resumed.get("appliedCount").asInt()).isEqualTo(eligible);
        assertThat(resumed.get("failedCount").asInt()).isZero();
        assertThat(resumed.get("items").toString()).doesNotContain("NOT_ATTEMPTED");
        assertThat(Files.readAllBytes(resumeFile)).isEqualTo(transformedBytes);
        Integer resumeAudit = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where project_id = ? "
                        + "and action = 'TRANSFORMATION_PLAN_RUN_RESUMED'",
                Integer.class, java.util.UUID.fromString(setup.projectId()));
        assertThat(resumeAudit).isGreaterThan(0);
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/resume",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_RUN_NOT_RESUMABLE"));
        deleteRecursively(tempProject);
    }

    @Test
    void runsOnlySelectedEligiblePlanItems() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "plan-run-targeted-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        PlanSetup setup = createPlanSetup(tempProject, "Plan Run Targeted Copy");
        String coverageBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-coverage",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode coverage = objectMapper.readTree(coverageBody);
        Map<String, String> pathByItemId = new LinkedHashMap<>();
        for (JsonNode item : setup.items()) {
            pathByItemId.put(item.get("id").asText(), item.get("primaryFilePath").asText());
        }
        List<JsonNode> javaEligible = new ArrayList<>();
        JsonNode ineligible = null;
        for (JsonNode row : coverage.get("items")) {
            if (!row.get("eligible").asBoolean()) {
                if (ineligible == null) {
                    ineligible = row;
                }
                continue;
            }
            String path = pathByItemId.get(row.get("itemId").asText());
            if (path != null && path.endsWith(".java")
                    && !"STRUTS-CONTROLLER-SKELETON".equals(row.get("ruleId").asText())) {
                javaEligible.add(row);
            }
        }
        assertThat(javaEligible.size()).isGreaterThan(2);
        assertThat(ineligible).isNotNull();
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", List.of(ineligible.get("itemId").asText()),
                                "confirmed", true))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_ITEMS_INVALID"));

        JsonNode first = javaEligible.get(0);
        JsonNode second = javaEligible.stream()
                .filter(row -> !pathByItemId.get(row.get("itemId").asText())
                        .equals(pathByItemId.get(first.get("itemId").asText())))
                .findFirst()
                .orElseThrow();
        String firstPath = pathByItemId.get(first.get("itemId").asText());
        String secondPath = pathByItemId.get(second.get("itemId").asText());
        String untouchedPath = javaEligible.stream()
                .map(row -> pathByItemId.get(row.get("itemId").asText()))
                .filter(path -> !path.equals(firstPath) && !path.equals(secondPath))
                .findFirst()
                .orElseThrow();
        Path firstFile = tempProject.resolve(firstPath);
        Path secondFile = tempProject.resolve(secondPath);
        Path untouchedFile = tempProject.resolve(untouchedPath);
        byte[] firstBytes = Files.readAllBytes(firstFile);
        byte[] secondBytes = Files.readAllBytes(secondFile);
        byte[] untouchedBytes = Files.readAllBytes(untouchedFile);

        String startBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", List.of(first.get("itemId").asText(),
                                        second.get("itemId").asText()),
                                "confirmed", true))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String planRunId = objectMapper.readTree(startBody).get("id").asText();
        JsonNode applied = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(applied.get("status").asText()).isEqualTo("APPLIED");
        assertThat(applied.get("eligibleCount").asInt()).isEqualTo(2);
        assertThat(applied.get("appliedCount").asInt()).isEqualTo(2);
        assertThat(applied.get("items").size()).isEqualTo(2);
        assertThat(Files.readAllBytes(firstFile)).isNotEqualTo(firstBytes);
        assertThat(Files.readAllBytes(secondFile)).isNotEqualTo(secondBytes);
        assertThat(Files.readAllBytes(untouchedFile)).isEqualTo(untouchedBytes);
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/restore",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.readAllBytes(firstFile)).isEqualTo(firstBytes);
        assertThat(Files.readAllBytes(secondFile)).isEqualTo(secondBytes);
        assertThat(Files.readAllBytes(untouchedFile)).isEqualTo(untouchedBytes);
        deleteRecursively(tempProject);
    }

    @Test
    void generatesASpringConfigurationFromASimpleDiconOnACopy() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "dicon-config-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        Path simpleDicon = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/dicon/simple.dicon");
        Files.writeString(simpleDicon, """
                <?xml version="1.0" encoding="UTF-8"?>
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"
                               instance="prototype"/>
                </components>
                """);
        byte[] diconBytes = Files.readAllBytes(simpleDicon);
        Path componentsDicon = tempProject.resolve(
                "web-app/src/main/resources/config/components.dicon");
        byte[] componentsDiconBytes = Files.readAllBytes(componentsDicon);
        Files.writeString(tempProject.resolve(
                        "web-app/src/main/java/com/example/legacy/service/LiteralSettings.java"),
                """
                        package com.example.legacy.service;
                        public class LiteralSettings {
                            public String name;
                            public boolean enabled;
                            public int count;
                            public long total;
                            public double ratio;
                        }
                        """);
        Path literalDicon = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/dicon/literal.dicon");
        Files.writeString(literalDicon, """
                <?xml version="1.0" encoding="UTF-8"?>
                <components>
                    <component name="literalSettings"
                               class="com.example.legacy.service.LiteralSettings">
                        <property name="name">"demo"</property>
                        <property name="enabled">true</property>
                        <property name="count">3</property>
                        <property name="total">10L</property>
                        <property name="ratio">1.5</property>
                    </component>
                </components>
                """);
        byte[] literalDiconBytes = Files.readAllBytes(literalDicon);
        PlanSetup setup = createPlanSetup(tempProject, "Dicon Configuration Copy");
        JsonNode simple = null;
        JsonNode components = null;
        JsonNode app = null;
        JsonNode literal = null;
        for (JsonNode item : setup.items()) {
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("simple.dicon")) {
                simple = item;
            }
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("components.dicon")) {
                components = item;
            }
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("app.dicon")) {
                app = item;
            }
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("literal.dicon")) {
                literal = item;
            }
        }
        assertThat(simple).isNotNull();
        assertThat(components).isNotNull();
        assertThat(app).isNotNull();
        assertThat(literal).isNotNull();
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-coverage",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.title == 'simple.dicon'"
                        + " && @.eligible == true"
                        + " && @.ruleId == 'DICON-COMPONENT-SPRING-CONFIG')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.title == 'components.dicon'"
                        + " && @.eligible == true"
                        + " && @.ruleId == 'DICON-COMPONENT-SPRING-CONFIG')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.title == 'app.dicon'"
                        + " && @.eligible == false"
                        + " && @.ineligibleReason == 'TRANSFORMATION_UNSUPPORTED')]").isNotEmpty());

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), app.get("id").asText()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_UNSUPPORTED"));

        String proposalBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), simple.get("id").asText()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("DICON-COMPONENT-SPRING-CONFIG"))
                .andExpect(jsonPath("$.ruleVersion").value("1.6.0"))
                .andExpect(jsonPath("$.artifactType").value("CREATE_FILE"))
                .andExpect(jsonPath("$.relativePath").value(
                        "web-app/src/main/java/com/example/legacy/service/"
                                + "SimpleDiconConfiguration.java"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("@Configuration")))
                .andReturn().getResponse().getContentAsString();
        String proposalId = objectMapper.readTree(proposalBody).get("id").asText();
        String runBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        setup.projectId(), proposalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.artifactType").value("CREATE_FILE"))
                .andReturn().getResponse().getContentAsString();
        Path generated = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/service/"
                        + "SimpleDiconConfiguration.java");
        assertThat(Files.readAllBytes(simpleDicon)).isEqualTo(diconBytes);
        assertThat(new String(Files.readAllBytes(generated), StandardCharsets.UTF_8))
                .contains("package com.example.legacy.service;")
                .contains("@Configuration")
                .contains("@Scope(\"prototype\")")
                .contains("public com.example.legacy.service.OrderService orderService()")
                .contains("return new com.example.legacy.service.OrderService();");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        setup.projectId(), objectMapper.readTree(runBody).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.exists(generated)).isFalse();
        assertThat(Files.readAllBytes(simpleDicon)).isEqualTo(diconBytes);

        String literalProposalBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), literal.get("id").asText()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("DICON-COMPONENT-SPRING-CONFIG"))
                .andExpect(jsonPath("$.ruleVersion").value("1.6.0"))
                .andExpect(jsonPath("$.relativePath").value(
                        "web-app/src/main/java/com/example/legacy/service/"
                                + "LiteralDiconConfiguration.java"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("name = \"demo\"")))
                .andReturn().getResponse().getContentAsString();
        String literalRunBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        setup.projectId(), objectMapper.readTree(literalProposalBody)
                                .get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        Path literalConfig = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/service/"
                        + "LiteralDiconConfiguration.java");
        assertThat(Files.readAllBytes(literalDicon)).isEqualTo(literalDiconBytes);
        assertThat(new String(Files.readAllBytes(literalConfig), StandardCharsets.UTF_8))
                .contains("bean.name = \"demo\";")
                .contains("bean.enabled = true;")
                .contains("bean.count = 3;")
                .contains("bean.total = 10L;")
                .contains("bean.ratio = 1.5;");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        setup.projectId(), objectMapper.readTree(literalRunBody).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.exists(literalConfig)).isFalse();
        assertThat(Files.readAllBytes(literalDicon)).isEqualTo(literalDiconBytes);

        String componentProposalBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), components.get("id").asText()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("DICON-COMPONENT-SPRING-CONFIG"))
                .andExpect(jsonPath("$.ruleVersion").value("1.6.0"))
                .andExpect(jsonPath("$.relativePath").value(
                        "web-app/src/main/java/com/example/legacy/action/"
                                + "ComponentsDiconConfiguration.java"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("@Qualifier(\"orderService\")")))
                .andReturn().getResponse().getContentAsString();
        String componentRunBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        setup.projectId(), objectMapper.readTree(componentProposalBody)
                                .get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        Path componentConfig = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/action/"
                        + "ComponentsDiconConfiguration.java");
        assertThat(Files.readAllBytes(componentsDicon)).isEqualTo(componentsDiconBytes);
        assertThat(new String(Files.readAllBytes(componentConfig), StandardCharsets.UTF_8))
                .contains("public com.example.legacy.action.OrderAction orderAction("
                        + "@Qualifier(\"orderService\")"
                        + " com.example.legacy.service.OrderService orderService)")
                .contains("bean.orderService = orderService;");
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        setup.projectId(), objectMapper.readTree(componentRunBody)
                                .get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.exists(componentConfig)).isFalse();
        assertThat(Files.readAllBytes(componentsDicon)).isEqualTo(componentsDiconBytes);
        deleteRecursively(tempProject);
    }

    @Test
    void combinesSameModuleDiconIncludesIntoOneConfigurationOnACopy() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "dicon-include-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        Path baseDicon = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/dicon/base.dicon");
        Files.writeString(baseDicon, """
                <?xml version="1.0" encoding="UTF-8"?>
                <components>
                    <component name="orderService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """);
        Path rootDicon = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/dicon/root.dicon");
        Files.writeString(rootDicon, """
                <?xml version="1.0" encoding="UTF-8"?>
                <components>
                    <include path="base.dicon"/>
                    <component name="orderAction"
                               class="com.example.legacy.action.OrderAction">
                        <property name="orderService">orderService</property>
                    </component>
                </components>
                """);
        byte[] baseBytes = Files.readAllBytes(baseDicon);
        byte[] rootBytes = Files.readAllBytes(rootDicon);
        PlanSetup setup = createPlanSetup(tempProject, "Dicon Include Copy");
        JsonNode base = null;
        JsonNode root = null;
        for (JsonNode item : setup.items()) {
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("base.dicon")) {
                base = item;
            }
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("root.dicon")) {
                root = item;
            }
        }
        assertThat(base).isNotNull();
        assertThat(root).isNotNull();
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-coverage",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.title == 'root.dicon'"
                        + " && @.eligible == true"
                        + " && @.ruleId == 'DICON-COMPONENT-SPRING-CONFIG')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.title == 'base.dicon'"
                        + " && @.eligible == false"
                        + " && @.ineligibleReason == 'TRANSFORMATION_UNSUPPORTED')]").isNotEmpty());

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), base.get("id").asText()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_UNSUPPORTED"));
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", List.of(base.get("id").asText()),
                                "confirmed", true))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_PLAN_ITEMS_INVALID"));

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), root.get("id").asText()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("DICON-COMPONENT-SPRING-CONFIG"))
                .andExpect(jsonPath("$.ruleVersion").value("1.6.0"))
                .andExpect(jsonPath("$.relativePath").value(
                        "web-app/src/main/java/com/example/legacy/"
                                + "RootDiconConfiguration.java"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("@Qualifier(\"orderService\")")));

        String startBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", List.of(root.get("id").asText()),
                                "confirmed", true))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String planRunId = objectMapper.readTree(startBody).get("id").asText();
        JsonNode planRun = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(planRun.get("status").asText()).isEqualTo("APPLIED");
        assertThat(planRun.get("eligibleCount").asInt()).isEqualTo(1);
        assertThat(planRun.get("appliedCount").asInt()).isEqualTo(1);
        Path generated = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/RootDiconConfiguration.java");
        assertThat(Files.readAllBytes(baseDicon)).isEqualTo(baseBytes);
        assertThat(Files.readAllBytes(rootDicon)).isEqualTo(rootBytes);
        assertThat(new String(Files.readAllBytes(generated), StandardCharsets.UTF_8))
                .contains("public com.example.legacy.service.OrderService orderService()")
                .contains("public com.example.legacy.action.OrderAction orderAction("
                        + "@Qualifier(\"orderService\")"
                        + " com.example.legacy.service.OrderService orderService)")
                .contains("bean.orderService = orderService;");
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/restore",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.exists(generated)).isFalse();
        assertThat(Files.readAllBytes(baseDicon)).isEqualTo(baseBytes);
        assertThat(Files.readAllBytes(rootDicon)).isEqualTo(rootBytes);
        deleteRecursively(tempProject);
    }

    @Test
    void generatesNestedAnonymousComponentsOnACopy() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "dicon-nested-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        Files.writeString(tempProject.resolve(
                        "web-app/src/main/java/com/example/legacy/service/NestedParent.java"), """
                package com.example.legacy.service;
                public class NestedParent {
                    public NestedChild child;
                }
                """);
        Files.writeString(tempProject.resolve(
                        "web-app/src/main/java/com/example/legacy/service/NestedChild.java"), """
                package com.example.legacy.service;
                public class NestedChild {
                    public void initialize() {
                    }
                }
                """);
        Files.writeString(tempProject.resolve(
                        "web-app/src/main/java/com/example/legacy/service/NestedAction.java"), """
                package com.example.legacy.service;
                public class NestedAction {
                    public NestedAction(NestedChild child, String label) {
                    }
                }
                """);
        Path nestedDicon = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/dicon/nested.dicon");
        Files.writeString(nestedDicon, """
                <?xml version="1.0" encoding="UTF-8"?>
                <components>
                    <component name="nestedParent"
                               class="com.example.legacy.service.NestedParent">
                        <property name="child">
                            <component class="com.example.legacy.service.NestedChild">
                                <initMethod name="initialize"/>
                            </component>
                        </property>
                    </component>
                    <component name="nestedAction"
                               class="com.example.legacy.service.NestedAction">
                        <arg>
                            <component class="com.example.legacy.service.NestedChild">
                                <initMethod name="initialize"/>
                            </component>
                        </arg>
                        <arg>"demo"</arg>
                    </component>
                </components>
                """);
        byte[] nestedBytes = Files.readAllBytes(nestedDicon);
        PlanSetup setup = createPlanSetup(tempProject, "Dicon Nested Copy");
        JsonNode nested = null;
        for (JsonNode item : setup.items()) {
            if (item.get("itemType").asText().equals("CONFIGURATION")
                    && item.get("title").asText().equals("nested.dicon")) {
                nested = item;
            }
        }
        assertThat(nested).isNotNull();
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-coverage",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.title == 'nested.dicon'"
                        + " && @.eligible == true"
                        + " && @.ruleId == 'DICON-COMPONENT-SPRING-CONFIG')]").isNotEmpty());

        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), nested.get("id").asText()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleId").value("DICON-COMPONENT-SPRING-CONFIG"))
                .andExpect(jsonPath("$.ruleVersion").value("1.6.0"))
                .andExpect(jsonPath("$.relativePath").value(
                        "web-app/src/main/java/com/example/legacy/service/"
                                + "NestedDiconConfiguration.java"))
                .andExpect(jsonPath("$.diff").value(
                        org.hamcrest.Matchers.containsString("nestedParentChild")));
        String startBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/transformation-plan-runs",
                        setup.projectId(), setup.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", List.of(nested.get("id").asText()),
                                "confirmed", true))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String planRunId = objectMapper.readTree(startBody).get("id").asText();
        JsonNode planRun = awaitPlanRun(setup.projectId(), setup.planId(), planRunId);
        assertThat(planRun.get("status").asText()).isEqualTo("APPLIED");
        Path generated = tempProject.resolve(
                "web-app/src/main/java/com/example/legacy/service/"
                        + "NestedDiconConfiguration.java");
        assertThat(Files.readAllBytes(nestedDicon)).isEqualTo(nestedBytes);
        assertThat(new String(Files.readAllBytes(generated), StandardCharsets.UTF_8))
                .contains("public com.example.legacy.service.NestedChild nestedParentChild()")
                .contains("bean.initialize();")
                .contains("public com.example.legacy.service.NestedParent nestedParent("
                        + "@Qualifier(\"nestedParentChild\")"
                        + " com.example.legacy.service.NestedChild nestedParentChild)")
                .contains("bean.child = nestedParentChild;")
                .contains("public com.example.legacy.service.NestedChild nestedActionArg1()")
                .contains("public com.example.legacy.service.NestedAction nestedAction("
                        + "@Qualifier(\"nestedActionArg1\")"
                        + " com.example.legacy.service.NestedChild nestedActionArg1)")
                .contains("return new com.example.legacy.service.NestedAction("
                        + "nestedActionArg1, \"demo\");");
        mockMvc.perform(post("/api/v1/projects/{id}/migration-plans/{plan}/"
                                + "transformation-plan-runs/{run}/restore",
                        setup.projectId(), setup.planId(), planRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));
        assertThat(Files.exists(generated)).isFalse();
        assertThat(Files.readAllBytes(nestedDicon)).isEqualTo(nestedBytes);
        deleteRecursively(tempProject);
    }

    @Test
    void keepsAppliedBackupsAndPrunesRestoredBackups() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "backup-ledger-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        PlanSetup setup = createPlanSetup(tempProject, "Backup Ledger Copy");
        JsonNode component = null;
        JsonNode queryDao = null;
        for (JsonNode item : setup.items()) {
            if (item.get("itemType").asText().equals("COMPONENT_BEAN")
                    && item.get("title").asText().equals("OrderService.java")) {
                component = item;
            }
            if (item.get("itemType").asText().equals("DATA_ACCESS")
                    && item.get("title").asText().equals("OrderQueryDao.java")) {
                queryDao = item;
            }
        }
        assertThat(component).isNotNull();
        assertThat(queryDao).isNotNull();

        String componentProposal = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), component.get("id").asText()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        setup.projectId(), objectMapper.readTree(componentProposal).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        String queryProposal = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items/{item}/"
                                + "transformation-proposals",
                        setup.projectId(), setup.planId(), queryDao.get("id").asText()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String queryRun = mockMvc.perform(post(
                        "/api/v1/projects/{id}/transformation-proposals/{proposal}/apply",
                        setup.projectId(), objectMapper.readTree(queryProposal).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-runs/{run}/restore",
                        setup.projectId(), objectMapper.readTree(queryRun).get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESTORED"));

        String ledgerBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/transformation-backups", setup.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.existing").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.restorable").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.prunable").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[?(@.status == 'APPLIED' && @.exists == true"
                        + " && @.restorable == true)]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.status == 'RESTORED' && @.exists == true"
                        + " && @.prunable == true)]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode ledger = objectMapper.readTree(ledgerBody);
        JsonNode appliedBackup = null;
        for (JsonNode item : ledger.get("items")) {
            if (item.get("status").asText().equals("APPLIED")) {
                appliedBackup = item;
            }
        }
        assertThat(appliedBackup).isNotNull();
        String appliedRunId = appliedBackup.get("runId").asText();

        mockMvc.perform(post("/api/v1/projects/{id}/transformation-backups/prune",
                        setup.projectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSFORMATION_CONFIRMATION_REQUIRED"));
        mockMvc.perform(post("/api/v1/projects/{id}/transformation-backups/prune",
                        setup.projectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.failed").value(0));

        String afterBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/transformation-backups", setup.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prunable").value(0))
                .andExpect(jsonPath("$.items[?(@.runId == '" + appliedRunId
                        + "' && @.status == 'APPLIED' && @.exists == true)]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.status == 'RESTORED' && @.exists == false)]")
                        .isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(afterBody).get("total").asInt())
                .isGreaterThanOrEqualTo(2);
        Integer pruneAudit = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where project_id = ? "
                        + "and action = 'TRANSFORMATION_BACKUPS_PRUNED'",
                Integer.class, java.util.UUID.fromString(setup.projectId()));
        assertThat(pruneAudit).isGreaterThan(0);
        deleteRecursively(tempProject);
    }

    @Test
    void comparesMigrationPlanVersionsAndGuardsTheBasePlan() throws Exception {
        Path tempProject = Path.of(System.getProperty("user.dir"), "build",
                "plan-comparison-fixture").toAbsolutePath().normalize();
        deleteRecursively(tempProject);
        copyRecursively(SAMPLE, tempProject);
        PlanSetup setup = createPlanSetup(tempProject, "Plan Comparison Copy");
        JsonNode scopeItem = null;
        for (JsonNode item : setup.items()) {
            if (item.get("included").asBoolean()
                    && item.get("dependentCount").asInt() == 0) {
                scopeItem = item;
                break;
            }
        }
        assertThat(scopeItem).isNotNull();
        mockMvc.perform(patch("/api/v1/projects/{id}/migration-plans/{plan}/items/{item}",
                        setup.projectId(), setup.planId(), scopeItem.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"included\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.included").value(false));

        String secondPlanBody = mockMvc.perform(post(
                        "/api/v1/projects/{id}/migration-plans", setup.projectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfile\":\"SPRING_BOOT_MVC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode secondPlan = objectMapper.readTree(secondPlanBody).get("summary");
        String secondPlanId = secondPlan.get("id").asText();
        assertThat(secondPlan.get("sequence").asInt()).isEqualTo(2);
        String optionsBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/options", setup.projectId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode options = objectMapper.readTree(optionsBody);
        assertThat(options.size()).isGreaterThanOrEqualTo(2);
        assertThat(options.findValuesAsText("id"))
                .contains(setup.planId(), secondPlanId);

        String comparisonBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), secondPlanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.sequence").value(2))
                .andExpect(jsonPath("$.basePlan.sequence").value(1))
                .andExpect(jsonPath("$.summary.added").value(0))
                .andExpect(jsonPath("$.summary.removed").value(0))
                .andExpect(jsonPath("$.summary.changed").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode comparison = objectMapper.readTree(comparisonBody);
        assertThat(comparison.at("/summary/unchanged").asInt())
                .isEqualTo(secondPlan.get("itemCount").asInt() - 1);
        JsonNode changedItem = comparison.get("items").get(0);
        assertThat(changedItem.get("status").asText()).isEqualTo("CHANGED");
        assertThat(changedItem.get("itemKey").asText())
                .isEqualTo(scopeItem.get("itemKey").asText());
        String secondItemsBody = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items",
                        setup.projectId(), secondPlanId)
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String currentItemId = null;
        for (JsonNode item : objectMapper.readTree(secondItemsBody).get("content")) {
            if (item.get("itemKey").asText().equals(scopeItem.get("itemKey").asText())) {
                currentItemId = item.get("id").asText();
            }
        }
        assertThat(currentItemId).isNotNull();
        assertThat(changedItem.get("itemId").asText()).isEqualTo(currentItemId);
        assertThat(changedItem.get("changes").get(0).get("field").asText())
                .isEqualTo("INCLUDED");
        assertThat(changedItem.get("changes").get(0).get("before").asText())
                .isEqualTo("false");
        assertThat(changedItem.get("changes").get(0).get("after").asText())
                .isEqualTo("true");

        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), secondPlanId)
                        .param("basePlanId", setup.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePlan.id").value(setup.planId()))
                .andExpect(jsonPath("$.summary.changed").value(1));
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), secondPlanId)
                        .param("basePlanId", secondPlanId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MIGRATION_PLAN_COMPARISON_SELF"));
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), setup.planId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MIGRATION_PLAN_COMPARISON_UNAVAILABLE"));
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), secondPlanId)
                        .param("basePlanId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MIGRATION_PLAN_NOT_FOUND"));

        Path addedDicon = tempProject.resolve(
                "web-app/src/main/webapp/WEB-INF/dicon/added.dicon");
        Files.writeString(addedDicon, """
                <?xml version="1.0" encoding="UTF-8"?>
                <components>
                    <component name="addedService"
                               class="com.example.legacy.service.OrderService"/>
                </components>
                """);
        rescanProject(setup.projectId());
        String thirdPlanId = createPlan(setup.projectId());
        String thirdComparison = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), thirdPlanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePlan.sequence").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(thirdComparison).at("/summary/added").asInt())
                .isGreaterThan(0);
        assertThat(objectMapper.readTree(thirdComparison).at("/summary/removed").asInt())
                .isZero();
        JsonNode addedRow = null;
        for (JsonNode row : objectMapper.readTree(thirdComparison).get("items")) {
            if (row.get("status").asText().equals("ADDED")) {
                addedRow = row;
                break;
            }
        }
        assertThat(addedRow).isNotNull();
        assertThat(addedRow.hasNonNull("itemId")).isTrue();

        Files.delete(addedDicon);
        rescanProject(setup.projectId());
        String fourthPlanId = createPlan(setup.projectId());
        String fourthComparison = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), fourthPlanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePlan.sequence").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(fourthComparison).at("/summary/removed").asInt())
                .isGreaterThan(0);
        assertThat(objectMapper.readTree(fourthComparison).at("/summary/added").asInt())
                .isZero();
        JsonNode removedRow = null;
        for (JsonNode row : objectMapper.readTree(fourthComparison).get("items")) {
            if (row.get("status").asText().equals("REMOVED")) {
                removedRow = row;
                break;
            }
        }
        assertThat(removedRow).isNotNull();
        assertThat(removedRow.hasNonNull("itemId")).isFalse();

        Path otherProject = Path.of(System.getProperty("user.dir"), "build",
                "plan-comparison-other").toAbsolutePath().normalize();
        deleteRecursively(otherProject);
        copyRecursively(SAMPLE, otherProject);
        PlanSetup other = createPlanSetup(otherProject, "Plan Comparison Other");
        mockMvc.perform(get("/api/v1/projects/{id}/migration-plans/{plan}/comparison",
                        setup.projectId(), fourthPlanId)
                        .param("basePlanId", other.planId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MIGRATION_PLAN_NOT_FOUND"));
        deleteRecursively(tempProject);
        deleteRecursively(otherProject);
    }

    private void rescanProject(String projectId) throws Exception {
        String scanResponse = mockMvc.perform(post("/api/v1/projects/{id}/scans", projectId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode scan = awaitScan(objectMapper.readTree(scanResponse).get("id").asText());
        assertThat(scan.get("status").asText()).isEqualTo("COMPLETED");
    }

    private String createPlan(String projectId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects/{id}/migration-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfile\":\"SPRING_BOOT_MVC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/summary/id").asText();
    }

    private PlanSetup createPlanSetup(Path source, String name) throws Exception {
        String projectResponse = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "sourcePath", source.toString()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();
        String scanResponse = mockMvc.perform(post("/api/v1/projects/{id}/scans", projectId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode scan = awaitScan(objectMapper.readTree(scanResponse).get("id").asText());
        assertThat(scan.get("status").asText()).isEqualTo("COMPLETED");
        String planResponse = mockMvc.perform(post("/api/v1/projects/{id}/migration-plans",
                        projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfile\":\"SPRING_BOOT_MVC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String planId = objectMapper.readTree(planResponse).at("/summary/id").asText();
        String itemsResponse = mockMvc.perform(get(
                        "/api/v1/projects/{id}/migration-plans/{plan}/items", projectId, planId)
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new PlanSetup(projectId, planId,
                objectMapper.readTree(itemsResponse).get("content"));
    }

    private record PlanSetup(String projectId, String planId, JsonNode items) {
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
    void publishesTheVettedTransformationRuleCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/transformation-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ruleId == 'SEASAR-DI-SPRING-DI')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'SEASAR-DI-SPRING-DI'"
                        + " && @.ruleVersion == '1.1.0')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'SASTRUTS-VALIDATION-BEAN')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'SASTRUTS-VALIDATION-BEAN'"
                        + " && @.ruleVersion == '1.1.0')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'STRUTS-ACTIONFORM-MODEL')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'JAVAX-TO-JAKARTA')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'S2JDBC-QUERY-SPRING-JDBC')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'S2JDBC-QUERY-SPRING-JDBC'"
                        + " && @.ruleVersion == '1.3.0')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'S2TX-SPRING-TRANSACTIONAL')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'S2TX-SPRING-TRANSACTIONAL'"
                        + " && @.ruleVersion == '1.2.0')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'STRUTS-CONTROLLER-SKELETON')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'MESSAGE-RESOURCE-SPRING-UTF8')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'MESSAGE-RESOURCE-SPRING-UTF8'"
                        + " && @.language == 'PROPERTIES')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'JSP-BEAN-MESSAGE-JSTL')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'JSP-BEAN-MESSAGE-JSTL'"
                        + " && @.language == 'JSP')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'JSP-BEAN-MESSAGE-JSTL'"
                        + " && @.ruleVersion == '1.2.0')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'WEBXML-JAKARTA-NAMESPACE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'WEBXML-JAKARTA-NAMESPACE'"
                        + " && @.ruleVersion == '1.2.0')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'WEBXML-JAKARTA-NAMESPACE'"
                        + " && @.language == 'XML' && @.fileName == 'web.xml')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'STRUTS-CONFIG-MESSAGE-SPRING')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'STRUTS-CONFIG-MESSAGE-SPRING'"
                        + " && @.language == 'YAML' && @.fileName == 'struts-config.xml')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'DICON-COMPONENT-SPRING-CONFIG')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$[?(@.ruleId == 'DICON-COMPONENT-SPRING-CONFIG'"
                        + " && @.ruleVersion == '1.6.0' && @.language == 'JAVA'"
                        + " && @.fileName == '.dicon')]").isNotEmpty());
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

    private JsonNode awaitPlanRun(String projectId, String planId, String planRunId)
            throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            String response = mockMvc.perform(get(
                            "/api/v1/projects/{id}/migration-plans/{plan}/"
                                    + "transformation-plan-runs/{run}",
                            projectId, planId, planRunId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            last = objectMapper.readTree(response);
            if (!"RUNNING".equals(last.get("status").asText())) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Plan run did not finish: " + last);
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
