package com.paytech.platform.support.delivery.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;

import com.paytech.foundation.common.error.InvalidParameterException;

/** 端点地址的语法规则；地址是否指向公网由发送侧解析 DNS 后判断。 */
public final class WebhookEndpointUrl {

    /** 地址最大长度。 */
    public static final int MAX_LENGTH = 1000;

    /** 只承载静态方法。 */
    private WebhookEndpointUrl() {
    }

    /**
     * 校验并解析端点地址
     *
     * @param url 请求地址
     * @param allowedSchemes 部署允许的协议；生产只允许 https
     * @return 解析后的地址
     */
    public static URI parse(String url, Collection<String> allowedSchemes) {
        if (url == null || url.isBlank() || url.length() > MAX_LENGTH || !url.equals(url.strip())) {
            throw new InvalidParameterException("url 不能为空、不能有首尾空白且不超过 1000 字符");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException invalid) {
            throw new InvalidParameterException("url 不是合法地址");
        }
        if (!uri.isAbsolute() || uri.getScheme() == null || !allowedSchemes.contains(uri.getScheme())) {
            throw new InvalidParameterException("url 必须是 https 地址");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new InvalidParameterException("url 必须有主机名，且不能包含用户信息或片段");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost")) {
            throw new InvalidParameterException("url 主机名不能是 localhost");
        }
        return uri;
    }
}
