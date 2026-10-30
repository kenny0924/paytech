package com.paytech.tax.domain.calculation;

import com.paytech.tax.api.enums.TaxBlockReason;

/**
 * BLOCKED 的一条原因
 *
 * @param code 原因码
 * @param path 所缺字段或所在位置，例如 lines[2].categoryCode；不含个人原值
 */
public record TaxBlock(TaxBlockReason code, String path) {
}
