package com.paytech.channel.connector.api.externalapi;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.channel.connector.api.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.channel.connector.api.externalapi.response.FlowEditorContextResponseBody;

/**
 * 外部 API 配置编辑器目录契约（E29、E30）；只由 Operations BFF 经私网调用。
 * <li>2026-10-20 自 Platform 迁入 channel-connector，路径、JSON 字段与状态码不变。</li>
 * <li>写命令必带 {@value com.paytech.channel.connector.api.ChannelConnectorHeaders#ACTOR_HEADER}（规范正十进制 Account ID），查询与静态校验不带；权限由 Gateway Route 与 BFF 实时授权负责。</li>
 */
@HttpExchange("/external-api-admin/v1")
public interface ExternalApiEditorApi {

    /**
     * 一次取得流程编辑器需要的标准 API 字段与本接入方全部原子 API 合同。
     *
     * @param integrationCode
     *            接入方编码，不存在时返回 404
     * @param standardApiCode
     *            被实现的标准 API 编码，不存在时返回 404
     * @return 编辑器上下文
     */
    @GetExchange("/integrations/{integrationCode}/flow-editor-context")
    FlowEditorContextResponseBody getFlowEditorContext(@PathVariable("integrationCode") String integrationCode,
        @RequestParam("standardApiCode") String standardApiCode);

    /**
     * 读取编辑器可选枚举、容量上限与注册函数签名。
     *
     * @return 能力目录
     */
    @GetExchange("/editor-capabilities")
    EditorCapabilitiesResponseBody getEditorCapabilities();
}
