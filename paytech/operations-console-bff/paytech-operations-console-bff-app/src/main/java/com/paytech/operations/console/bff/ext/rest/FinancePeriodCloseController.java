package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.FinancePeriodCloseClient;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseBoardResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.ReviewPeriodCloseRequestBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.StartPeriodReopenRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.processor.PeriodCloseBoardProcessor;
import com.paytech.operations.console.bff.service.processor.request.PeriodCloseBoardRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 期间关账与重开的浏览器入口（块 101）：看板由 BFF 聚合，其余简单转发 ledger PeriodCloseApi
 * <li>Gateway 绑定：看板、列表、详情 FINANCE_ACCOUNTING_READ；发起关账、发起重开 FINANCE_ACCOUNTING_MANAGE；批准、驳回
 * FINANCE_ACCOUNTING_APPROVE，并经 PLT-I02 实时授权。</li>
 * <li>浏览器路径 /finance/accounting-periods/v1/{accountingPeriodId}/** 对应 ledger /accounting-periods/v1/{periodId}/**；发起关账不接受 Body。</li>
 * <li>关账/重开审批同一组接口按流程类型由 ledger 分派。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance")
public class FinancePeriodCloseController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String CLOSE_NO = "[1-9][0-9]{0,8}";

    private final FinancePeriodCloseClient client;
    private final PeriodCloseBoardProcessor boardProcessor;

    /** F1 期间看板：日历下全部会计期间及各自最近一次关账/重开流程。 */
    @GetMapping("/period-close/v1/board")
    public PeriodCloseBoardResponseBody board(HttpServletRequest servletRequest,
        @RequestParam("fiscalCalendarId") @Pattern(regexp = ID) String fiscalCalendarId) {
        requireQueryKeys(servletRequest, Set.of("fiscalCalendarId"));
        return boardProcessor.process(new PeriodCloseBoardRequest(fiscalCalendarId));
    }

    /** 期间的关账/重开流程分页，按序号倒序。 */
    @GetMapping("/accounting-periods/v1/{accountingPeriodId}/closes")
    public PagingResponseBody<PeriodCloseResponseBody> listPeriodCloses(HttpServletRequest servletRequest,
        @PathVariable("accountingPeriodId") @Pattern(regexp = ID) String accountingPeriodId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listPeriodCloses(accountingPeriodId, paging.page(), paging.size());
    }

    /** 流程详情：步骤、账簿证据（最终试算、结转指令）、摘要与审批记录。 */
    @GetMapping("/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}")
    public PeriodCloseDetailResponseBody getPeriodClose(HttpServletRequest servletRequest,
        @PathVariable("accountingPeriodId") @Pattern(regexp = ID) String accountingPeriodId,
        @PathVariable("closeNo") @Pattern(regexp = CLOSE_NO) String closeNo) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getPeriodClose(accountingPeriodId, closeNo);
    }

    /** 发起关账；无请求体，携带 Body 时被请求边界拒绝。 */
    @PostMapping("/accounting-periods/v1/{accountingPeriodId}/closes")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodCloseResponseBody startPeriodClose(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("accountingPeriodId") @Pattern(regexp = ID) String accountingPeriodId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.startPeriodClose(context, accountingPeriodId);
    }

    /** 发起重开。 */
    @PostMapping("/accounting-periods/v1/{accountingPeriodId}/reopens")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodCloseResponseBody startPeriodReopen(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("accountingPeriodId") @Pattern(regexp = ID) String accountingPeriodId,
        @RequestBody @NotNull @Valid StartPeriodReopenRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.startPeriodReopen(context, accountingPeriodId, body);
    }

    /** 批准关账/重开。 */
    @PostMapping("/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/approve")
    public PeriodCloseResponseBody approvePeriodClose(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("accountingPeriodId") @Pattern(regexp = ID) String accountingPeriodId,
        @PathVariable("closeNo") @Pattern(regexp = CLOSE_NO) String closeNo,
        @RequestBody @NotNull @Valid ReviewPeriodCloseRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.approvePeriodClose(context, accountingPeriodId, closeNo, body);
    }

    /** 驳回关账/重开。 */
    @PostMapping("/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/reject")
    public PeriodCloseResponseBody rejectPeriodClose(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("accountingPeriodId") @Pattern(regexp = ID) String accountingPeriodId,
        @PathVariable("closeNo") @Pattern(regexp = CLOSE_NO) String closeNo,
        @RequestBody @NotNull @Valid ReviewPeriodCloseRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.rejectPeriodClose(context, accountingPeriodId, closeNo, body);
    }
}
