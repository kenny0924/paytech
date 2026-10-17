package com.paytech.platform.support.api.externalapi;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.*;

import com.paytech.platform.support.api.externalapi.request.CreateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationListResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;

import jakarta.validation.Valid;

/** 渠道标准 API 实现的 Operations 管理契约（E23～E28）；只由 Operations BFF 经私网调用，请求幂等归 Gateway。 */
@HttpExchange("/external-api-admin/v1")
public interface IntegrationImplementationManagementApi {

    /**
     * 查询接入方的全部标准 API 实现，按标准 API 编码排序，不分页。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode
     *            接入方编码，不存在时返回 404
     * @return 实现列表
     */
    @GetExchange("/integrations/{integrationCode}/implementations")
    ImplementationListResponseBody listImplementations(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("integrationCode") String integrationCode);

    /**
     * 读取一个标准 API 实现的流程定义。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode
     *            接入方编码
     * @param standardApiCode
     *            标准 API 编码；接入方、标准 API 或实现不存在时返回 404
     * @return 实现详情
     */
    @GetExchange("/integrations/{integrationCode}/implementations/{standardApiCode}")
    ImplementationResponseBody getImplementation(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("integrationCode") String integrationCode,
        @PathVariable("standardApiCode") String standardApiCode);

    /**
     * 创建标准 API 实现。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode
     *            接入方编码，不存在时返回 404
     * @param request
     *            标准 API、流程与原因；已实现或标准 API 不存在返回 400，流程静态校验失败返回 422 及 errors[]
     * @return 编码与初始版本 1
     */
    @PostExchange("/integrations/{integrationCode}/implementations")
    ExternalApiMutationResponseBody createImplementation(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateImplementationRequestBody request);

    /**
     * 按当前版本整体替换流程。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode
     *            接入方编码
     * @param standardApiCode
     *            标准 API 编码，实现不存在时返回 404
     * @param request
     *            新流程；静态校验失败返回 422，版本不一致返回 409
     * @return 保存后的版本
     */
    @PutExchange("/integrations/{integrationCode}/implementations/{standardApiCode}")
    ExternalApiMutationResponseBody updateImplementation(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("integrationCode") String integrationCode,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody UpdateImplementationRequestBody request);

    /**
     * 按当前版本物理删除实现。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode
     *            接入方编码
     * @param standardApiCode
     *            标准 API 编码，实现不存在时返回 404
     * @param request
     *            期望版本与原因
     * @return 删除前的版本
     */
    @DeleteExchange("/integrations/{integrationCode}/implementations/{standardApiCode}")
    ExternalApiMutationResponseBody deleteImplementation(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("integrationCode") String integrationCode,
        @PathVariable("standardApiCode") String standardApiCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request);

    /**
     * 只做流程静态校验，不保存；问题通过 issues 返回而不是错误响应。
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode
     *            接入方编码，不存在时返回 404
     * @param request
     *            待校验的流程
     * @return 是否可保存及全部问题
     */
    @PostExchange("/integrations/{integrationCode}/implementations/validate")
    ValidationResponseBody validateImplementation(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody ValidateImplementationRequestBody request);
}
