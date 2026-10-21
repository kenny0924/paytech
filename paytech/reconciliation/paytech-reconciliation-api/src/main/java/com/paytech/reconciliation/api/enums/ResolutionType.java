package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 异常处置方式（t_recon_resolution.resolution_type）
 */
public enum ResolutionType implements CodeEnum {
    /** 重新匹配：交易层在账单行上写平台流水，银行层追加一条已确认银行匹配 */
    RE_MATCH,

    /** 接受时间差：不动金额，只解除阻塞或结案；用于 COST_DEVIATION、DUPLICATE_SETTLEMENT、PLATFORM_ITEM_UNSETTLED 等 */
    ACCEPT_TIMING_DIFFERENCE,

    /** 归类：带归类码与等于异常差异的带符号金额，按来源对象经 billing 入账 */
    CLASSIFY;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
