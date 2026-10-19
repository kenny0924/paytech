package com.paytech.commercial.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeHistory;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.foundation.common.error.ErrorException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 商务 BCR 审计快照 JSON：{_meta: {schemaVersion, triggerType, triggerId, reasonCode, integrityAlgorithm, integrityHash}, data}
 * <li>沿用 client-master 的信封与完整性元组；完整性哈希为 SHA-256，输入是按键排序的 JSON（所有标量为字符串或 null），未引入
 * RFC 8785 规范化 JAR。</li>
 * <li>同一命令写出的全部快照使用同一 triggerId；运营命令 triggerType=COMMAND，定时激活等系统命令为 SYSTEM。</li>
 */
public final class BusinessChangeSnapshots {
    /** 运营命令触发。 */
    public static final String TRIGGER_COMMAND = "COMMAND";

    /** 系统命令（定时激活）触发。 */
    public static final String TRIGGER_SYSTEM = "SYSTEM";

    /** 完整性元组的领域分隔。 */
    private static final String DOMAIN = "CODE-MOR:COMMERCIAL:BUSINESS_CHANGE_SNAPSHOT:1";

    /** 快照上限 64 KiB。 */
    private static final int MAX_BYTES = 65536;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 纯静态工具。 */
    private BusinessChangeSnapshots() {
    }

    /**
     * 为一条待插入快照生成 JSON
     *
     * @param history 所属审计身份
     * @param version 待插入快照（versionNo、operationType、createBy、createTime 已填）
     * @param triggerType COMMAND 或 SYSTEM
     * @param triggerId 本次命令的关联 ID
     * @param reasonCode 变化原因
     * @param data 白名单字段，值为字符串或 null
     * @return 快照 JSON
     */
    public static String snapshot(BusinessChangeHistory history, BusinessChangeVersion version, String triggerType,
        long triggerId, String reasonCode, Map<String, String> data) {
        Map<String, Object> meta = new TreeMap<>();
        meta.put("schemaVersion", "1");
        meta.put("triggerType", triggerType);
        meta.put("triggerId", Long.toString(triggerId));
        meta.put("reasonCode", reasonCode);
        meta.put("integrityAlgorithm", "SHA-256");
        Map<String, Object> envelope = new TreeMap<>();
        envelope.put("_meta", meta);
        envelope.put("data", new TreeMap<>(data));
        meta.put("integrityHash", integrity(history, version, envelope));
        String json = JSON.writeValueAsString(envelope);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ErrorException("审计快照超过 64 KiB: " + history.getRecordType().getCode() + ":" + history.getRecordId());
        }
        return json;
    }

    /**
     * 读取快照的变化原因
     *
     * @param snapshotJson 快照 JSON
     * @return reasonCode，缺失为 null
     */
    public static String reasonCode(String snapshotJson) {
        JsonNode reason = JSON.readTree(snapshotJson).path("_meta").path("reasonCode");
        return reason.isString() ? reason.asString() : null;
    }

    /** 完整性哈希：审计身份元组、版本坐标与不含哈希的信封。 */
    static String integrity(BusinessChangeHistory history, BusinessChangeVersion version, Map<String, Object> envelope) {
        Map<String, Object> tuple = new TreeMap<>();
        tuple.put("domain", DOMAIN);
        tuple.put("record_type", history.getRecordType().getCode());
        tuple.put("record_id", history.getRecordId());
        tuple.put("aggregate_root_type", history.getAggregateRootType().getCode());
        tuple.put("aggregate_root_id", history.getAggregateRootId());
        tuple.put("parent_history_id", text(history.getParentHistoryId()));
        tuple.put("history_id", text(history.getHistoryId()));
        tuple.put("version_no", text(version.getVersionNo()));
        tuple.put("operation_type", version.getOperationType().getCode());
        tuple.put("create_by", text(version.getCreateBy()));
        tuple.put("create_time", micros(version.getCreateTime()));
        tuple.put("snapshot", envelope);
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(tuple)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new ErrorException("SHA-256 不可用", unavailable);
        }
    }

    /** 十进制文本，空为 null。 */
    static String text(Long value) {
        return value == null ? null : value.toString();
    }

    /** UTC 微秒文本。 */
    static String micros(Instant value) {
        return new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(value);
    }
}
