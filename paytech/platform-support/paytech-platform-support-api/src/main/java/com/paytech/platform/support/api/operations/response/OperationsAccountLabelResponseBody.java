package com.paytech.platform.support.api.operations.response;

import lombok.Getter;
import lombok.Setter;

/** 一个 Operations 账号的显示名。 */
@Getter
@Setter
public class OperationsAccountLabelResponseBody {

    /**
     * 账号 ID
     * <li>十进制字符串，避免浏览器整数精度丢失。</li>
     */
    private String accountId;

    /**
     * 显示名
     * <li>登录名；登录名为空时为脱敏邮箱，例如 ke***@gmail.com。不返回完整邮箱。</li>
     */
    private String label;
}
