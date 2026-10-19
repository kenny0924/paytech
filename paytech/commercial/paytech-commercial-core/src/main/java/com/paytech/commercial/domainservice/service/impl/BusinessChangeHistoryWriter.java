package com.paytech.commercial.domainservice.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.commercial.domain.BusinessChangeSnapshot;
import com.paytech.commercial.domain.enums.BusinessChangeOperation;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.BusinessChangeSnapshotFormat;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;

import lombok.AllArgsConstructor;

/**
 * BCR 写入协作者：供写入 Service 在其本地事务内调用，按 DATABASE_DESIGN_GUIDE §9.1 / 9.1.1 维护 Root CAS、Child 指针与不可变快照
 * <li>不是业务 Service：只组合 t_business_change_record 与 t_business_change_version 两个仓储，不开启事务、不访问外部依赖；
 * 调用方必须已处于写事务中。</li>
 * <li>同一命令的 Root 与 Child 快照共享 triggerId（取 Root 本次快照的 change_version_id）与 triggerType；运营命令为
 * COMMAND，系统命令（如条款版本定时激活）为 SYSTEM。</li>
 */
@AllArgsConstructor
public final class BusinessChangeHistoryWriter {
    /** 快照上限（字节），与表注释一致。 */
    static final int MAX_SNAPSHOT_BYTES = 65536;

    private final IdGenerator idGenerator;
    private final BusinessChangeRecordRepository recordRepository;
    private final BusinessChangeVersionRepository versionRepository;

    /**
     * 创建聚合 Root：current_version = 1，并追加 Version 1 CREATE 快照
     *
     * @param rootType 聚合根类型（同时是 record_type）
     * @param rootId 聚合根 ID
     * @param reason 业务原因
     * @param data 审计视图
     * @param actorId 操作者
     * @param now 本次时间
     * @return 已插入的 Root
     */
    public BusinessChangeRecord createRoot(BusinessChangeRecordAggregateRootType rootType, long rootId,
        BusinessChangeReason reason, Map<String, Object> data, long actorId, Instant now) {
        BusinessChangeRecord root = record(BusinessChangeRecordRecordType.valueOf(rootType.getCode()), rootId, rootType,
            Long.toString(rootId), null, 1L, now);
        recordRepository.insert(root);
        long versionId = idGenerator.nextId();
        appendVersion(root, versionId, BusinessChangeOperation.CREATE, BusinessChangeSnapshot.TRIGGER_COMMAND, versionId,
            reason, data, actorId, now);
        return root;
    }

    /**
     * Root CAS 推进一次并追加 UPDATE 快照
     *
     * @param root 已读取的 Root
     * @param expectedVersion 外部命令携带的 expected_change_version
     * @param reason 业务原因
     * @param data 本次提交后的 Root 审计视图
     * @param actorId 操作者
     * @param now 本次时间
     * @return 本命令的 triggerId，供 Child 快照关联
     */
    public long advanceRoot(BusinessChangeRecord root, long expectedVersion, BusinessChangeReason reason,
        Map<String, Object> data, long actorId, Instant now) {
        return advanceRoot(root, expectedVersion, BusinessChangeSnapshot.TRIGGER_COMMAND, reason, data, actorId, now);
    }

    /**
     * Root CAS 推进一次并追加 UPDATE 快照，显式给出触发类型
     *
     * @param root 已读取的 Root
     * @param expectedVersion 期望版本（运营命令来自请求，系统命令为锁内读取的当前值）
     * @param triggerType COMMAND 或 SYSTEM
     * @param reason 业务原因
     * @param data 本次提交后的 Root 审计视图
     * @param actorId 操作者（系统命令为 0）
     * @param now 本次时间
     * @return 本命令的 triggerId，供 Child 快照关联
     */
    public long advanceRoot(BusinessChangeRecord root, long expectedVersion, String triggerType,
        BusinessChangeReason reason, Map<String, Object> data, long actorId, Instant now) {
        recordRepository.advanceRoot(root, expectedVersion, now);
        long versionId = idGenerator.nextId();
        appendVersion(root, versionId, BusinessChangeOperation.UPDATE, triggerType, versionId, reason, data, actorId, now);
        return versionId;
    }

    /**
     * 为新建的受审计 Child 创建历史身份（指针等于 Root 当前版本）并追加 CREATE 快照
     *
     * @param root 已推进到本次版本的 Root
     * @param childType Child 类型
     * @param childId Child 业务行 ID
     * @param triggerId 本命令 triggerId
     * @param reason 业务原因
     * @param data 审计视图
     * @param actorId 操作者
     * @param now 本次时间
     */
    public void createChild(BusinessChangeRecord root, BusinessChangeRecordRecordType childType, long childId,
        long triggerId, BusinessChangeReason reason, Map<String, Object> data, long actorId, Instant now) {
        BusinessChangeRecord child = record(childType, childId, root.getAggregateRootType(), root.getAggregateRootId(),
            root.getHistoryId(), root.getCurrentVersion(), now);
        recordRepository.insert(child);
        appendVersion(child, idGenerator.nextId(), BusinessChangeOperation.CREATE, BusinessChangeSnapshot.TRIGGER_COMMAND,
            triggerId, reason, data, actorId, now);
    }

    /**
     * 为已有受审计 Child 推进版本指针并追加 UPDATE 或 DELETE 快照；DELETE 须在删除结构化行之前调用
     *
     * @param root 已推进到本次版本的 Root
     * @param childType Child 类型
     * @param childId Child 业务行 ID
     * @param operation UPDATE 或 DELETE
     * @param triggerId 本命令 triggerId
     * @param reason 业务原因
     * @param data 审计视图（DELETE 为删除前的安全字段）
     * @param actorId 操作者
     * @param now 本次时间
     */
    public void changeChild(BusinessChangeRecord root, BusinessChangeRecordRecordType childType, long childId,
        BusinessChangeOperation operation, long triggerId, BusinessChangeReason reason, Map<String, Object> data,
        long actorId, Instant now) {
        changeChild(root, childType, childId, operation, BusinessChangeSnapshot.TRIGGER_COMMAND, triggerId, reason, data,
            actorId, now);
    }

    /**
     * 为已有受审计 Child 推进版本指针并追加快照，显式给出触发类型（须与同命令 Root 快照一致）
     *
     * @param root 已推进到本次版本的 Root
     * @param childType Child 类型
     * @param childId Child 业务行 ID
     * @param operation UPDATE 或 DELETE
     * @param triggerType COMMAND 或 SYSTEM
     * @param triggerId 本命令 triggerId
     * @param reason 业务原因
     * @param data 审计视图
     * @param actorId 操作者
     * @param now 本次时间
     */
    public void changeChild(BusinessChangeRecord root, BusinessChangeRecordRecordType childType, long childId,
        BusinessChangeOperation operation, String triggerType, long triggerId, BusinessChangeReason reason,
        Map<String, Object> data, long actorId, Instant now) {
        BusinessChangeRecord child = recordRepository.findByRecord(childType, Long.toString(childId));
        if (child == null || !root.getHistoryId().equals(child.getParentHistoryId())) {
            throw new ErrorException("受审计 Child 缺少历史身份或父级不一致: " + childType.getCode() + " " + childId);
        }
        recordRepository.advanceChild(child, root.getCurrentVersion(), now);
        appendVersion(child, idGenerator.nextId(), operation, triggerType, triggerId, reason, data, actorId, now);
    }

    /** 构造一条新的历史身份并分配 history_id。 */
    BusinessChangeRecord record(BusinessChangeRecordRecordType recordType, long recordId,
        BusinessChangeRecordAggregateRootType rootType, String rootId, Long parentHistoryId, long version, Instant now) {
        BusinessChangeRecord record = new BusinessChangeRecord();
        record.setHistoryId(idGenerator.nextId());
        record.setRecordType(recordType);
        record.setRecordId(Long.toString(recordId));
        record.setAggregateRootType(rootType);
        record.setAggregateRootId(rootId);
        record.setParentHistoryId(parentHistoryId);
        record.setCurrentVersion(version);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    /** 生成快照并插入；版本号取该记录当前版本。 */
    void appendVersion(BusinessChangeRecord record, long versionId, BusinessChangeOperation operation, String triggerType,
        long triggerId, BusinessChangeReason reason, Map<String, Object> data, long actorId, Instant now) {
        BusinessChangeVersion version = new BusinessChangeVersion();
        version.setChangeVersionId(versionId);
        version.setHistoryId(record.getHistoryId());
        version.setVersionNo(record.getCurrentVersion());
        version.setOperationType(operation);
        version.setSnapshotFormat(BusinessChangeSnapshotFormat.ENTITY_NO);
        version.setCreateBy(actorId);
        version.setCreateTime(now.truncatedTo(ChronoUnit.MICROS));
        String snapshot = BusinessChangeSnapshot.build(record, version, triggerType, triggerId, reason, data);
        if (BusinessChangeSnapshot.byteLength(snapshot) > MAX_SNAPSHOT_BYTES) {
            throw new ErrorException("业务变更快照超过 64 KiB: " + record.getRecordType().getCode() + " " + record.getRecordId());
        }
        version.setSnapshotJson(snapshot);
        versionRepository.insert(version);
    }
}
