package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税务计算结果状态（t_tax_calculation.status）；BLOCKED 是正常业务结果（HTTP 200），不是错误。 */
public enum TaxCalculationStatus implements CodeEnum {
    /** 已按规则包算出税额；FINAL 阶段且未过期时可供支付 */
    READY,

    /** 主体是平台登记的纳税主体，但地区未启用、规则缺失或冲突、证据缺失；订单不可支付，原因见 blockReasons */
    BLOCKED,

    /** 税务责任方不是平台登记的纳税主体；税额取源 Owner 自报值，不阻断支付，tax 不建正式事实 */
    NOT_MANAGED;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
