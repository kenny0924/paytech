package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 国家证据类型（evidence[].type / t_tax_evidence.evidence_type）；本期只有两类。 */
public enum TaxEvidenceType implements CodeEnum {
    /** Client 建单时声明的计税国家（PROVISIONAL 的供应地依据） */
    DECLARED_COUNTRY,

    /** 消费者提交的账单地址国家（FINAL 的供应地依据） */
    BILLING_COUNTRY;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
