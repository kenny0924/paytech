package com.paytech.platform.support.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 读取或下载入驻文件的内部请求；三个身份已由入口按规范正整数解析。 */
@Getter
@AllArgsConstructor
public class OnboardingFileAccessRequest {

    /**
     * 当前账号 ID
     * <li>来自 Gateway 已验证的 Actor Subject，必须是原上传账号。</li>
     */
    @Positive(message = "账号 ID 必须为正整数")
    private final long accountId;

    /** 当前 CLIENT 会话 ID，每个只读快照内重新核验。 */
    @Positive(message = "会话 ID 必须为正整数")
    private final long sessionId;

    /** 文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;
}
