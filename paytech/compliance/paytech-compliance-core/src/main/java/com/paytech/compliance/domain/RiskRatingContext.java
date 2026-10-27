package com.paytech.compliance.domain;

import java.util.List;

import com.paytech.compliance.core.dal.dataobject.RiskFactor;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次手工客户风险评级：评级、说明与替换后的全部审核员因子。 */
@Getter
@AllArgsConstructor
public class RiskRatingContext {
    /** 命令公共事实。 */
    private final ReviewCommandContext command;
    /** LOW、MEDIUM 或 HIGH。 */
    private final String riskRating;
    /** 评级说明。 */
    private final String note;
    /** 新因子。 */
    private final List<RiskFactor> factors;
}
