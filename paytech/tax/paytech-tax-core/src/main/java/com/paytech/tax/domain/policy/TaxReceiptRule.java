package com.paytech.tax.domain.policy;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 地区包 documents.receipt：简化税票的号码序列、模板与法定文字键。 */
@Getter
@AllArgsConstructor
public class TaxReceiptRule {
    /** 序列前缀，series_code = 前缀 + "-" + 年份 */
    private final String seriesPrefix;

    /** 号码格式：{series} 与 {number:0N} */
    private final String numberFormat;

    /** 模板代码 */
    private final String templateCode;

    /** 模板版本 */
    private final int templateRevision;

    /** 票面标题文字键 */
    private final String titleKey;

    /** 法定文字键 */
    private final List<String> legalTextKeys;

    /**
     * 按格式生成单据号码
     *
     * @param seriesCode 序列代码
     * @param number 序号
     * @return 号码，例如 SA-RCPT-2026-000001
     */
    public String formatNumber(String seriesCode, long number) {
        int start = numberFormat.indexOf("{number:0");
        int end = numberFormat.indexOf('}', start);
        int width = Integer.parseInt(numberFormat.substring(start + "{number:0".length(), end));
        String padded = String.format("%0" + width + "d", number);
        return numberFormat.substring(0, start).replace("{series}", seriesCode) + padded
            + numberFormat.substring(end + 1).replace("{series}", seriesCode);
    }
}
