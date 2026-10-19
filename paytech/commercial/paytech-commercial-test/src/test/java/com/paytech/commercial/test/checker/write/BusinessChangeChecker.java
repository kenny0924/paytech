package com.paytech.commercial.test.checker.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * BCR 记录与快照的只读断言：版本坐标、操作序列、快照原因与 data 字段，并按 DATABASE_DESIGN_GUIDE §9.1 的冻结输入独立重算完整性哈希
 * <li>预期值来自用例约定；哈希按文档定义的 Tuple 在测试侧重新组装，不调用被测快照构造器。</li>
 */
@TestComponent
public class BusinessChangeChecker {
    /** Integrity Domain Tag（COMMERCIAL_PHYSICAL_DECISIONS §4）。 */
    static final String DOMAIN = "CODE-MOR:CCT:BUSINESS_CHANGE_SNAPSHOT:1";

    private final CommercialTestMapper mapper;

    private final JsonMapper json = JsonMapper.builder().build();

    /** 注入测试 Mapper。 */
    public BusinessChangeChecker(CommercialTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 读取一条历史身份及其快照
     *
     * @param recordType 记录类型
     * @param recordId 业务行 ID
     * @return 记录检查器
     */
    public Record record(String recordType, long recordId) {
        Map<String, Object> row = mapper.businessChangeRecord(recordType, recordId);
        assertNotNull(row, "缺少 BCR 记录：" + recordType + " " + recordId);
        return new Record(row, mapper.businessChangeVersions(((Number) row.get("history_id")).longValue()));
    }

    /** 一条历史身份的断言。 */
    public class Record {
        private final Map<String, Object> row;
        private final List<Map<String, Object>> versions;

        Record(Map<String, Object> row, List<Map<String, Object>> versions) {
            this.row = row;
            this.versions = versions;
        }

        /** 当前版本。 */
        public Record assertCurrentVersion(long expected) {
            assertEquals(expected, ((Number) row.get("current_version")).longValue(), "current_version 不符");
            return this;
        }

        /** 父级为给定记录的 history_id（Root 传 null）。 */
        public Record assertParent(Record parent) {
            Object actual = row.get("parent_history_id");
            assertEquals(parent == null ? null : parent.historyId(), actual == null ? null : ((Number) actual).longValue(),
                "parent_history_id 不符");
            return this;
        }

        /** 快照按版本升序的 版本:操作 序列。 */
        public Record assertVersions(String... expected) {
            assertEquals(List.of(expected), versions.stream()
                .map(version -> version.get("version_no") + ":" + version.get("operation_type")).toList(), "快照序列不符");
            return this;
        }

        /** 每条快照的格式、Schema 与完整性哈希。 */
        public Record assertIntegrity() {
            assertTrue(!versions.isEmpty(), "没有快照");
            for (Map<String, Object> version : versions) {
                assertEquals("ENTITY_NO", version.get("snapshot_format"));
                JsonNode snapshot = json.readTree((String) version.get("snapshot_json"));
                JsonNode meta = snapshot.path("_meta");
                assertEquals(1, meta.path("schemaVersion").asInt());
                assertEquals("COMMAND", meta.path("triggerType").asString());
                assertEquals("SHA-256", meta.path("integrityAlgorithm").asString());
                assertTrue(meta.path("triggerId").asString().matches("[1-9][0-9]{0,18}"), "triggerId 格式不符");
                assertEquals(meta.path("integrityHash").asString(), recompute(version), "完整性哈希不符");
            }
            return this;
        }

        /** 最新快照的 reasonCode。 */
        public Record assertLastReason(String expected) {
            assertEquals(expected, last().path("_meta").path("reasonCode").asString(), "reasonCode 不符");
            return this;
        }

        /** 最新快照 data 的文本字段。 */
        public Record assertLastData(String field, String expected) {
            assertEquals(expected, last().path("data").path(field).asString(null), "快照字段不符：" + field);
            return this;
        }

        /** 最新快照的 create_by。 */
        public Record assertLastActor(long expected) {
            assertEquals(expected, ((Number) versions.getLast().get("create_by")).longValue(), "create_by 不符");
            return this;
        }

        /** 最新快照。 */
        public JsonNode last() {
            return json.readTree((String) versions.getLast().get("snapshot_json"));
        }

        /** 最新快照的 triggerId。 */
        public String lastTriggerId() {
            return last().path("_meta").path("triggerId").asString();
        }

        /** history_id。 */
        public long historyId() {
            return ((Number) row.get("history_id")).longValue();
        }

        /** 按冻结输入重算哈希。 */
        String recompute(Map<String, Object> version) {
            Map<String, Object> envelope = json.readValue((String) version.get("snapshot_json"),
                new TypeReference<LinkedHashMap<String, Object>>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) envelope.get("_meta");
            meta.remove("integrityHash");
            Object parent = row.get("parent_history_id");
            Map<String, Object> tuple = new LinkedHashMap<>();
            tuple.put("domain", DOMAIN);
            tuple.put("record_type", row.get("record_type"));
            tuple.put("record_id", row.get("record_id"));
            tuple.put("aggregate_root_type", row.get("aggregate_root_type"));
            tuple.put("aggregate_root_id", row.get("aggregate_root_id"));
            tuple.put("parent_history_id", parent == null ? null : parent.toString());
            tuple.put("history_id", row.get("history_id").toString());
            tuple.put("version_no", version.get("version_no").toString());
            tuple.put("operation_type", version.get("operation_type"));
            tuple.put("create_by", version.get("create_by").toString());
            tuple.put("create_time", new DateTimeFormatterBuilder().appendInstant(6).toFormatter()
                .format(instant(version.get("create_time"))));
            tuple.put("snapshot", envelope);
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Rfc8785JsonCanonicalizer.canonicalizeObject(json.writeValueAsBytes(tuple))));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    /**
     * DATETIME(6) 读回为 UTC Instant
     *
     * @param value JDBC 读回值
     * @return UTC Instant
     */
    public static Instant instant(Object value) {
        if (value instanceof LocalDateTime local) {
            return local.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return (Instant) value;
    }

}
