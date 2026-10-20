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
import com.paytech.operations.console.bff.integration.FinanceManualAdjustmentClient;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ManualAdjustmentDetailResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ReviewManualAdjustmentRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.ReviewManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.SubmitManualAdjustmentRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceManualAdjustmentModels.SubmitManualAdjustmentResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 人工会计调整的浏览器入口（块 100），全部简单转发 ledger ManualAdjustmentApi
 * <li>Gateway 绑定：列表、详情 FINANCE_ACCOUNTING_READ；提交 FINANCE_ACCOUNTING_MANAGE；批准、驳回 FINANCE_ACCOUNTING_APPROVE，并经 PLT-I02 实时授权。</li>
 * <li>浏览器路径为 /finance + ledger 路径；Query 按路由白名单严格匹配，GET 不接受 Body。</li>
 * <li>编制人与复核人取自会话；复核人不能是编制人等规则由 ledger 判断。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/manual-adjustments/v1")
public class FinanceManualAdjustmentController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final FinanceManualAdjustmentClient client;

    /** 账簿的人工调整分页，按提交时间倒序。 */
    @GetMapping
    public PagingResponseBody<ManualAdjustmentResponseBody> listManualAdjustments(HttpServletRequest servletRequest,
        @RequestParam("ledgerBookId") @Pattern(regexp = ID) String ledgerBookId,
        @RequestParam(value = "accountingPeriodId", required = false) @Pattern(regexp = ID) String accountingPeriodId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "SUBMITTED|POSTED|REJECTED") String status,
        @RequestParam(value = "makerId", required = false) @Pattern(regexp = ID) String makerId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("ledgerBookId", "accountingPeriodId", "status", "makerId", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listManualAdjustments(ledgerBookId, accountingPeriodId, status, makerId, paging.page(),
            paging.size());
    }

    /** 创建并提交人工调整。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitManualAdjustmentResponseBody submitManualAdjustment(HttpServletRequest servletRequest,
        OperatorContext context, @RequestBody @NotNull @Valid SubmitManualAdjustmentRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.submitManualAdjustment(context, body);
    }

    /** 人工调整详情：明细、内容摘要、审批记录与过账结果。 */
    @GetMapping("/{manualAdjustmentId}")
    public ManualAdjustmentDetailResponseBody getManualAdjustment(HttpServletRequest servletRequest,
        @PathVariable("manualAdjustmentId") @Pattern(regexp = ID) String manualAdjustmentId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.getManualAdjustment(manualAdjustmentId);
    }

    /** 批准人工调整并过账。 */
    @PostMapping("/{manualAdjustmentId}/approve")
    public ReviewManualAdjustmentResponseBody approveManualAdjustment(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("manualAdjustmentId") @Pattern(regexp = ID) String manualAdjustmentId,
        @RequestBody @NotNull @Valid ReviewManualAdjustmentRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.approveManualAdjustment(context, manualAdjustmentId, body);
    }

    /** 驳回人工调整。 */
    @PostMapping("/{manualAdjustmentId}/reject")
    public ReviewManualAdjustmentResponseBody rejectManualAdjustment(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("manualAdjustmentId") @Pattern(regexp = ID) String manualAdjustmentId,
        @RequestBody @NotNull @Valid ReviewManualAdjustmentRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.rejectManualAdjustment(context, manualAdjustmentId, body);
    }
}
