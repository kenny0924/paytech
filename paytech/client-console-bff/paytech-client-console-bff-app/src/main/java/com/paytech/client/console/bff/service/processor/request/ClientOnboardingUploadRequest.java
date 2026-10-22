package com.paytech.client.console.bff.service.processor.request;

import com.paytech.client.console.bff.service.model.ClientActor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 开始上传入驻材料的内部请求；字段格式已由入口 Body 校验。 */
@Getter
@AllArgsConstructor
public class ClientOnboardingUploadRequest {

    /** 入站边界已解析的可信身份 */
    @NotNull(message = "可信身份不能为空")
    private final ClientActor actor;

    /** 展示文件名 */
    @NotBlank(message = "文件名不能为空")
    private final String filename;

    /** 声明的规范 MIME */
    @NotBlank(message = "文件类型不能为空")
    private final String mimeType;

    /** 声明字节数，十进制字符串 */
    @NotBlank(message = "文件大小不能为空")
    private final String sizeBytes;

    /** 不记录可能包含个人信息的文件名。 */
    @Override
    public String toString() {
        return "ClientOnboardingUploadRequest[REDACTED]";
    }
}
