package com.paytech.commerce.test.builder.external;

import com.paytech.client.contract.api.checkout.CheckoutEligibilityModels.PrecheckResponseBody;

import lombok.Setter;
import lombok.experimental.Accessors;

/** CCT 预检响应的合法模板；用例只声明场景差异，构造时保留所有显式负例值。 */
@Setter
@Accessors(fluent = true)
public final class CctPrecheckResponseBuilder {
    private boolean eligible = true;
    private String evaluatedAt = "2026-09-08T00:00:00Z";
    private String reasonCode;

    /** 每次返回独立的预检通过模板，不共享可变构造状态。 */
    public static CctPrecheckResponseBuilder template() {
        return new CctPrecheckResponseBuilder();
    }

    /** 原样构造对方 DTO，不按 reasonCode 修正 eligible 或补回显式 null。 */
    public PrecheckResponseBody build() {
        return new PrecheckResponseBody(eligible, evaluatedAt, reasonCode);
    }
}
