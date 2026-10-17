package com.paytech.platform.support.api.notificationmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 启用或停用模板、路由或发送账号。 */
@Getter
@Setter
public class ChangeNotificationStatusRequestBody {

    /**
     * 目标状态
     * <li>ACTIVE 或 DISABLED；与当前相同返回 400。</li>
     */
    @NotBlank
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能为 ACTIVE 或 DISABLED")
    private String status;

    /**
     * 调用方看到的当前版本
     * <li>不一致返回 409。</li>
     */
    @Positive
    private int expectedVersion;

    /**
     * 操作原因
     * <li>去除首尾空白后 1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
