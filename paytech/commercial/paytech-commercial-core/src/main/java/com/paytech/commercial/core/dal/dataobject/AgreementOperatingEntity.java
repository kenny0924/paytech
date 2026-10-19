package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * t_agreement_operating_entity：PARTNER 合同覆盖的 OE。
 * <li>历史 CLIENT 关联只读保留；uk_agreement_entity(agreement_id, entity_no)。</li>
 */
@Getter
@Setter
public class AgreementOperatingEntity {
    /** 覆盖关系标识 */
    private Long agreementOperatingEntityId;

    /** 所属合同 */
    private Long agreementId;

    /** 覆盖的准确 OE */
    private String entityNo;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
