package com.paytech.platform.support.api.referencedata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.referencedata.request.ChangeCurrencySpecStatusRequestBody;
import com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody;
import com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody;

import jakarta.validation.Valid;

/**
 * 全平台币种规则维护入口；Platform 为唯一 Owner，服务端再次验证 CURRENCY_SPEC_MANAGE，请求幂等由 Gateway 负责。
 * <li>查询沿用 {@link CurrencySpecQueryApi}，完整字段（数字代码、名称、符号、logo）见 {@link ReferenceCurrencyQueryApi}。</li>
 * <li>启用后数字代码、名称、符号、小数位与舍入方式冻结，只允许修改 logo 文件地址。</li>
 */
@HttpExchange(url = "/currency-admin/v1/currency-specs", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CurrencySpecManagementApi {

    /**
     * 新建 DRAFT 币种规则
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 币种代码、数字代码、名称、符号、小数位、舍入方式、可选 logo 文件地址及原因
     * @return 保存结果，objectCode 为币种代码，version 为 1
     *         <li>币种代码已存在返回 400 BUSINESS_CHECK_FAILED（“币种规则已存在: {currency}”）。</li>
     */
    @PostExchange
    ConfigurationMutationResponseBody createCurrencySpec(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateCurrencySpecRequestBody request);

    /**
     * 按原版本修改币种规则
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param currency 三位大写字母币种代码
     * @param request 目标数字代码、名称、符号、小数位、舍入方式、logo 文件地址、原版本及原因
     * @return 保存结果
     *         <li>非 DRAFT 时冻结字段与库中不同返回 422 并写明冻结字段，只允许修改 logo 文件地址；logo 文件不是公开文件返回 422。</li>
     *         <li>无变化不递增版本；版本不符返回并发冲突；不存在返回 NOT_FOUND。</li>
     */
    @PutExchange("/{currency}")
    ConfigurationMutationResponseBody updateCurrencySpec(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("currency") String currency,
        @Valid @RequestBody UpdateCurrencySpecRequestBody request);

    /**
     * 按原版本切换币种规则状态
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param currency 三位大写字母币种代码
     * @param request 目标状态、原版本及原因
     * @return 保存结果
     *         <li>只允许 DRAFT→ACTIVE、ACTIVE→INACTIVE、INACTIVE→ACTIVE；其他转换拒绝。</li>
     */
    @PatchExchange("/{currency}/status")
    ConfigurationMutationResponseBody changeCurrencySpecStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("currency") String currency,
        @Valid @RequestBody ChangeCurrencySpecStatusRequestBody request);
}
