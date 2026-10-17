package com.paytech.platform.support.delivery.service.processor.response;

import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 单次发送的供应商结果分类；ACCEPTED 只表示供应商受理，不表示已送达。 */
@Getter
@RequiredArgsConstructor
public class SendTransientEmailResponse {

    /** 供应商结果分类。 */
    private final EmailProviderResult.Outcome outcome;

    /** 有界结果码，如 SES_ACCEPTED、SES_THROTTLED。 */
    private final String code;
}
