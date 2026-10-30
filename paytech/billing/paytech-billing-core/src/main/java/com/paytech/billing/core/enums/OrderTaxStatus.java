package com.paytech.billing.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * OrderPaymentSucceeded 载荷 taxStatus 的取值（与 tax TaxCalculationStatus 同码，billing 不依赖 paytech-tax-api）
 * <li>只有 READY 生成 SELLER_TAX 行；NOT_MANAGED 与现状相同不生成税行；已支付订单不应出现 BLOCKED，出现即为上游数据不一致。</li>
 */
public enum OrderTaxStatus implements CodeEnum {
    /** 税额已由 tax 计算完成，按组件生成 SELLER_TAX 行 */
    READY,

    /** 计算被阻断；支付门禁应已拒绝，billing 收到即拒绝 */
    BLOCKED,

    /** 纳税主体不由平台代管，税额为 Client 自报，不生成税行 */
    NOT_MANAGED;

    /** 载荷使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
