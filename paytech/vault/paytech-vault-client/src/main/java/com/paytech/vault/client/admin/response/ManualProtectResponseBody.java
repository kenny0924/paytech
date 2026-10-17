package com.paytech.vault.client.admin.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工加密结果；Token 只向操作人展示一次，不得写入日志。
 */
@Getter
@Setter
public class ManualProtectResponseBody {
    /**
     * 不透明 Token
     * <li>43 位 URL 安全字符。</li>
     */
    private String token;
}
