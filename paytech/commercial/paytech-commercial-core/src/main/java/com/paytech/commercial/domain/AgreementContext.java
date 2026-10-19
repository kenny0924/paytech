package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.Agreement;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementDocumentRef;
import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.domain.enums.AgreementStatus;
import com.paytech.foundation.compensation.api.CompensationEventRequest;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同聚合一次写入命令的上下文：贯穿合同、签署、订阅与开通项写入 Processor 与 AgreementService（technique.md §2.2）
 * <li>Processor 在事务前完成归属、状态与业务规则校验后一次构造；Service 只按其中已准备的行与目标值保存，不重复查询已确认的关系。</li>
 * <li>root 为合同 BCR Root（创建时由 Service 插入后回填）；Repository 在 SQL 成功后直接同步其中的对象。事务回滚后整体丢弃。</li>
 * <li>只持有本模块 DataObject 与目标值，不保存 HTTP 对象、Bean 或回调；未参与本次命令的字段保持空列表或 null。</li>
 */
@Getter
@Setter
public class AgreementContext {
    /** 合同；创建时为待插入的新行 */
    private final Agreement agreement;

    /** 操作者 Account ID */
    private final long actorId;

    /** 本次命令的业务时刻（UTC 微秒） */
    private final Instant now;

    /** 外部命令携带的期望 Root 版本；创建为空 */
    private final Long expectedChangeVersion;

    /** 合同 BCR Root；创建时由 Service 插入后回填 */
    private BusinessChangeRecord root;

    /**
     * 参与方角色行
     * <li>创建：待插入的全部角色；签署登记：本次接受的同一 Party 的全部待签角色（按主键升序）。</li>
     */
    private List<AgreementParty> parties = List.of();

    /** 本次新增的合同文件引用（创建时的签署文件 / 附件、签署证据的执行文件、终止通知） */
    private List<AgreementDocumentRef> newDocuments = List.of();

    /** 创建 PARTNER 合同时待插入的覆盖 OE */
    private List<AgreementOperatingEntity> operatingEntities = List.of();

    /** 订阅替换：新增的订阅行 */
    private List<AgreementProduct> addedProducts = List.of();

    /** 订阅替换：将删除的已有订阅行 */
    private List<AgreementProduct> removedProducts = List.of();

    /** 开通项：新增时为待插入的新行，修改时为已读取的行 */
    private AgreementCapability capability;

    /** 开通项修改的目标值载体（status、生效区间与保证金）；新增时为空 */
    private AgreementCapability capabilityChange;

    /** 开通项本次保存后的完整约束行（修改时整体替换） */
    private List<AgreementCapabilityLimit> capabilityLimits = List.of();

    /**
     * 签署登记的签署字段载体
     * <li>只读取 acceptanceMethod、acceptedByActorId、acceptedTime、evidenceDocumentId、acceptanceReference、acceptanceRecordedBy、
     * acceptanceRecordedAt；acceptanceRefSlot 由 Service 按组内最小主键行设置。</li>
     */
    private AgreementParty acceptance;

    /** 生命周期推进的目标状态；不推进为空 */
    private AgreementStatus nextStatus;

    /** 生命周期推进后的生效终点（不变时为原值） */
    private Instant nextEffectiveTo;

    /** 生命周期推进后的签署时刻（不变时为原值） */
    private Instant nextSignedTime;

    /** 生命周期推进后的激活时刻（不变时为原值） */
    private Instant nextActivatedTime;

    /** 提交后发布 AgreementStatusChanged 的补偿登记请求；只在激活与终止时非空 */
    private CompensationEventRequest statusChangedEvent;

    /**
     * 构造一次写入命令的上下文
     *
     * @param agreement 合同
     * @param root 合同 BCR Root，创建时为空
     * @param actorId 操作者
     * @param now 业务时刻
     * @param expectedChangeVersion 期望版本，创建时为空
     */
    public AgreementContext(Agreement agreement, BusinessChangeRecord root, long actorId, Instant now,
        Long expectedChangeVersion) {
        this.agreement = agreement;
        this.root = root;
        this.actorId = actorId;
        this.now = now;
        this.expectedChangeVersion = expectedChangeVersion;
    }

    /** 本次命令以当前状态推进生命周期，其余时间保持原值。 */
    public void moveTo(AgreementStatus status) {
        this.nextStatus = status;
        this.nextEffectiveTo = agreement.getEffectiveTo();
        this.nextSignedTime = agreement.getSignedTime();
        this.nextActivatedTime = agreement.getActivatedTime();
    }
}
