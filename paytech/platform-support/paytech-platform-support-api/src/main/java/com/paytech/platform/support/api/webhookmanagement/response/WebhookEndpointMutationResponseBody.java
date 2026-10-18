package com.paytech.platform.support.api.webhookmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 端点修改结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEndpointMutationResponseBody {

    /** 端点 ID，字符串形式。 */
    private String endpointId;

    /** 写入后的版本。 */
    private int version;
}
