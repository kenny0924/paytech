package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody;
import com.paytech.ledger.api.request.StartPeriodCloseRequestBody;
import com.paytech.ledger.api.request.StartPeriodReopenRequestBody;
import com.paytech.ledger.api.response.PeriodCloseDetailResponseBody;
import com.paytech.ledger.api.response.PeriodCloseResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * FIN-22 期间关账（内部能力 Trial Balance &amp; Period Close）
 * <li>发起关账同步执行 7 个固定步骤，成功进入 PENDING_CHECK，失败为 FAILED 且期间回到 OPEN。</li>
 * <li>必须按顺序关账：同日历更早期间全部 CLOSED 后才能发起。</li>
 * <li>重开只针对同日历最近关闭的期间：发起即冻结冲回计划进入 PENDING_CHECK，批准时过账冲回分录并回到 OPEN，驳回回到 CLOSED。</li>
 * <li>关账与重开共用流程序号、审批、驳回与查询接口，按流程类型分派。</li>
 * <li>Finance 内部开发接口：操作人标识由调用方传入，未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/accounting-periods/v1", accept = "application/json")
public interface PeriodCloseApi {

    /**
     * 发起期间关账
     *
     * @param periodId 会计期间主键
     * @param body 编制人
     * @return 201 与流程摘要（PENDING_CHECK 或 FAILED）；期间不存在、非 OPEN 或前序期间未关闭时 400；同期间已有活动流程或并发发起时 409
     */
    @PostExchange("/{periodId}/closes")
    ResponseEntity<PeriodCloseResponseBody> startPeriodClose(
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @Valid @RequestBody StartPeriodCloseRequestBody body);

    /**
     * 发起期间重开
     *
     * @param periodId 会计期间主键
     * @param body 编制人、重开原因与申请编号
     * @return 201 与 REOPEN 流程摘要（PENDING_CHECK，closeHash 已冻结）；期间不存在、非 CLOSED、存在更晚的已关闭或关账/重开中期间、
     *         原因代码不合法或缺少已完成的原关账时 400；同期间已有活动流程或并发发起时 409
     */
    @PostExchange("/{periodId}/reopens")
    ResponseEntity<PeriodCloseResponseBody> startPeriodReopen(
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @Valid @RequestBody StartPeriodReopenRequestBody body);

    /**
     * 批准期间关账
     *
     * @param periodId 会计期间主键
     * @param closeNo 期间内流程序号
     * @param body 复核人与确认摘要
     * @return 200 与 COMPLETED 流程摘要；关账流程期间随之 CLOSED，重开流程过账冲回分录后期间回到 OPEN；
     *         非待审批、同人复核或摘要不符时 400
     */
    @PostExchange("/{periodId}/closes/{closeNo}/approve")
    PeriodCloseResponseBody approvePeriodClose(
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @PathVariable("closeNo") @Pattern(regexp = "[1-9][0-9]{0,8}") String closeNo,
        @Valid @RequestBody ReviewPeriodCloseRequestBody body);

    /**
     * 驳回期间关账
     *
     * @param periodId 会计期间主键
     * @param closeNo 期间内流程序号
     * @param body 复核人与确认摘要
     * @return 200 与 REJECTED 流程摘要；关账流程期间回到 OPEN，重开流程期间回到 CLOSED 且不生成分录；
     *         非待审批、同人复核或摘要不符时 400
     */
    @PostExchange("/{periodId}/closes/{closeNo}/reject")
    PeriodCloseResponseBody rejectPeriodClose(
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @PathVariable("closeNo") @Pattern(regexp = "[1-9][0-9]{0,8}") String closeNo,
        @Valid @RequestBody ReviewPeriodCloseRequestBody body);

    /**
     * 分页查询期间的关账流程，按序号降序
     *
     * @param periodId 会计期间主键
     * @param page 可选页码
     * @param size 可选每页条数：10、20、50、100、200
     * @return 流程摘要分页；期间不存在时 400
     */
    @GetExchange("/{periodId}/closes")
    PagingResponseBody<PeriodCloseResponseBody> listPeriodCloses(
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询关账流程详情
     *
     * @param periodId 会计期间主键
     * @param closeNo 期间内流程序号
     * @return 摘要、步骤、账簿证据与审批记录；期间或流程不存在时 400
     */
    @GetExchange("/{periodId}/closes/{closeNo}")
    PeriodCloseDetailResponseBody getPeriodClose(
        @PathVariable("periodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String periodId,
        @PathVariable("closeNo") @Pattern(regexp = "[1-9][0-9]{0,8}") String closeNo);
}
