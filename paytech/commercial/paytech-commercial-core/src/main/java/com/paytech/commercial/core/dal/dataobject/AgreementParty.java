package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;

import lombok.Getter;
import lombok.Setter;

/**
 * t_agreement_party：合同内一条 Party 角色记录。
 * <li>待签时身份冻结；最终签署结果只允许一次整组原子补齐（以 accepted_time 为空为条件）。</li>
 */
@Getter
@Setter
public class AgreementParty {
    /** Party 角色记录标识 */
    private Long agreementPartyId;

    /** 所属合同 */
    private Long agreementId;

    /** 法律 Party 标识 */
    private Long partyId;

    /** 合同冻结引用的 Party 资料版本 */
    private Long partyProfileId;

    /** Party 在合同中的角色 */
    private AgreementPartyPartyRole partyRole;

    /** 签署方式；未签为空 */
    private AgreementPartyAcceptanceMethod acceptanceMethod;

    /** 代表签约方完成接受的外部 Actor */
    private Long acceptedByActorId;

    /** 接受事实成立的 UTC 时刻；未签为空 */
    private Instant acceptedTime;

    /** 签署证据文件引用 */
    private Long evidenceDocumentId;

    /** 平台生成的全局唯一合同接受事件编号 */
    private String acceptanceReference;

    /** 1 或 NULL：同合同同 Party 最小角色主键行签后为 1，其他为空 */
    private Integer acceptanceRefSlot;

    /** 登记最终签署证据的可信 Actor */
    private Long acceptanceRecordedBy;

    /** 首次登记最终签署结果的 UTC 时间 */
    private Instant acceptanceRecordedAt;

    /** 创建该行的 Actor */
    private Long createBy;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
