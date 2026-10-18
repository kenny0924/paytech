package com.paytech.card.api.material.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 凭票据读卡请求 */
@Getter
@Setter
public class RevealChannelCardMaterialRequestBody {
    /** Card 签发的一次性票据 */
    @NotBlank(message = "读卡票据必填")
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "读卡票据格式无效")
    private String ticket;

    @Override
    public String toString() {
        return "RevealChannelCardMaterialRequestBody[REDACTED]";
    }
}
