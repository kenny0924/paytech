package com.paytech.operations.console.bff.service.model.onboardingcode.response;

import lombok.Getter;

/** 签发结果：入驻码（掩码）+ 唯一一次出现的明文码。 */
@Getter
public class IssuedOnboardingCodeResponseBody {

    /** 新入驻码 */
    private final OnboardingCodeResponseBody issued;

    /** 明文入驻码 XXXX-XXXX；只在本响应出现，不得记录 */
    private final String code;

    /**
     * 签发结果
     *
     * @param issued 掩码后的入驻码
     * @param code 明文入驻码
     */
    public IssuedOnboardingCodeResponseBody(OnboardingCodeResponseBody issued, String code) {
        this.issued = issued;
        this.code = code;
    }

    /** 明文码不进入日志。 */
    @Override
    public String toString() {
        return "IssuedOnboardingCodeResponseBody[REDACTED]";
    }
}
