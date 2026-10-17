package com.paytech.platform.support.api.externalapi;

import java.util.List;

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
import com.paytech.platform.support.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyAccountResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyAccountSummary;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseSummary;
import com.paytech.platform.support.api.externalapi.response.VerifyRunResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunSummary;

import jakarta.validation.Valid;

/** 渠道沙箱验证的 Operations 管理契约（V01～V14）；只由 Operations BFF 经私网调用，请求幂等归 Gateway。 */
@HttpExchange("/external-api-admin/v1")
public interface IntegrationVerifyManagementApi {

    /**
     * V01 列出接入方的验证账号，按编码排序。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码，不存在时返回 404
     * @return 验证账号列表
     */
    @GetExchange("/integrations/{integrationCode}/verify-accounts")
    List<VerifyAccountSummary> listVerifyAccounts(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode);

    /**
     * V02 读取验证账号详情，含参数、沙箱凭据与引用它的用例。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param accountCode 账号编码，不存在时返回 404
     * @return 验证账号详情
     */
    @GetExchange("/integrations/{integrationCode}/verify-accounts/{accountCode}")
    VerifyAccountResponseBody getVerifyAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("accountCode") String accountCode);

    /**
     * V03 创建验证账号。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param request 账号内容及原因；编码重复返回 400，参数不合法返回 422 及 errors[]
     * @return 编码与初始版本 1
     */
    @PostExchange("/integrations/{integrationCode}/verify-accounts")
    ExternalApiMutationResponseBody createVerifyAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateVerifyAccountRequestBody request);

    /**
     * V04 按当前版本整体修改验证账号。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param accountCode 账号编码，不存在时返回 404
     * @param request 修改内容；版本不一致返回 409
     * @return 保存后的版本
     */
    @PutExchange("/integrations/{integrationCode}/verify-accounts/{accountCode}")
    ExternalApiMutationResponseBody updateVerifyAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("accountCode") String accountCode,
        @Valid @RequestBody UpdateVerifyAccountRequestBody request);

    /**
     * V05 按当前版本删除验证账号。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param accountCode 账号编码，不存在时返回 404
     * @param request 期望版本与原因；仍被用例引用时返回 400
     * @return 删除前的版本
     */
    @DeleteExchange("/integrations/{integrationCode}/verify-accounts/{accountCode}")
    ExternalApiMutationResponseBody deleteVerifyAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("accountCode") String accountCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request);

    /**
     * V06 分页查询验证用例，含最近一次运行与验证状态。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，最大 100
     * @param keyword 可选编码或名称包含查询，1～64 字符
     * @return 用例列表项与总数
     */
    @GetExchange("/integrations/{integrationCode}/verify-cases")
    PagingResponseBody<VerifyCaseSummary> listVerifyCases(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * V07 读取验证用例详情。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param caseCode 用例编码，不存在时返回 404
     * @return 验证用例详情
     */
    @GetExchange("/integrations/{integrationCode}/verify-cases/{caseCode}")
    VerifyCaseResponseBody getVerifyCase(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode);

    /**
     * V08 创建验证用例。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param request 用例内容及原因；编码重复或账号不存在返回 400，步骤不合法返回 422 及 errors[]
     * @return 编码与初始版本 1
     */
    @PostExchange("/integrations/{integrationCode}/verify-cases")
    ExternalApiMutationResponseBody createVerifyCase(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody CreateVerifyCaseRequestBody request);

    /**
     * V09 按当前版本整体修改验证用例；已有运行记录保留。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param caseCode 用例编码，不存在时返回 404
     * @param request 修改内容；版本不一致返回 409
     * @return 保存后的版本
     */
    @PutExchange("/integrations/{integrationCode}/verify-cases/{caseCode}")
    ExternalApiMutationResponseBody updateVerifyCase(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode,
        @Valid @RequestBody UpdateVerifyCaseRequestBody request);

    /**
     * V10 按当前版本删除验证用例；运行记录保留。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param caseCode 用例编码，不存在时返回 404
     * @param request 期望版本与原因
     * @return 删除前的版本
     */
    @DeleteExchange("/integrations/{integrationCode}/verify-cases/{caseCode}")
    ExternalApiMutationResponseBody deleteVerifyCase(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode,
        @Valid @RequestBody DeleteExternalApiConfigRequestBody request);

    /**
     * V11 只校验验证用例步骤，不保存；问题通过 issues 返回。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param request 待校验的账号编码与步骤
     * @return 是否可保存及全部问题
     */
    @PostExchange("/integrations/{integrationCode}/verify-cases/validate")
    ValidationResponseBody validateVerifyCase(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @Valid @RequestBody ValidateVerifyCaseRequestBody request);

    /**
     * V12 发起一次沙箱验证运行：先记录 RUNNING 并返回，事务外异步执行，页面轮询 V14。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_MANAGE
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param caseCode 用例编码，不存在时返回 404
     * @param request 运行原因，随运行快照保存
     * @return 运行编号与 RUNNING
     */
    @PostExchange("/integrations/{integrationCode}/verify-cases/{caseCode}/runs")
    VerifyRunStartResponseBody startVerifyRun(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("caseCode") String caseCode,
        @Valid @RequestBody StartVerifyRunRequestBody request);

    /**
     * V13 分页查询验证运行，按开始时间倒序，可按用例筛选。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param caseCode 可选用例编码
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，最大 100
     * @return 运行列表项与总数
     */
    @GetExchange("/integrations/{integrationCode}/verify-runs")
    PagingResponseBody<VerifyRunSummary> listVerifyRuns(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @RequestParam(value = "caseCode", required = false) String caseCode,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * V14 读取验证运行详情与证据：步骤、期望核对、轨迹与脱敏报文。
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 EXTERNAL_API_READ
     * @param integrationCode 接入方编码，不存在时返回 404
     * @param runCode 运行编号，不存在时返回 404
     * @return 运行详情
     */
    @GetExchange("/integrations/{integrationCode}/verify-runs/{runCode}")
    VerifyRunResponseBody getVerifyRun(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("integrationCode") String integrationCode,
        @PathVariable("runCode") String runCode);
}
