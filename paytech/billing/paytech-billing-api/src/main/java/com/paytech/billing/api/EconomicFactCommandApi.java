package com.paytech.billing.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.billing.api.request.ApproveEconomicFactRequestBody;
import com.paytech.billing.api.request.SubmitEconomicFactRequestBody;
import com.paytech.billing.api.response.EconomicFactListResponseBody;
import com.paytech.billing.api.response.EconomicFactResponseBody;
import com.paytech.billing.api.response.PendingEconomicFactsResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 经济事实登记（Maker-Checker）与读取（design/04 §2、§8）
 * <li>阶段 0 只登记 MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT：Maker 提交 DRAFT，Checker（≠ Maker）批准后事实版本定稿，
 * 同一本地事务登记 EconomicFactRecorded 发布任务，提交后由补偿 Handler 发布到 billing.events。</li>
 * <li>账簿主体参数与 ledger 账簿身份一致：OPERATING_ENTITY 账簿按事实 entityNo 匹配，FINANCE_ENTITY 账簿按事实
 * financeEntityId 匹配，并限定币种。</li>
 * <li>内部开发接口：操作人标识由调用方传入，未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/billing/v1/economic-facts", accept = "application/json")
public interface EconomicFactCommandApi {

    /**
     * 提交经济事实（DRAFT）
     *
     * @param body 类型、来源引用、主体、币种、明细行与编制人
     * @return 新提交 201；同来源引用同内容重复提交 200 并返回原事实；字段格式或代码不在目录时 400 INVALID_REQUEST；
     *         来源引用内容不同、合作方三元组不完整、平台服务费主体不等于 Finance Entity、币种未启用或金额超精度、
     *         来源事实或被替代事实不合格时 400 BUSINESS_CHECK_FAILED；并发写入时 409
     */
    @PostExchange
    ResponseEntity<EconomicFactResponseBody> submitEconomicFact(@Valid @RequestBody SubmitEconomicFactRequestBody body);

    /**
     * 批准经济事实
     *
     * @param factId 经济事实 ID
     * @param body 复核人，必须不同于编制人
     * @return 200 与 APPROVED 事实；同一复核人重复批准返回已批准事实且不重复登记事件；事实不存在、复核人等于编制人、
     *         已由其他复核人批准或被替代事实已不是 APPROVED 时 400；被替代事实并发变化时 409
     */
    @PostExchange("/{factId}/approve")
    EconomicFactResponseBody approveEconomicFact(
        @PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId,
        @Valid @RequestBody ApproveEconomicFactRequestBody body);

    /**
     * 查询经济事实
     *
     * @param factId 经济事实 ID
     * @return 事实头与全部明细行（任意状态）；不存在时 400
     */
    @GetExchange("/{factId}")
    EconomicFactResponseBody getEconomicFact(@PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId);

    /**
     * 按账簿主体分页读取已批准经济事实（供 account 重放重建余额）
     *
     * @param bookEntityType 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param bookEntityId 账簿主体标识：entityNo 或 Finance Entity 十进制 ID
     * @param currency 账簿币种
     * @param fromFactId 游标，返回 factId 大于它的事实；缺省为 0
     * @param limit 每页条数 1–500，缺省 100
     * @return APPROVED 与 SUPERSEDED 事实（含全部明细行），按 factId 升序
     */
    @GetExchange
    EconomicFactListResponseBody listEconomicFacts(
        @RequestParam("bookEntityType") @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String bookEntityType,
        @RequestParam("bookEntityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String bookEntityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency,
        @RequestParam(value = "fromFactId", required = false) @Pattern(regexp = "0|[1-9][0-9]{0,18}") String fromFactId,
        @RequestParam(value = "limit", required = false) @Pattern(regexp = "[1-9][0-9]{0,2}") String limit);

    /**
     * 查询账簿主体下尚未发布的已批准事实
     *
     * @param bookEntityType 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param bookEntityId 账簿主体标识
     * @param currency 账簿币种
     * @return 未发布计数、未发布最大 factId 与已发布最大 factId
     */
    @GetExchange("/pending")
    PendingEconomicFactsResponseBody getPendingEconomicFacts(
        @RequestParam("bookEntityType") @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String bookEntityType,
        @RequestParam("bookEntityId") @Pattern(regexp = "[A-Z0-9]{1,19}") String bookEntityId,
        @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency);
}
