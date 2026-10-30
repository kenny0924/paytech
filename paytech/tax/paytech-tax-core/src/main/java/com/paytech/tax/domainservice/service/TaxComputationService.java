package com.paytech.tax.domainservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.domain.calculation.LineTaxAmounts;

/** 比例税纯计算（technique 9.1 第 6 步）：未税与含税反算、逐行取整与尾差；不访问数据库。 */
public interface TaxComputationService {

    /**
     * 按一个比例税率计算一行
     *
     * @param priceMode 价格口径
     * @param lineAmount 行金额（数量 × 单价 − 折扣；EXCLUSIVE 为净额，INCLUSIVE 为含税金额）
     * @param rate 税率小数；为空表示无适用税率，税额为 0
     * @param roundingMode 取整方法
     * @param scale 取整位数
     * @return 行净额、税额、含税金额、尾差与算法
     */
    LineTaxAmounts computeLine(TaxPriceMode priceMode, BigDecimal lineAmount, BigDecimal rate, RoundingMode roundingMode,
        int scale);
}
