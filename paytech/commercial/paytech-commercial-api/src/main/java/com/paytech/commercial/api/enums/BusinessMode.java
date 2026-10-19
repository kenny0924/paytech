package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 合同业务模式（t_agreement.business_mode）；CLIENT 合同必填、PARTNER 合同为空，创建后不可修改。 */
public enum BusinessMode implements CodeEnum {
    /** 商户记录方（Merchant of Record）：OE 作为卖方并承担税务责任 */
    MOR("MOR"),

    /** 支付服务商：Client 作为卖方，平台经 OE 提供收单与资金服务 */
    PSP("PSP");

    private final String code;

    BusinessMode(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
