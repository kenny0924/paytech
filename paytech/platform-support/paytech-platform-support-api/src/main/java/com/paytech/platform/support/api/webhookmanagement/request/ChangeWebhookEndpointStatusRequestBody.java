package com.paytech.platform.support.api.webhookmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 启用或停用 Webhook 端点。 */
@Getter
@Setter
public class ChangeWebhookEndpointStatusRequestBody {

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
     * <li>1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
