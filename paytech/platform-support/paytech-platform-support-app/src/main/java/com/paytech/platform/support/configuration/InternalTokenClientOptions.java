package com.paytech.platform.support.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.core.autoconfigure.InternalAuthProperties;

import lombok.AllArgsConstructor;

/**
 * Platform 出站 HTTP 客户端的内部 Token 选项（D26）：{@code internal-auth.mode=OFF} 时原样返回，不附加 Token。
 * <li>mode 非 OFF 时必须有出站 Token 来源（签发端启用后由 Foundation 创建），否则拒绝启动，不静默发送无 Token 请求。</li>
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
            throw new IllegalStateException("internal-auth.mode=" + properties.getMode() + " 时 Platform 出站需要内部 Token："
                + "请启用 internal-token.issuer.enabled 并为 " + targetService + " 登记白名单");
        }
        return options.withInternalToken(supplier, targetService);
    }
}
