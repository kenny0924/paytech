package com.paytech.platform.support.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成入驻文件上传的内部请求；三个身份已由入口按规范正整数解析，不接受浏览器自报 Hash 或 Body。 */
@Getter
@AllArgsConstructor
public class CompleteOnboardingFileRequest {

    /**
     * 当前账号 ID
     * <li>来自 Gateway 已验证的 Actor Subject。</li>
     */
    @Positive(message = "账号 ID 必须为正整数")
    private final long accountId;

    /**
     * 当前会话 ID
     * <li>与账号一起在事务内重新核验。</li>
     */
    @Positive(message = "会话 ID 必须为正整数")
    private final long sessionId;

    /**
     * 上传 ID
     * <li>完成操作的业务身份，也是资源锁 Key；重复完成返回同一回执。</li>
     */
    @Positive(message = "上传 ID 必须为正整数")
    private final long uploadId;
}
