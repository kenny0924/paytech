package com.paytech.risk.auto;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import com.paytech.risk.compliance.core.configuration.MaterialRequirementsProperties;
import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import tools.jackson.databind.ObjectMapper;
/**
 * 材料目录从真实 HTTP/MySQL 查询，所有程序映射只使用合成夹具；材料按资格类型通用，不按国家区分。
 *
 * @see com.paytech.risk.api.compliance.MaterialRequirementsApi#read(String, String)
 */
@TestPropertySource(properties="onboarding.materials.enabled=true")
class MaterialRequirementsHttpAutoTest extends RiskServiceAutoTestBase {
    private static final String PATH="/internal/due-diligence/material-requirements";
    @Autowired JdbcTemplate jdbc;
    @Autowired DecisionIdentifierGenerator ids;
    @Autowired ObjectMapper json;
    @Autowired MaterialRequirementsProperties materials;
    private final List<Long> programs=new ArrayList<>();
    private final List<Long> versions=new ArrayList<>();
    private String originalPrograms;
    private org.apache.hc.client5.http.impl.classic.CloseableHttpClient transport;
    private RestTestClient http;
    private long program,version;
    private String code;
    /** 每个测试只配置自有程序，不触及真实目录。 */
    @BeforeEach void prepare() {
        transport=org.apache.hc.client5.http.impl.classic.HttpClients.custom().disableAutomaticRetries().build();
        http=RestTestClient.bindToServer(new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(transport)).baseUrl("http://127.0.0.1:"+port).build();
        originalPrograms=materials.getProgramsJson();
        program=ids.nextIdentifier();version=ids.nextIdentifier();code="SYNTHETIC_"+program;programs.add(program);versions.add(version);
        jdbc.update("INSERT INTO t_dd_program(program_id,program_code,qualification_type,status,create_time,update_time) VALUES(?,?,'INDIVIDUAL_KYC','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",program,code);
        jdbc.update("INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,status,active_slot,content_hash,approved_by,approved_time,create_time,update_time) VALUES(?,?,1,'ACTIVE',1,?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",version,program,"a".repeat(64));
        jdbc.update("INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(?,?,'SYNTHETIC_ID','SYNTHETIC_DOCUMENT',1,UTC_TIMESTAMP(6))",ids.nextIdentifier(),version);
        mapping(List.of(row("INDIVIDUAL_KYC",code)));
    }
    /** 只清理记录过的测试主键，验证过程不改动任何业务目录。 */
    @AfterEach void cleanup() throws java.io.IOException {
        for (long id:versions) { jdbc.update("DELETE FROM t_dd_program_requirement WHERE program_version_id=?",id);jdbc.update("DELETE FROM t_dd_program_version WHERE program_version_id=?",id); }
        for (long id:programs) jdbc.update("DELETE FROM t_dd_program WHERE program_id=?",id);
        materials.setProgramsJson(originalPrograms);transport.close();
    }
    /** 三类资格都按配置选择同类型通用程序，不返回无关材料。 */
    @ParameterizedTest @ValueSource(strings={"INDIVIDUAL_KYC","ORGANIZATION_KYB","RELATED_PERSON_KYC"})
    void exactProgramReturnsCurrentRequirements(String type) {
        jdbc.update("UPDATE t_dd_program SET qualification_type=? WHERE program_id=?",type,program);mapping(List.of(row(type,code)));
        jdbc.update("INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(?,?,'SYNTHETIC_OPTIONAL','SYNTHETIC_OTHER',0,UTC_TIMESTAMP(6))",ids.nextIdentifier(),version);
        var before=jdbc.queryForList("SELECT * FROM t_dd_program_version WHERE program_id=?",program);
        var response=json.readTree(read(type).expectStatus().isOk().expectHeader().valueEquals("Cache-Control","no-store").expectBody(String.class).returnResult().getResponseBody());
        assertEquals(Long.toString(program),response.path("programId").asText());assertEquals(Long.toString(version),response.path("programVersionId").asText());
        assertEquals(2,response.path("items").size());assertEquals("SYNTHETIC_ID",response.path("items").get(0).path("requirementCode").asText());assertTrue(response.path("items").get(0).path("requiredFlag").asBoolean());assertFalse(response.path("items").get(1).path("requiredFlag").asBoolean());
        assertEquals(before,jdbc.queryForList("SELECT * FROM t_dd_program_version WHERE program_id=?",program));
        read(type.equals("INDIVIDUAL_KYC")?"ORGANIZATION_KYB":"INDIVIDUAL_KYC").expectStatus().isEqualTo(503);
    }
    /** 未配置资格类型或映射程序不存在时明确不可用，不回退到第一个 ACTIVE。 */
    @Test void missingMappingIsUnavailable() {
        mapping(List.of(row("RELATED_PERSON_KYC",code)));read("INDIVIDUAL_KYC").expectStatus().isEqualTo(503);
        mapping(List.of(row("INDIVIDUAL_KYC","SYNTHETIC_MISSING")));read("INDIVIDUAL_KYC").expectStatus().isEqualTo(503);
        mapping(List.of(row("ORGANIZATION_KYB",code)));read("ORGANIZATION_KYB").expectStatus().isEqualTo(503);
    }
    /** 配置结构、重复资格、旧国家字段和未知资格类型均失败关闭。 */
    @ParameterizedTest @ValueSource(strings={"","null","{}","[]","[1]","[{}]","[{\"qualificationType\":1,\"programCode\":\"SYNTHETIC\"}]","DUPLICATE","UNKNOWN_TYPE","COUNTRY_FIELD","BAD_CODE","OVERSIZED","TRAILING","DUPLICATE_FIELD"})
    void invalidMappingNeverProvidesAnEmptyPass(String invalid) {
        String valid=json.writeValueAsString(List.of(row("INDIVIDUAL_KYC",code)));
        String value=switch(invalid) {
            case "DUPLICATE" -> json.writeValueAsString(List.of(row("INDIVIDUAL_KYC",code),row("INDIVIDUAL_KYC",code)));
            case "UNKNOWN_TYPE" -> json.writeValueAsString(List.of(row("ENTITY_UNDERWRITING",code)));
            case "COUNTRY_FIELD" -> json.writeValueAsString(List.of(Map.of("qualificationType","INDIVIDUAL_KYC","countryCode","ZZ","programCode",code)));
            case "BAD_CODE" -> json.writeValueAsString(List.of(row("INDIVIDUAL_KYC","lower")));
            case "OVERSIZED" -> " ".repeat(65537);
            case "TRAILING" -> valid+"{}";
            case "DUPLICATE_FIELD" -> valid.replace("\"qualificationType\":\"INDIVIDUAL_KYC\"","\"qualificationType\":\"INDIVIDUAL_KYC\",\"qualificationType\":\"INDIVIDUAL_KYC\"");
            default -> invalid;
        };
        materials.setProgramsJson(value);read("INDIVIDUAL_KYC").expectStatus().isEqualTo(503);
    }
    /** 尚未批准、未来批准、无有效修订或损坏占位/摘要都不可作为材料权威。 */
    @ParameterizedTest @ValueSource(strings={"PROGRAM_RETIRED","VERSION_RETIRED","NO_SLOT","NO_APPROVER","NO_APPROVAL_TIME","FUTURE","BAD_HASH","BAD_VERSION"})
    void invalidProgramIsUnavailable(String change) {
        switch(change) {
            case "PROGRAM_RETIRED" -> jdbc.update("UPDATE t_dd_program SET status='RETIRED' WHERE program_id=?",program);
            case "VERSION_RETIRED" -> jdbc.update("UPDATE t_dd_program_version SET status='RETIRED',active_slot=NULL WHERE program_version_id=?",version);
            case "NO_SLOT" -> jdbc.update("UPDATE t_dd_program_version SET active_slot=NULL WHERE program_version_id=?",version);
            case "NO_APPROVER" -> jdbc.update("UPDATE t_dd_program_version SET approved_by=NULL WHERE program_version_id=?",version);
            case "NO_APPROVAL_TIME" -> jdbc.update("UPDATE t_dd_program_version SET approved_time=NULL WHERE program_version_id=?",version);
            case "FUTURE" -> jdbc.update("UPDATE t_dd_program_version SET approved_time=? WHERE program_version_id=?",Timestamp.from(Instant.now().plusSeconds(3600)),version);
            case "BAD_HASH" -> jdbc.update("UPDATE t_dd_program_version SET content_hash='bad' WHERE program_version_id=?",version);
            default -> jdbc.update("UPDATE t_dd_program_version SET version_no=0 WHERE program_version_id=?",version);
        }
        read("INDIVIDUAL_KYC").expectStatus().isEqualTo(503);
    }
    /** 两个 ACTIVE 修订即使只有一个占位有效也不能选择第一条掩盖目录损坏。 */
    @Test void ambiguousActiveRevisionFailsClosed() {
        long extra=ids.nextIdentifier();versions.add(extra);
        jdbc.update("INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,status,active_slot,content_hash,approved_by,approved_time,create_time,update_time) VALUES(?,?,2,'ACTIVE',NULL,?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",extra,program,"b".repeat(64));
        read("INDIVIDUAL_KYC").expectStatus().isEqualTo(503);
    }
    /** 材料闭包损坏不能被投影成默认合格的空清单。 */
    @ParameterizedTest @ValueSource(strings={"EMPTY","BAD_REQUIREMENT","BAD_TYPE","BAD_REQUIRED_FLAG"})
    void malformedRequirementsAreUnavailable(String change) {
        switch(change) {
            case "EMPTY" -> jdbc.update("DELETE FROM t_dd_program_requirement WHERE program_version_id=?",version);
            case "BAD_REQUIREMENT" -> jdbc.update("UPDATE t_dd_program_requirement SET requirement_code='bad' WHERE program_version_id=?",version);
            case "BAD_TYPE" -> jdbc.update("UPDATE t_dd_program_requirement SET document_type='' WHERE program_version_id=?",version);
            default -> jdbc.update("UPDATE t_dd_program_requirement SET required_flag=2 WHERE program_version_id=?",version);
        }
        read("INDIVIDUAL_KYC").expectStatus().isEqualTo(503);
    }
    /** C03 页面聚合只读取通用材料目录，BFF Caller 不能扩展资格类型。 */
    @Test void clientBffReadsConfiguredMaterialCatalog() {
        mapping(List.of(row("INDIVIDUAL_KYC",code)));
        http.get().uri(PATH+"?qualificationType=INDIVIDUAL_KYC")
            .header("X-Caller-Service","client-console-bff").exchange().expectStatus().isOk();
    }
    /** 协议只允许资格类型一个无歧义参数，旧国家参数按未知参数拒绝。 */
    @Test void invalidRequestIsRejected() {
        for (String query:List.of("","qualificationType=ENTITY_UNDERWRITING","qualificationType=INDIVIDUAL_KYC&countryCode=ZZ","qualificationType=INDIVIDUAL_KYC&qualificationType=ORGANIZATION_KYB","qualificationType=INDIVIDUAL_KYC&unknown=1","qualificationType="))
            http.get().uri(PATH+"?"+query).header("X-Caller-Service","client-contract-service").exchange().expectStatus().isBadRequest();
    }
    /** 独立 HTTP 读取，不绕过协议或事务。 */
    private RestTestClient.ResponseSpec read(String type) { return http.get().uri(PATH+"?qualificationType="+type).header("X-Caller-Service","client-contract-service").exchange(); }
    /** 只设置当前测试的明确映射。 */
    private void mapping(List<Map<String,String>> rows) { materials.setProgramsJson(json.writeValueAsString(rows)); }
    /** 合成映射不包含真实材料政策。 */
    private Map<String,String> row(String type,String programCode) { return Map.of("qualificationType",type,"programCode",programCode); }
}
