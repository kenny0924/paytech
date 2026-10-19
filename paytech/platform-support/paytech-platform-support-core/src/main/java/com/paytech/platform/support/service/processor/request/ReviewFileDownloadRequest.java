package com.paytech.platform.support.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营审核下载入驻材料的内部请求；文件标识已由入口按规范正整数解析。 */
@Getter
@AllArgsConstructor
public class ReviewFileDownloadRequest {

    /** 运营账号，来自 Gateway 已验证的 Actor Subject；会话与权限在只读快照内核验。 */
    private final String actorId;

    /** 运营会话。 */
    private final String sessionId;

    /** 文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;
}
