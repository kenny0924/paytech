package com.paytech.operations.console.bff.service.model.onboardingcode.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 某 PayFac 的入驻码（掩码），可按状态过滤。 */
@Getter
@AllArgsConstructor
public class OnboardingCodeListResponseBody {

    /** 入驻码，顺序与 client-master 一致 */
    private final List<OnboardingCodeResponseBody> items;
}
