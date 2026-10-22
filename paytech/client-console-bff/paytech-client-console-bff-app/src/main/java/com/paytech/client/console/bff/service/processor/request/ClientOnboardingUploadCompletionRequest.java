package com.paytech.client.console.bff.service.processor.request;

import com.paytech.client.console.bff.service.model.ClientActor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成上传的内部请求。 */
@Getter
@AllArgsConstructor
public class ClientOnboardingUploadCompletionRequest {

    /** 入站边界已解析的可信身份 */
    @NotNull(message = "可信身份不能为空")
    private final ClientActor actor;

    /**
     * 上传 ID
     * <li>入站拦截器已按规范正整数校验。</li>
     */
    @NotBlank(message = "上传 ID 不能为空")
    private final String uploadId;
}
