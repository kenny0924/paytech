package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import com.paytech.commercial.api.enums.ProductCode;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * t_agreement_product：合同订阅的可选产品模块。
 * <li>产品 business_mode_scope 必须包含合同 business_mode；基座产品不写行；应用校验，不建 CHECK/FK。</li>
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AgreementProduct {
    /** 合同订阅产品行的内部唯一标识 */
    private Long agreementProductId;

    /** 所属合同 */
    private Long agreementId;

    /**
     * 订阅的产品代码
     * <li>与 agreementId 组成 uk_agreement_product。</li>
     */
    private ProductCode productCode;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
