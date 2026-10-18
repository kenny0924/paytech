package com.paytech.platform.support.api.externalapi;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationScriptSummary;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;

import jakarta.validation.Valid;

/** 公共脚本的 Operations 管理契约（E17～E22）；只由 Operations BFF 经私网调用，请求幂等归 Gateway。 */
@HttpExchange("/external-api-admin/v1")
public interface IntegrationScriptManagementApi {

    /**
     * 分页查询接入方下的公共脚本，按编码稳定排序。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，最大 100
     * @param keyword 可选编码或名称包含查询，1～64 字符，通配符按文字处理
     * @param executionStage 可选执行阶段筛选，REQUEST 或 RESPONSE
     * @return 公共脚本列表项与总数
     */
    @GetExchange("/integrations/{integrationCode}/scripts")
    PagingResponseBody<IntegrationScriptSummary> listIntegrationScripts(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "executionStage", required = false) String executionStage);

    /**
     * 读取公共脚本详情，含引用方。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码
     * @param scriptCode 公共脚本编码，不存在时返回 404
     * @return 公共脚本详情
     */
    @GetExchange("/integrations/{integrationCode}/scripts/{scriptCode}")
    IntegrationScriptResponseBody getIntegrationScript(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("scriptCode") String scriptCode);

    /**
     * 创建公共脚本。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param request 定义及原因；编码重复或引用不存在返回 400，静态校验失败返回 422 及 errors[]
     * @return 编码与初始版本 1
     */
    @PostExchange("/integrations/{integrationCode}/scripts")
    ExternalApiMutationResponseBody createIntegrationScript(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateIntegrationScriptRequestBody request);

    /**
     * 按当前版本整体修改公共脚本。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码
     * @param scriptCode 公共脚本编码，不存在时返回 404
     * @param request 修改内容；破坏引用方时返回 400，版本不一致返回 409
     * @return 保存后的版本
     */
    @PutExchange("/integrations/{integrationCode}/scripts/{scriptCode}")
    ExternalApiMutationResponseBody updateIntegrationScript(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("scriptCode") String scriptCode,
        @Valid @RequestBody UpdateIntegrationScriptRequestBody request);

    /**
     * 按当前版本物理删除公共脚本。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码
     * @param scriptCode 公共脚本编码，不存在时返回 404
     * @param request 期望版本与原因；仍被引用时返回 400
     * @return 删除前的版本
     */
    @DeleteExchange("/integrations/{integrationCode}/scripts/{scriptCode}")
    ExternalApiMutationResponseBody deleteIntegrationScript(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("scriptCode") String scriptCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request);

    /**
     * 只做静态校验，不保存；问题通过 issues 返回而不是错误响应。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param request 待校验的配置
     * @return 是否可保存及全部问题
     */
    @PostExchange("/integrations/{integrationCode}/scripts/validate")
    ValidationResponseBody validateIntegrationScript(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody ValidateIntegrationScriptRequestBody request);
}
