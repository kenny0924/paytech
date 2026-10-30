package com.paytech.operations.console.bff.service.model.risklimittier;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 档位变更记录页面模型：Risk 字段原样平铺，另加申请人、审批人显示名。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabeledTierChangeBody {
    /** Risk 变更记录，字段平铺到本对象。 */
    @JsonUnwrapped
    private ClientTierChangeResponseBody change;

    /** 申请人显示名；没有申请人或查不到为 null。 */
    private String requestedByLabel;

    /** 审批、驳回或撤回人显示名；没有或查不到为 null。 */
    private String decidedByLabel;
}
