package com.paytech.platform.support.test.notification;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.delivery.integration.provider.EmailProvider;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;

import lombok.RequiredArgsConstructor;

/** 第二个合成账号用于验证明确失败后的主备选择，共用当前用例的显式响应配置。 */
@TestComponent
@RequiredArgsConstructor
public class FallbackEmailProvider implements EmailProvider {

    private final MockEmailProvider responses;

    /** 独立账号编码，不作为生产默认配置。 */
    @Override
    public String providerCode() {
        return "NOTIFICATION_TEST_FALLBACK";
    }

    /** 故意使用不同请求号规则，证明恢复身份由 Provider 生成。 */
    @Override
    public String requestNo(long attemptId) {
        return "fallback_" + attemptId;
    }

    /** 只消费当前用例为此收件人预置的下一条响应。 */
    @Override
    public EmailProviderResult send(EmailProviderRequest request) {
        return responses.send(request);
    }

    /** 查询继续使用本账号原请求号。 */
    @Override
    public EmailProviderResult query(String requestNo, String messageId) {
        return responses.query(requestNo, messageId);
    }
}
