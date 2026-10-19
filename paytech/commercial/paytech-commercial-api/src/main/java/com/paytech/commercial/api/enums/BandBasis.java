package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 阶梯组件的档位匹配基数（t_commercial_term_component.band_basis）；仅 TIERED 组件非空。 */
public enum BandBasis implements CodeEnum {
    /** 按单笔交易金额匹配档位 */
    TRANSACTION_AMOUNT("TRANSACTION_AMOUNT"),

    /** 按周期累计交易量匹配档位；本期拒绝激活 */
    PERIOD_VOLUME("PERIOD_VOLUME");

    private final String code;

    BandBasis(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
