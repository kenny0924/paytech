package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 单据开具方式（t_tax_document.issuance_mode）。 */
public enum IssuanceMode implements CodeEnum {
    /** 本系统分号并生成文件 */
    SYSTEM,

    /** 在税局或自有开票系统外部开具后回填（本期不用） */
    EXTERNAL;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
