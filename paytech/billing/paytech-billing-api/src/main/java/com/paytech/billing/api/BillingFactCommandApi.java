package com.paytech.billing.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.billing.api.request.SubmitBillingFactRequestBody;
import com.paytech.billing.api.response.BillingFactResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 计费事实人工登记（Maker-Checker）
 * <li>本期只登记 MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT（originType = MANUAL）：Maker 提交 DRAFT，Checker（≠ Maker）批准后事实版本定稿，
 * businessTime 覆盖为批准时间，同一本地事务登记 BILLING_FACT_RECORDED 补偿任务，提交后由补偿 Handler 发布 BillingFactRecorded；
 * 填错的 DRAFT 可作废为 VOIDED，不发布事件。</li>
 * <li>读取接口见 {@link BillingFactQueryApi}；调用方需要区分的业务拒绝见 {@link com.paytech.billing.api.enums.BillingFactErrorCode}。</li>
 * <li>Finance 内部接口：操作人身份来自 Operations BFF 从会话填入的 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header（Operations
 * Account ID 与会话 ID），仅经 Operations BFF 转发，不得直接对外发布。</li>
 */
@HttpExchange(url = "/billing/v1/facts", accept = "application/json")
public interface BillingFactCommandApi {

    /**
     * 提交计费事实（DRAFT）
     *
     * @param operatorId 编制人 Operations Account ID（X-Gateway-Actor-Subject）；缺失或格式不符时 400 INVALID_REQUEST
     * @param sessionId 编制人会话 ID（X-Gateway-Session-Id），只记日志；缺失时 400 INVALID_REQUEST
     * @param body 类型、来源引用、主体、币种、外部引用与明细行
     * @return 新提交 201；同来源引用同内容重复提交 200 并返回原事实（编制人参与内容比较）；字段格式或代码不在目录、事实类型不允许的
     *         引用类型时 400 INVALID_REQUEST；明细行违反规则（组件、收付方、合作方组件、目标余额桶、金额非正或超精度）时 400
     *         FACT_LINE_INVALID；被替代事实不合格时 400 FACT_SUPERSEDE_INVALID；来源引用内容不同、合作方二元组不完整、币种未启用、
     *         来源事实不合格、缺少 Client 或 COST_ADJUSTMENT 缺少来源事实 / CLEARING_ACCOUNT 引用时 400 BUSINESS_CHECK_FAILED；并发写入时 409
     */
    @PostExchange
    ResponseEntity<BillingFactResponseBody> submitBillingFact(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String operatorId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody SubmitBillingFactRequestBody body);

    /**
     * 批准计费事实
     *
     * @param operatorId 复核人 Operations Account ID（X-Gateway-Actor-Subject），必须不同于编制人
     * @param sessionId 复核人会话 ID（X-Gateway-Session-Id），只记日志
     * @param factId 计费事实 ID
     * @return 200 与 APPROVED 事实；同一复核人重复批准返回已批准事实且不重复登记事件；复核人等于编制人时 400 SELF_REVIEW_FORBIDDEN；
     *         已作废或已由其他复核人批准时 400 FACT_NOT_DRAFT；被替代事实已不是 APPROVED 时 400 FACT_SUPERSEDE_INVALID；事实不存在时
     *         400 BUSINESS_CHECK_FAILED；被替代事实并发变化时 409
     */
    @PostExchange("/{factId}/approve")
    BillingFactResponseBody approveBillingFact(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String operatorId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId);

    /**
     * 作废计费事实草稿（DRAFT → VOIDED）
     * <li>不发布事件、不登记补偿任务；作废人只记日志（不新增字段），权限由 Operations 路由控制。</li>
     *
     * @param operatorId 作废人 Operations Account ID（X-Gateway-Actor-Subject）
     * @param sessionId 作废人会话 ID（X-Gateway-Session-Id），只记日志
     * @param factId 计费事实 ID
     * @return 200 与 VOIDED 事实；已作废时直接返回原事实；已批准、已替代时 400 FACT_NOT_DRAFT；事实不存在时 400 BUSINESS_CHECK_FAILED；
     *         条件更新未命中时 409
     */
    @PostExchange("/{factId}/void")
    BillingFactResponseBody voidBillingFact(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String operatorId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId);
}
