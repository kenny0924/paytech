package com.paytech.client.console.bff.service.processor.request;

import com.paytech.client.console.bff.service.model.ClientActor;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 受控表单目录（C03）的内部请求。 */
@Getter
@AllArgsConstructor
public class ClientFormOptionsRequest {

    /** 入站边界已解析的可信身份 */
    @NotNull(message = "可信身份不能为空")
    private final ClientActor actor;

    /**
     * 客户类型
     * <li>INDIVIDUAL 或 ORGANIZATION；在会话回读之后校验，保持原错误优先级。</li>
     */
    @NotNull(message = "客户类型不能为空")
    private final String clientType;
}
