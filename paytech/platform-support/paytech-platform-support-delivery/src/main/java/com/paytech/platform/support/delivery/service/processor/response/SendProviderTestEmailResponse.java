package com.paytech.platform.support.delivery.service.processor.response;

import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 测试发送的服务商结果；ACCEPTED 只表示服务商受理，不表示已送达。 */
@Getter
@RequiredArgsConstructor
public class SendProviderTestEmailResponse {

    /** 已规范化的收件邮箱，仅供调用方记录摘要。 */
    private final String recipient;

    /** 服务商结果分类。 */
    private final EmailProviderResult.Outcome outcome;

    /** 有界结果码，如 SES_ACCEPTED、SES_CREDENTIAL_REJECTED。 */
    private final String code;

    /** 不输出收件邮箱。 */
    @Override
    public String toString() {
        return "SendProviderTestEmailResponse(outcome=" + outcome + ", code=" + code + ")";
    }
}
