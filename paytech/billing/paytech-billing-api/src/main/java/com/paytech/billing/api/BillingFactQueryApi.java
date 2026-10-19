package com.paytech.billing.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.billing.api.response.BillingFactListResponseBody;
import com.paytech.billing.api.response.BillingFactProgressResponseBody;
import com.paytech.billing.api.response.BillingFactResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 计费事实读取
 * <li>账簿主体参数与 ledger 账簿身份、account 进度接口一致：OPERATING_ENTITY 按事实 entityNo 匹配，FINANCE_ENTITY 按事实
 * financeEntityId 匹配，并限定币种。</li>
 * <li>只读，不开显式事务；内部开发接口，未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/billing/v1/facts", accept = "application/json")
public interface BillingFactQueryApi {

    /**
     * 查询计费事实
     *
     * @param factId 计费事实 ID
     * @return 事实头、登记与批准信息、发布状态、定价输入（仅 PAYMENT，本期恒为空）、外部引用、全部明细行及其计算证据与行链接
     *         （任意状态）；不存在时 400
     */
    @GetExchange("/{factId}")
    BillingFactResponseBody getBillingFact(@PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId);

    /**
     * 按账簿主体分页读取已批准计费事实（供 account 重放重建余额）
     *
     * @param entityType 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 账簿主体标识：entityNo 或 Finance Entity 十进制 ID
     * @param currency 账簿币种
     * @param fromFactId 游标，返回 factId 大于它的事实；缺省为 0
     * @param limit 每页条数 1–500，缺省 100；超出范围 400 INVALID_REQUEST
     * @return APPROVED 与 SUPERSEDED 事实（含引用与全部明细行），按 factId 升序
     */
    @GetExchange
    BillingFactListResponseBody listBillingFacts(
        @RequestParam("entityType") @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam("entityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String entityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "fromFactId", required = false) @Pattern(regexp = "0|[1-9][0-9]{0,18}") String fromFactId,
        @RequestParam(value = "limit", required = false) @Pattern(regexp = "[1-9][0-9]{0,2}") String limit);

    /**
     * 查询账簿主体下已批准事实的发布进度（供 ledger 关账比对 billing → account 段）
     *
     * @param entityType 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 账簿主体标识
     * @param currency 账簿币种
     * @return 未发布计数、未发布最大 factId、已发布计数与已发布最大 factId
     */
    @GetExchange("/progress")
    BillingFactProgressResponseBody getBillingFactProgress(
        @RequestParam("entityType") @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam("entityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String entityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency);

    /**
     * 按外部业务对象引用查询计费事实（如按订单一次取回支付、退款、争议与成本调整）
     *
     * @param refType 引用类型代码（FactRefType）；不在目录时 400 INVALID_REQUEST
     * @param refId 引用标识
     * @param fromFactId 游标，返回 factId 大于它的事实；缺省为 0
     * @param limit 每页条数 1–200，缺省 100；超出范围 400 INVALID_REQUEST
     * @return 引用该对象且 factId 大于游标的任意状态事实（含引用与全部明细行），按 factId 升序；nextFromFactId 为本页最后一条的
     *         factId，本页不足 limit 条时为空；无匹配返回空列表
     */
    @GetExchange("/by-ref")
    BillingFactListResponseBody listBillingFactsByRef(
        @RequestParam("refType") @Pattern(regexp = "[A-Z][A-Z_]{0,31}") String refType,
        @RequestParam("refId") @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String refId,
        @RequestParam(value = "fromFactId", required = false) @Pattern(regexp = "0|[1-9][0-9]{0,18}") String fromFactId,
        @RequestParam(value = "limit", required = false) @Pattern(regexp = "[1-9][0-9]{0,2}") String limit);
}
