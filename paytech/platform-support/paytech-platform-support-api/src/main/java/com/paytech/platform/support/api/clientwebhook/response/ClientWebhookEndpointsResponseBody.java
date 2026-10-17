package com.paytech.platform.support.api.clientwebhook.response;

import java.util.List;

import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 本工作区端点列表；端点字段沿用运营查询投影（不含密钥值）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientWebhookEndpointsResponseBody {

    /** 按创建时间排序的端点。 */
    private List<WebhookEndpointResponseBody> items;
}
