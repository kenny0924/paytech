package com.paytech.platform.support.service.processor.webhook.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 Client ID 或名称搜索 Client。 */
@Getter
@AllArgsConstructor
public class ListWebhookClientsRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 搜索关键字，可空。 */
    private final String keyword;

    /** 已校验的分页。 */
    private final PagingRequestBody paging;
}
