package com.paytech.operations.console.bff.service.model.riskunderwriting;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.paytech.risk.decision.api.control.response.UnderwritingSummaryBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 承保列表行页面模型：Risk 列表字段原样平铺，另加提交人、决定人显示名。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabeledUnderwritingSummaryBody {
    /** Risk 列表行，字段平铺到本对象。 */
    @JsonUnwrapped
    private UnderwritingSummaryBody summary;

    /** 提交人显示名；没有提交人或查不到为 null。 */
    private String submittedByLabel;

    /** 决定人显示名；没有决定人或查不到为 null。 */
    private String decidedByLabel;
}
