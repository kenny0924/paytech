package com.paytech.client.master.core.dal.dataobject;
import java.time.Instant;
import com.paytech.client.master.domain.enums.*;
/** CCT 内部申请/主体绑定；不跨模块返回数据库对象。 */
public record ClientDraftIdentity(
    /** 稳定申请标识。 */ long applicationId,
    /** 稳定申请编号。 */ String applicationNo,
    /** 原申请账号。 */ long accountId,
    /** 申请当前状态。 */ ClientApplicationStatus status,
    /** 未完成申请占位，终态为空。 */ Integer activeSlot,
    /** 稳定客户标识。 */ long clientId,
    /** 稳定客户编号。 */ String clientNo,
    /** 客户主体标识。 */ long partyId,
    /** 个人或组织。 */ ClientType clientType,
    /** 当前单份资料标识。 */ Long profileId,
    /** 草稿编辑所属申请。 */ Long draftApplicationId,
    /** 批准后冻结时间。 */ Instant approvedTime,
    /** 当前业务资料标识。 */ Long businessProfileId,
    /** 原申请建立时间。 */ Instant createdAt,
    /** 最近资料保存或状态变化时间。 */ Instant savedAt,
    /** 申请适用的辖区代码。 */ String jurisdictionCode,
    /** 组织类型代码；个人且无个人经营形态时为空。 */ String organizationTypeCode,
    /** 申请冻结的采集 Schema 版本。 */ Long schemaId,
    /** 责任 PayFac；平台自营为空。 */ Long responsiblePartnerId,
    /** 当前审核任务；未登记或首次提交前为空。 */ Long dueDiligenceCaseId,
    /** 退回补件截止时间，可空。 */ Instant reviewDueTime) {}
