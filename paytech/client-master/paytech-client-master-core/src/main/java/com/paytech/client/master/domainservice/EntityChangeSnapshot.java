package com.paytech.client.master.domainservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import tools.jackson.databind.json.JsonMapper;

/**
 * OPERATING_ENTITY / LOCAL_PARTNER 根的 UPDATE 审计快照（运营主档维护）
 * <li>与 CLIENT 快照同一封装：_meta（schemaVersion、COMMAND、triggerId、reasonCode、运营原因、SHA-256 完整性哈希）+ data。</li>
 * <li>完整性哈希覆盖根身份、history_id、version_no、操作类型、操作者、时间与不含哈希的快照，按 RFC 8785 规范化后计算。</li>
 * <li>data 只放稳定身份与本次维护的属性，不含个人资料。</li>
 */
public final class EntityChangeSnapshot {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DOMAIN = "CODE-MOR:CCT:BUSINESS_CHANGE_SNAPSHOT:1";
    private static final int MAX_BYTES = 65536;

    /** 工具类不创建实例。 */
    private EntityChangeSnapshot() {
    }

    /**
     * 构造一条 UPDATE 快照
     *
     * @param rootType 根类型（同时是 record_type 与 aggregate_root_type）
     * @param rootId 根业务标识
     * @param historyId 根 history_id
     * @param versionNo 本次推进后的根版本
     * @param actorId 操作者 Account ID
     * @param now 写入时间，微秒精度
     * @param triggerId 本次快照的 change_version_id
     * @param reasonCode 稳定原因 Code
     * @param reason 运营填写的原因
     * @param data 快照业务字段，值只能是字符串、布尔或字符串列表
     * @return 快照 JSON，不超过 64 KiB
     */
    public static String update(BusinessChangeRecordRecordType rootType, String rootId, long historyId, long versionNo,
        long actorId, Instant now, long triggerId, String reasonCode, String reason, Map<String, Object> data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", 1);
        meta.put("triggerType", "COMMAND");
        meta.put("triggerId", Long.toString(triggerId));
        meta.put("reasonCode", reasonCode);
        meta.put("reason", reason);
        meta.put("integrityAlgorithm", "SHA-256");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("_meta", meta);
        envelope.put("data", data);
        meta.put("integrityHash", integrity(rootType, rootId, historyId, versionNo, actorId, now, envelope));
        String result = JSON.writeValueAsString(envelope);
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ErrorException("审计快照超过 64 KiB: " + rootType.getCode() + ":" + rootId);
        }
        return result;
    }

    /** 根身份、版本坐标与不含哈希的快照的 SHA-256。 */
    private static String integrity(BusinessChangeRecordRecordType rootType, String rootId, long historyId, long versionNo,
        long actorId, Instant now, Map<String, Object> envelope) {
        Map<String, Object> tuple = new LinkedHashMap<>();
        tuple.put("domain", DOMAIN);
        tuple.put("record_type", rootType.getCode());
        tuple.put("record_id", rootId);
        tuple.put("aggregate_root_type", rootType.getCode());
        tuple.put("aggregate_root_id", rootId);
        tuple.put("parent_history_id", null);
        tuple.put("history_id", Long.toString(historyId));
        tuple.put("version_no", Long.toString(versionNo));
        tuple.put("operation_type", "UPDATE");
        tuple.put("create_by", Long.toString(actorId));
        tuple.put("create_time", new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(now));
        tuple.put("snapshot", envelope);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(tuple))));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ErrorException("JDK 缺少 SHA-256", impossible);
        }
    }
}
