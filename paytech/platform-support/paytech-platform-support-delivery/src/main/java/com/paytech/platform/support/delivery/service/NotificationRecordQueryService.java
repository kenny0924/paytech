package com.paytech.platform.support.delivery.service;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.delivery.domain.NotificationRequestDetailView;
import com.paytech.platform.support.delivery.domain.NotificationRequestFilter;
import com.paytech.platform.support.delivery.domain.NotificationRequestSummaryView;

/** 运营查询普通通知发送记录；不返回收件邮箱、Vault Token、参数值和供应商流水号。 */
public interface NotificationRecordQueryService {

    /** 按筛选条件、创建时间倒序分页；模板编码不存在时返回空页。 */
    PagingResponseBody<NotificationRequestSummaryView> requests(NotificationRequestFilter filter,
        PagingRequestBody paging);

    /** 请求详情含全部投递与尝试；不存在返回 NOT_FOUND。 */
    NotificationRequestDetailView request(long requestId);
}
