package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody;
import com.paytech.ledger.api.request.SubmitManualAdjustmentRequestBody;
import com.paytech.ledger.api.response.ManualAdjustmentDetailResponseBody;
import com.paytech.ledger.api.response.ManualAdjustmentResponseBody;
import com.paytech.ledger.api.response.ReviewManualAdjustmentResponseBody;
import com.paytech.ledger.api.response.SubmitManualAdjustmentResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 人工会计调整 Maker-Checker（FIN-10 人工调整；内部能力 Accounting Instruction）
 * <li>提交即冻结内容摘要；复核人必须不同于编制人，并原样确认同一摘要。</li>
 * <li>批准后在同一本地事务内生成人工记账指令并过账；驳回后不可再审批。</li>
 * <li>Finance 内部接口：编制人与复核人取自 Operations BFF 从会话填入的 X-Gateway-Actor-Subject（Operations Account ID）/
 * X-Gateway-Session-Id Header，仅经 Operations BFF 转发，不得直接对外发布。</li>
 * <li>需要页面区分的拒绝以 {@link com.paytech.ledger.api.enums.LedgerErrorCode} 返回 400：SELF_REVIEW_FORBIDDEN、REVIEW_CONTENT_CHANGED、
 * REVIEW_STATUS_INVALID、ADJUSTMENT_UNBALANCED、ADJUSTMENT_PERIOD_NOT_OPEN、ADJUSTMENT_ACCOUNT_INVALID、OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN；
 * 其余为 BUSINESS_CHECK_FAILED。</li>
 */
@HttpExchange(url = "/manual-adjustments/v1", accept = "application/json")
public interface ManualAdjustmentApi {

    /**
     * 创建并提交人工调整
     *
     * @param actorAccountId 编制人 Account ID（X-Gateway-Actor-Subject），正十进制整数字符串；缺失或格式错误时 400
     * @param sessionId 编制人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param body 请求
     * @return 201 与调整主键、单号和内容摘要；账簿/期间/账户/币种/精度/平衡不满足时 400
     */
    @PostExchange
    ResponseEntity<SubmitManualAdjustmentResponseBody> submitManualAdjustment(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody SubmitManualAdjustmentRequestBody body);

    /**
     * 批准人工调整并过账
     *
     * @param actorAccountId 复核人 Account ID（X-Gateway-Actor-Subject），必须不同于编制人；缺失或格式错误时 400
     * @param sessionId 复核人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param manualAdjustmentId 调整主键
     * @param body 复核请求
     * @return 200 与过账分录；同一复核人同一摘要重复批准返回原分录；其他已处理、同人复核、摘要不符或过账门禁失败时 400；同一调整并发复核时 409
     */
    @PostExchange("/{manualAdjustmentId}/approve")
    ReviewManualAdjustmentResponseBody approveManualAdjustment(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("manualAdjustmentId") @Pattern(regexp = "[1-9][0-9]{0,18}") String manualAdjustmentId,
        @Valid @RequestBody ReviewManualAdjustmentRequestBody body);

    /**
     * 驳回人工调整
     *
     * @param actorAccountId 复核人 Account ID（X-Gateway-Actor-Subject），必须不同于编制人；缺失或格式错误时 400
     * @param sessionId 复核人会话 ID（X-Gateway-Session-Id）；缺失时 400
     * @param manualAdjustmentId 调整主键
     * @param body 复核请求
     * @return 200 与 REJECTED 状态；同一复核人同一摘要重复驳回返回当前结果；其他已处理、同人复核或摘要不符时 400
     */
    @PostExchange("/{manualAdjustmentId}/reject")
    ReviewManualAdjustmentResponseBody rejectManualAdjustment(
        @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("manualAdjustmentId") @Pattern(regexp = "[1-9][0-9]{0,18}") String manualAdjustmentId,
        @Valid @RequestBody ReviewManualAdjustmentRequestBody body);

    /**
     * 分页查询账簿的人工调整，按提交时间、主键降序
     *
     * @param ledgerBookId 账簿主键，必填
     * @param accountingPeriodId 可选会计期间主键
     * @param status 可选状态：SUBMITTED、POSTED 或 REJECTED
     * @param makerId 可选编制人 Account ID
     * @param page 可选页码
     * @param size 可选每页条数：10、20、50、100、200
     * @return 调整摘要分页；账簿不存在时 400
     */
    @GetExchange
    PagingResponseBody<ManualAdjustmentResponseBody> listManualAdjustments(
        @RequestParam("ledgerBookId") @Pattern(regexp = "[1-9][0-9]{0,18}") String ledgerBookId,
        @RequestParam(value = "accountingPeriodId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String accountingPeriodId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "SUBMITTED|POSTED|REJECTED") String status,
        @RequestParam(value = "makerId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String makerId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询人工调整详情
     *
     * @param manualAdjustmentId 调整主键
     * @return 调整头、明细、审批记录与过账结果；不存在时 400
     */
    @GetExchange("/{manualAdjustmentId}")
    ManualAdjustmentDetailResponseBody getManualAdjustment(
        @PathVariable("manualAdjustmentId") @Pattern(regexp = "[1-9][0-9]{0,18}") String manualAdjustmentId);
}
