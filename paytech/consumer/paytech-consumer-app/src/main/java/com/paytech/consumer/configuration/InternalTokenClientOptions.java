package com.paytech.consumer.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.core.autoconfigure.InternalAuthProperties;

import lombok.AllArgsConstructor;

/**
 * 出站 HTTP 客户端的内部 Token 选项（D26）：{@code internal-auth.mode=OFF} 时原样返回，不附加 Token。
 * <li>mode 非 OFF 时必须有出站 Token 来源，否则拒绝启动，不静默发送无 Token 请求。</li>
 */
@Component
@AllArgsConstructor
public class InternalTokenClientOptions {
    private final ObjectProvider<InternalTokenSupplier> suppliers;
    private final ObjectProvider<InternalAuthProperties> internalAuth;

    /**
     * 按当前模式为目标服务追加出站 Token。
     *
     * @param options 客户端原有选项
     * @param targetService 目标服务发现名（Token {@code aud}）
     * @return mode=OFF 时原选项，否则附加 Token 的新选项
     */
    public HttpServiceClientOptions apply(HttpServiceClientOptions options, String targetService) {
        InternalAuthProperties properties = internalAuth.getIfAvailable();
        if (properties == null) {
            return options;
        }
        InternalTokenSupplier supplier = suppliers.getIfAvailable();
        if (supplier == null) {
            throw new IllegalStateException("internal-auth.mode=" + properties.getMode() + " 时出站需要内部 Token："
                + "请配置 internal-auth 的 Token 来源并在 Platform 签发白名单登记 " + targetService);
        }
        return options.withInternalToken(supplier, targetService);
    }
}
