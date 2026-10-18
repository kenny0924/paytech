package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 新建或修改模板路由的主备账号；新建的路由为启用状态。 */
@Getter
@Setter
public class SaveNotificationRouteRequestBody {

    /**
     * 主账号编码
     * <li>必须是启用的 EMAIL 账号。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String primaryProviderCode;

    /**
     * 备用账号编码
     * <li>可空；不能与主账号相同。认证邮件只用主账号。</li>
     */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "编码只能由大写字母开头，包含大写字母、数字或下划线，最多 64 字符")
    private String fallbackProviderCode;

    /**
     * 当前版本
     * <li>0 表示路由尚不存在、新建；不一致返回 409。</li>
     */
    @PositiveOrZero
    private int expectedVersion;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
