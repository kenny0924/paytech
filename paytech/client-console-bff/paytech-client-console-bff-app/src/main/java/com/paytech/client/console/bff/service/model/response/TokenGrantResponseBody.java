package com.paytech.client.console.bff.service.model.response;

import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 浏览器认证投影：只含 Access Token，有意不包含 Refresh Secret。 */
@Getter
@AllArgsConstructor
public class TokenGrantResponseBody {

    /** 当前账号上下文 */
    private final AccountContextResponseBody context;

    /**
     * Access Token
     * <li>页面只在内存或当前标签页保存。</li>
     */
    private final String accessToken;

    /** Access Token 到期时间，UTC 六位微秒字符串 */
    private final String expiresAt;

    /** 凭据不打印到日志。 */
    @Override
    public String toString() {
        return "TokenGrantResponseBody[REDACTED]";
    }
}
