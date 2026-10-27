package com.paytech.compliance.domain;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 SCREENING_SEARCH 的规范化结果。 */
@Getter
@AllArgsConstructor
public class ScreeningSearchResult {
    /** 执行成功，命中可用。 */
    public static final String SUCCESS = "SUCCESS";

    /** 提供方明确业务失败。 */
    public static final String FAIL = "FAIL";

    /** 结果未知（ERROR、超时或连接失败）。 */
    public static final String UNKNOWN = "UNKNOWN";

    /** SUCCESS、FAIL 或 UNKNOWN。 */
    private final String outcome;
    /** 提供方执行引用；可空。 */
    private final String runRef;
    /** 命中；非成功为空列表。 */
    private final List<Match> hits;

    /** 一条命中；不含实体姓名。 */
    @Getter
    @AllArgsConstructor
    public static class Match {
        /** 提供方命中引用。 */
        private final String externalRef;
        /** 实体类型。 */
        private final String entityType;
        /** 名单类型。 */
        private final List<String> listTypes;
        /** 匹配分数 0—100。 */
        private final BigDecimal score;
        /** 首个匹配方式。 */
        private final String matchType;
        /** 风险等级。 */
        private final String riskLevel;
    }
}
