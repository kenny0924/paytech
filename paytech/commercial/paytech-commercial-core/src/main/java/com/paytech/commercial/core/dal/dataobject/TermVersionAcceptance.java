package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.domain.enums.AgreementPartyAcceptanceMethod;

import lombok.Getter;
import lombok.Setter;

/**
 * t_term_version_acceptance：价格版本的客户签署记录（价格附件 + 签署证据）。
 * <li>uk(term_version_id) 一期一个版本一次签署；uk(acceptance_reference) 为业务唯一身份；登记后不可修改。</li>
 */
@Getter
@Setter
public class TermVersionAcceptance {
    /** 签署记录标识 */
    private Long versionAcceptanceId;

    /** 签署的价格版本 */
    private Long termVersionId;

    /** 签署 Party：Client 方案为合同 CLIENT 角色，Partner 方案为合同 LOCAL_PARTNER 角色 */
    private Long partyId;

    /** 签署方式，同合同签署 */
    private AgreementPartyAcceptanceMethod acceptanceMethod;

    /** 签署成立的 UTC 微秒时间 */
    private Instant acceptedAt;

    /** 全局唯一的签署编号 */
    private String acceptanceReference;

    /** 已签署价格附件文件 ID */
    private Long fileId;

    /** 价格附件 SHA-256 小写十六进制 */
    private String contentHash;

    /** 登记人 Operations Account ID */
    private String recordedBy;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
