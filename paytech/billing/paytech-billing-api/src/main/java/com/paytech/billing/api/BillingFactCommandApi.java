package com.paytech.billing.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.billing.api.request.ApproveBillingFactRequestBody;
import com.paytech.billing.api.request.SubmitBillingFactRequestBody;
import com.paytech.billing.api.response.BillingFactResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 计费事实人工登记（Maker-Checker）
 * <li>本期只登记 MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT（originType = MANUAL）：Maker 提交 DRAFT，Checker（≠ Maker）批准后事实版本定稿，
 * businessTime 覆盖为批准时间，同一本地事务登记 BILLING_FACT_RECORDED 补偿任务，提交后由补偿 Handler 发布 BillingFactRecorded。</li>
 * <li>读取接口见 {@link BillingFactQueryApi}。</li>
 * <li>内部开发接口：操作人标识由调用方传入，未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/billing/v1/facts", accept = "application/json")
public interface BillingFactCommandApi {

    /**
     * 提交计费事实（DRAFT）
     *
     * @param body 类型、来源引用、主体、币种、外部引用、明细行与编制人
     * @return 新提交 201；同来源引用同内容重复提交 200 并返回原事实；字段格式或代码不在目录、事实类型不允许的引用类型时 400
     *         INVALID_REQUEST；来源引用内容不同、合作方二元组不完整、平台服务费收方不等于 Finance Entity、行主体与头部不一致、
     *         目标余额桶用于非 Owner 收方、币种未启用或金额超精度、来源事实或被替代事实不合格、COST_ADJUSTMENT 缺少 CLEARING_ACCOUNT
     *         引用时 400 BUSINESS_CHECK_FAILED；并发写入时 409
     */
    @PostExchange
    ResponseEntity<BillingFactResponseBody> submitBillingFact(@Valid @RequestBody SubmitBillingFactRequestBody body);

    /**
     * 批准计费事实
     *
     * @param factId 计费事实 ID
     * @param body 复核人，必须不同于编制人
     * @return 200 与 APPROVED 事实；同一复核人重复批准返回已批准事实且不重复登记事件；事实不存在、复核人等于编制人、
     *         已由其他复核人批准或被替代事实已不是 APPROVED 时 400；被替代事实并发变化时 409
     */
    @PostExchange("/{factId}/approve")
    BillingFactResponseBody approveBillingFact(
        @PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId,
        @Valid @RequestBody ApproveBillingFactRequestBody body);
}
