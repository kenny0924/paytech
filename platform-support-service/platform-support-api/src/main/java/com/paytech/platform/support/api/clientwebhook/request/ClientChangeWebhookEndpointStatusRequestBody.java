package com.paytech.platform.support.api.clientwebhook.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户启用或停用端点。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientChangeWebhookEndpointStatusRequestBody {

    /** ACTIVE 或 DISABLED；与当前相同返回 400。 */
    @NotBlank
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能为 ACTIVE 或 DISABLED")
    private String status;

    /** 当前版本；不一致返回 409。 */
    @Positive
    private int expectedVersion;
}
