package com.paytech.operations.console.bff.configuration;

import org.springframework.beans.factory.ObjectProvider;

import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;

/**
 * 出站内部调用 Token（D26）的统一接线：本 BFF 每个 {@code HttpServiceClientFactory.create} 客户端都经此附加目标服务的 Token。
 * <li>{@code internal-auth.mode=OFF}（默认）时 Foundation 不创建 {@link InternalTokenSupplier}，选项原样返回，出站行为不变。</li>
 * <li>mode 非 OFF 且存在 Token 来源时，按显式目标服务发现名注入 {@code Authorization: Bearer}；取不到 Token 时请求不发出。</li>
 *
 * @author Claude
 */
final class InternalCallTokenOptions {

    private InternalCallTokenOptions() {
    }

    /**
     * 按目标服务名为客户端选项附加内部调用 Token。
     *
     * @param options 客户端原有选项
     * @param tokens Token 来源；未启用时为空
     * @param targetService 目标服务发现名（Token {@code aud}）
     * @return 附加 Token 后的选项，未启用时为原选项
     */
    static HttpServiceClientOptions apply(HttpServiceClientOptions options, ObjectProvider<InternalTokenSupplier> tokens,
        String targetService) {
        InternalTokenSupplier supplier = tokens.getIfAvailable();
        if (supplier == null) {
            return options;
        }
        return options.withInternalToken(supplier, targetService);
    }
}
