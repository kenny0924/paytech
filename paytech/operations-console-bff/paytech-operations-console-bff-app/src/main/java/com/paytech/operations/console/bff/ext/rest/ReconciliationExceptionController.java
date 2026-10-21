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
import com.paytech.operations.console.bff.integration.ReconciliationExceptionClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.OpenReconExceptionRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionDetailResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconExceptionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconResolutionResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReconSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.ReviewReconResolutionRequestBody;
import com.paytech.operations.console.bff.service.model.ReconciliationExceptionModels.SubmitReconResolutionRequestBody;
import com.paytech.operations.console.bff.service.processor.ReconExceptionDetailProcessor;
import com.paytech.operations.console.bff.service.processor.request.ReconExceptionDetailRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 对账异常、处置与在途汇总的浏览器入口（块 123），除详情外简单转发 reconciliation ReconExceptionApi、ReconResolutionApi、
 * ReconSummaryApi
 * <li>Gateway 绑定：查询 FINANCE_RECONCILIATION_READ；人工开异常、提交处置 FINANCE_RECONCILIATION_OPERATE；复核处置
 * FINANCE_RECONCILIATION_APPROVE。提交人不能复核、归类码范围与处置应用由 reconciliation 判断。</li>
 * <li>详情由 ReconExceptionDetailProcessor 补所属清算账户或银行账户的可读标识。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance/reconciliation/v1")
public class ReconciliationExceptionController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final ReconciliationExceptionClient client;
    private final ReconExceptionDetailProcessor detailProcessor;

    /** 对账异常分页，按账龄从长到短；不传状态时只查未结。 */
    @GetMapping("/recon-exceptions")
    public PagingResponseBody<ReconExceptionResponseBody> listReconExceptions(HttpServletRequest servletRequest,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "OPEN|UNDER_REVIEW|RESOLVED|AUTO_CLOSED") String status,
        @RequestParam(value = "exceptionType", required = false) @Pattern(regexp = "[A-Z][A-Z_]{0,31}") String exceptionType,
        @RequestParam(value = "reconLayer", required = false) @Pattern(regexp = "TRANSACTION|BANK") String reconLayer,
        @RequestParam(value = "clearingAccountId", required = false) @Pattern(regexp = ID) String clearingAccountId,
        @RequestParam(value = "cashAccountId", required = false) @Pattern(regexp = ID) String cashAccountId,
        @RequestParam(value = "statementId", required = false) @Pattern(regexp = ID) String statementId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("status", "exceptionType", "reconLayer", "clearingAccountId",
            "cashAccountId", "statementId", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listReconExceptions(status, exceptionType, reconLayer, clearingAccountId, cashAccountId,
            statementId, paging.page(), paging.size());
    }

    /** 对账异常详情：异常、来源对象摘要、可用处置与归类码、处置历史与所属账户标识。 */
    @GetMapping("/recon-exceptions/{exceptionId}")
    public ReconExceptionDetailResponseBody getReconException(HttpServletRequest servletRequest,
        @PathVariable("exceptionId") @Pattern(regexp = ID) String exceptionId) {
        requireQueryKeys(servletRequest, Set.of());
        return detailProcessor.process(new ReconExceptionDetailRequest(exceptionId));
    }

    /** 人工开立对账异常（平台流水、预期银行流水、银行流水）。 */
    @PostMapping("/recon-exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ReconExceptionResponseBody openReconException(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid OpenReconExceptionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.openReconException(context, body);
    }

    /** 提交异常处置。 */
    @PostMapping("/recon-exceptions/{exceptionId}/resolutions")
    @ResponseStatus(HttpStatus.CREATED)
    public ReconResolutionResponseBody submitReconResolution(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("exceptionId") @Pattern(regexp = ID) String exceptionId,
        @RequestBody @NotNull @Valid SubmitReconResolutionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.submitReconResolution(context, exceptionId, body);
    }

    /** 复核异常处置：批准即应用，拒绝异常回到 OPEN。 */
    @PostMapping("/recon-resolutions/{resolutionId}/approval")
    public ReconResolutionResponseBody reviewReconResolution(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("resolutionId") @Pattern(regexp = ID) String resolutionId,
        @RequestBody @NotNull @Valid ReviewReconResolutionRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.reviewReconResolution(context, resolutionId, body);
    }

    /** 在途汇总：在途账单、开放异常账龄段与未匹配银行流水。 */
    @GetMapping("/summary")
    public ReconSummaryResponseBody getReconSummary(HttpServletRequest servletRequest,
        @RequestParam(value = "entityNo", required = false) @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam("currency") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
        requireQueryKeys(servletRequest, Set.of("entityNo", "currency"));
        return client.getReconSummary(entityNo, currency);
    }
}
