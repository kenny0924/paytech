package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税务单据类型（t_tax_document.document_kind）；本期只有 RECEIPT。 */
public enum TaxDocumentKind implements CodeEnum {
    /** 不含买方个人资料的 B2C 简化税票 / 收据 */
    RECEIPT;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
