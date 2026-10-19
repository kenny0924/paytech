package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** CommercialTermVersion RoundingMode 的数据库闭合值域：金额计算采用的确定性十进制舍入规则。 */
public enum CommercialTermRoundingMode implements CodeEnum {
    /** 四舍五入（半数远离零） */
    HALF_UP("HALF_UP");

    private final String code;

    CommercialTermRoundingMode(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
