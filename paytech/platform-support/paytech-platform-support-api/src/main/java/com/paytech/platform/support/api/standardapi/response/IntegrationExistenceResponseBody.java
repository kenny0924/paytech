package com.paytech.platform.support.api.standardapi.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 接入方存在性查询结果；不返回接入方任何配置内容。 */
public class IntegrationExistenceResponseBody {

    /** 查询的接入方编码，原样回显。 */
    private final String integrationCode;

    /**
     * 接入方是否存在
     * <li>true 表示 Platform 外部 API 配置中登记了该编码的接入方（不区分其启停状态）。</li>
     */
    private final boolean exists;

    /** 按 JSON 属性绑定。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public IntegrationExistenceResponseBody(@JsonProperty("integrationCode") String integrationCode,
        @JsonProperty("exists") boolean exists) {
        this.integrationCode = integrationCode;
        this.exists = exists;
    }

    /** 接入方编码 */
    public String getIntegrationCode() {
        return integrationCode;
    }

    /** 是否存在 */
    public boolean isExists() {
        return exists;
    }
}
