package com.paytech.vault.client.admin.request;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工解密请求。
 */
@Getter
@Setter
public class ManualRevealRequestBody {
    /**
     * 待解密的 Token
     * <li>43 位 URL 安全字符；不得写入日志。</li>
     */
    private String token;

    /**
     * 理由码
     */
    private String reasonCode;

    /**
     * 工单号
     */
    private String ticketRef;

    /**
     * 本次路由允许的数据级别
     * <li>由 BFF 按路由填写：普通路由 [STANDARD]，密钥路由 [STANDARD, SECRET]；不接受页面传入。</li>
     */
    private List<String> allowedDataClasses;
}
