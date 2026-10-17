package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;
import com.paytech.client.master.domain.enums.BusinessChangeOperation;
import com.paytech.client.master.domain.enums.BusinessChangeSnapshotFormat;

/** 仅查询 CLIENT Root 的不可变审计来源，并核对当前不可变身份关联。 */
public record ClientProjectionHistory(
    /** 指定补偿事件引用的不可变历史行。 */ long changeVersionId,
    /** CLIENT Root 的稳定历史身份。 */ long historyId,
    /** Root 对应的 Client ID 字符串。 */ String recordId,
    /** 原领域历史位置，不能从当前表单重算。 */ long sourceVersion,
    /** 已由查询限定为 CREATE/UPDATE 的历史动作。 */ BusinessChangeOperation operation,
    /** 私有历史格式判别，用于拒绝尚未迁移的旧格式。 */ BusinessChangeSnapshotFormat snapshotFormat,
    /** 最大 64 KiB 的安全白名单审计 JSON。 */ String snapshotJson,
    /** 本次历史动作的实际操作者。 */ long actorId,
    /** 原历史发生时间，UTC 微秒。 */ Instant occurredAt,
    /** 当前 Client 的不可变内部身份。 */ long clientId,
    /** 当前 Client 固定绑定的法律主体。 */ long partyId,
    /** Client 创建后不改变的业务编号。 */ String clientNo,
    /** Client 第一版 CREATE 的实际发起账号。 */ long applicantId,
    /** 原账号确实拥有该 Client 申请的本库证据数量。 */ int applicationCount) {}
