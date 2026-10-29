package com.paytech.operations.console.bff.service.model.riskunderwriting;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 承保版本页面模型：Risk 版本字段原样平铺，另加创建人、提交人、决定人显示名。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabeledUnderwritingResponseBody {
    /** Risk 承保版本，字段平铺到本对象。 */
    @JsonUnwrapped
    private UnderwritingResponseBody underwriting;

    /** 创建人显示名；查不到为 null，页面回退展示 createdBy。 */
    private String createdByLabel;

    /** 提交人显示名；没有提交人或查不到为 null。 */
    private String submittedByLabel;

    /** 决定人显示名；没有决定人或查不到为 null。 */
    private String decidedByLabel;
}
