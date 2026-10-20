package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;

import lombok.Getter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 一条变更快照：历史身份与快照行的两表组合，快照信封解析为触发信息与白名单审计视图
 * <li>只对外给出 _meta 的 triggerType、triggerId、reasonCode；完整性算法与哈希不外显。data 按快照原样（保留键顺序）返回。</li>
 */
@Getter
public class BusinessChangeEntryView {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 历史身份 ID */
    private final long historyId;

    /** 受审计对象类型 */
    private final BusinessChangeRecordRecordType recordType;

    /** 受审计业务行 ID */
    private final String recordId;

    /** 聚合根版本坐标 */
    private final long versionNo;

    /** 行操作类型 */
    private final BusinessChangeOperation operationType;

    /** 操作者 */
    private final long createBy;

    /** 快照创建时间 */
    private final Instant createTime;

    /** 触发类型 */
    private final String triggerType;

    /** 触发标识 */
    private final String triggerId;

    /** 业务原因代码 */
    private final String reasonCode;

    /** 白名单审计视图 */
    private final Map<String, Object> data;

    /**
     * 由历史身份与其一条快照构造
     *
     * @param record 历史身份
     * @param snapshot 快照行
     */
    public BusinessChangeEntryView(BusinessChangeRecord record, BusinessChangeVersion snapshot) {
        JsonNode envelope = JSON.readTree(snapshot.getSnapshotJson());
        JsonNode meta = envelope.path("_meta");
        this.historyId = record.getHistoryId();
        this.recordType = record.getRecordType();
        this.recordId = record.getRecordId();
        this.versionNo = snapshot.getVersionNo();
        this.operationType = snapshot.getOperationType();
        this.createBy = snapshot.getCreateBy();
        this.createTime = snapshot.getCreateTime();
        this.triggerType = meta.path("triggerType").asString(null);
        this.triggerId = meta.path("triggerId").asString(null);
        this.reasonCode = meta.path("reasonCode").asString(null);
        this.data = JSON.convertValue(envelope.path("data"), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }
}
