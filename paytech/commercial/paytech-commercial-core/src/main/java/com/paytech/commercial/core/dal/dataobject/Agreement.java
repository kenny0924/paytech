package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.domain.enums.AgreementAgreementFormat;
import com.paytech.commercial.domain.enums.AgreementAgreementType;
import com.paytech.commercial.domain.enums.AgreementStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_agreement：一份独立法律合同（CLIENT 或 PARTNER）的完整行。
 * <li>不使用 Root/Version；状态推进以当前状态为条件更新。Checkout 只读快照另用 CheckoutRecords.Agreement。</li>
 */
@Getter
@Setter
public class Agreement {
    /** 合同内部唯一标识 */
    private Long agreementId;

    /** 可在业务操作中识别合同的唯一编号，uk_agreement_no */
    private String agreementNo;

    /** 合同展示名称 */
    private String agreementName;

    /** 合同类型：CLIENT 或 PARTNER */
    private AgreementAgreementType agreementType;

    /** CLIENT 合同必填的业务模式，PARTNER 为空；创建后不可修改 */
    private BusinessMode businessMode;

    /** CLIENT 合同必填的资金模式，PARTNER 为空；本期只接受 SETTLE_THROUGH_PLATFORM */
    private FundMode fundMode;

    /** CLIENT 合同必填的定价权归属，PARTNER 为空；本期只接受 PLATFORM */
    private PricingAuthority pricingAuthority;

    /** 推荐伙伴（client-master Local Partner 值引用）；pricing_authority=PARTNER 时必填 */
    private Long referralPartnerId;

    /** CLIENT 合同的 Client 标识；PARTNER 为空 */
    private Long clientId;

    /** PARTNER 合同的 Local Partner 标识；CLIENT 为空 */
    private Long localPartnerId;

    /** CLIENT 合同准确绑定的 OE；PARTNER 为空，覆盖 OE 见 t_agreement_operating_entity */
    private String entityNo;

    /** 新同 Party 共签或旧逐角色签署；创建后不可修改 */
    private AgreementAgreementFormat agreementFormat;

    /** 法律生命周期状态 */
    private AgreementStatus status;

    /** 生效区间 UTC 起点（含） */
    private Instant effectiveFrom;

    /** 生效区间 UTC 终点（不含）；空为未预定结束 */
    private Instant effectiveTo;

    /** 完成必要签署的 UTC 时刻 */
    private Instant signedTime;

    /** 进入 ACTIVE 的 UTC 时刻 */
    private Instant activatedTime;

    /** 创建该行的 Human 或 System Actor */
    private Long createBy;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
