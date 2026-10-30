package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 行价格口径（请求 priceMode / t_tax_calculation.price_mode）。 */
public enum TaxPriceMode implements CodeEnum {
    /** 未税价：tax = round(lineAmount × rate)，gross = lineAmount + tax */
    EXCLUSIVE,

    /** 含税价：tax = round(lineAmount × rate / (1 + rate))，net = lineAmount − tax */
    INCLUSIVE;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
