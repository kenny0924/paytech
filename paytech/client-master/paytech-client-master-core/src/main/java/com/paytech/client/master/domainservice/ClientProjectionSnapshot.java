package com.paytech.client.master.domainservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import com.paytech.client.master.core.dal.dataobject.ClientProjectionHistory;
import com.paytech.client.master.domain.enums.*;
import com.paytech.client.master.domain.onboarding.ClientProjectionModels.Source;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** CLIENT 审计目录：仅稳定身份和状态；无个人资料、技术租约或 Vault 值。 */
public final class ClientProjectionSnapshot {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DOMAIN = "CODE-MOR:CCT:BUSINESS_CHANGE_SNAPSHOT:1";
    private static final Set<String> REASONS = Set.of("CLIENT_CREATED", "CLIENT_UPDATED", "CLIENT_ACTIVATED",
        "CLIENT_SUSPENDED", "CLIENT_TERMINATED");
    private ClientProjectionSnapshot() {}

    /** 创建当前目录的不可变来源；与 BCR 行一起写入同一本地事务。 */
    public static String snapshot(ClientProjectionHistory row, ClientStatus status, String triggerId, String reason) {
        validateRow(row);
        if (status == null || !REASONS.contains(reason) || triggerId == null || !triggerId.matches("[1-9][0-9]{0,18}")) {
            throw invalid();
        }
        Map<String, Object> meta = new LinkedHashMap<>(Map.of("schemaVersion", 1, "triggerType", "COMMAND",
            "triggerId", triggerId, "reasonCode", reason, "integrityAlgorithm", "SHA-256"));
        Map<String, Object> envelope = Map.of("_meta", meta, "data", Map.of("clientId", row.recordId(),
            "partyId", Long.toString(row.partyId()), "clientNo", row.clientNo(), "status", status.getCode()));
        meta.put("integrityHash", integrity(row, envelope));
        String result = JSON.writeValueAsString(envelope);
        read(withSnapshot(row, result));
        return result;
    }

    /** 不从最新状态或表单推导事件；同时校验物理关联、封闭字段集和审计完整性。 */
    public static Source read(ClientProjectionHistory row) {
        validateRow(row);
        try {
            if (row.snapshotJson() == null || row.snapshotJson().length() > 65536) { throw invalid(); }
            byte[] bytes = row.snapshotJson().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65536) { throw invalid(); }
            byte[] canonical = Rfc8785JsonCanonicalizer.canonicalizeObject(bytes);
            Map<String, Object> envelope = JSON.readValue(canonical, new TypeReference<LinkedHashMap<String, Object>>() {});
            keys(envelope, Set.of("_meta", "data"));
            Map<String, Object> meta = object(envelope.get("_meta"));
            Map<String, Object> data = object(envelope.get("data"));
            keys(meta, Set.of("schemaVersion", "triggerType", "triggerId", "reasonCode", "integrityAlgorithm", "integrityHash"));
            keys(data, Set.of("clientId", "partyId", "clientNo", "status"));
            if (!Integer.valueOf(1).equals(meta.get("schemaVersion")) || !"COMMAND".equals(meta.get("triggerType"))
                || !"SHA-256".equals(meta.get("integrityAlgorithm")) || !REASONS.contains(text(meta, "reasonCode"))
                || !text(meta, "triggerId").matches("[1-9][0-9]{0,18}")
                || !row.recordId().equals(data.get("clientId")) || !Long.toString(row.partyId()).equals(data.get("partyId"))
                || !row.clientNo().equals(data.get("clientNo"))) { throw invalid(); }
            String hash = text(meta, "integrityHash");
            meta.remove("integrityHash");
            if (!hash.matches("[a-f0-9]{64}") || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                    integrity(row, envelope).getBytes(StandardCharsets.US_ASCII))) { throw invalid(); }
            ClientStatus status = ClientStatus.valueOf(text(data, "status"));
            if (row.sourceVersion() == 1 && status != ClientStatus.PENDING) { throw invalid(); }
            String payloadHash = sha(Map.of("domain", "CODE-MOR:CCT:CLIENT_TENANT_PROJECTION:1", "clientId", row.recordId(),
                "requestedByAccountId", Long.toString(row.applicantId()), "sourceVersion", Long.toString(row.sourceVersion()),
                "ownerStatus", status.getCode(), "sourceIntegrityHash", hash));
            return new Source(row.clientId(), row.applicantId(), Math.toIntExact(row.sourceVersion()), status.getCode(), payloadHash);
        } catch (RuntimeException malformed) { throw invalid(); }
    }

    private static void validateRow(ClientProjectionHistory row) {
        if (row == null || row.changeVersionId() <= 0 || row.historyId() <= 0 || row.clientId() <= 0 || row.partyId() <= 0
            || row.actorId() <= 0 || row.applicantId() <= 0 || row.applicationCount() < 1 || row.sourceVersion() < 1
            || row.sourceVersion() > Integer.MAX_VALUE || !Long.toString(row.clientId()).equals(row.recordId())
            || row.clientNo() == null || row.clientNo().isBlank() || row.clientNo().length() > 64
            || row.snapshotFormat() != BusinessChangeSnapshotFormat.ENTITY_NO || row.occurredAt() == null
            || row.occurredAt().getNano() % 1000 != 0
            || row.operation() != (row.sourceVersion() == 1 ? BusinessChangeOperation.CREATE : BusinessChangeOperation.UPDATE)
            || (row.sourceVersion() == 1 && row.actorId() != row.applicantId())) { throw invalid(); }
    }
    private static String integrity(ClientProjectionHistory row, Map<String, Object> envelope) {
        Map<String, Object> tuple = new LinkedHashMap<>();
        tuple.put("domain", DOMAIN);
        tuple.put("record_type", "CLIENT"); tuple.put("record_id", row.recordId());
        tuple.put("aggregate_root_type", "CLIENT"); tuple.put("aggregate_root_id", row.recordId());
        tuple.put("parent_history_id", null); tuple.put("history_id", Long.toString(row.historyId()));
        tuple.put("version_no", Long.toString(row.sourceVersion())); tuple.put("operation_type", row.operation().getCode());
        tuple.put("create_by", Long.toString(row.actorId()));
        tuple.put("create_time", new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(row.occurredAt()));
        tuple.put("snapshot", envelope);
        return sha(tuple);
    }
    private static String sha(Map<String, Object> value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(value)))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static void keys(Map<String, Object> value, Set<String> names) { if (!value.keySet().equals(names)) { throw invalid(); } }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) { throw invalid(); }
        return (Map<String, Object>) map;
    }
    private static String text(Map<String, Object> value, String key) {
        if (!(value.get(key) instanceof String result)) { throw invalid(); }
        return result;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Client projection source is invalid"); }
    private static ClientProjectionHistory withSnapshot(ClientProjectionHistory row, String snapshot) {
        return new ClientProjectionHistory(row.changeVersionId(), row.historyId(), row.recordId(), row.sourceVersion(),
            row.operation(), row.snapshotFormat(), snapshot, row.actorId(), row.occurredAt(), row.clientId(), row.partyId(),
            row.clientNo(), row.applicantId(), row.applicationCount());
    }
}
