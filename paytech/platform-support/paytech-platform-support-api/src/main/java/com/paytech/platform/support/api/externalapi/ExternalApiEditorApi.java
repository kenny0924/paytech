package com.paytech.platform.support.api.externalapi;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.platform.support.api.externalapi.response.FlowEditorContextResponseBody;

/** 外部 API 配置编辑器目录契约（E29、E30）；只由 Operations BFF 经私网调用。 */
@HttpExchange("/external-api-admin/v1")
public interface ExternalApiEditorApi {

    /**
     * 一次取得流程编辑器需要的标准 API 字段与本接入方全部原子 API 合同。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode
     *            接入方编码，不存在时返回 404
     * @param standardApiCode
     *            被实现的标准 API 编码，不存在时返回 404
     * @return 编辑器上下文
     */
    @GetExchange("/integrations/{integrationCode}/flow-editor-context")
    FlowEditorContextResponseBody getFlowEditorContext(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("integrationCode") String integrationCode,
        @RequestParam("standardApiCode") String standardApiCode);

    /**
     * 读取编辑器可选枚举、容量上限与注册函数签名。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @return 能力目录
     */
    @GetExchange("/editor-capabilities")
    EditorCapabilitiesResponseBody getEditorCapabilities(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session);
}
