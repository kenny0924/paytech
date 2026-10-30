package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.tax.response.TaxDocumentDownloadResponseBody;
import com.paytech.operations.console.bff.service.model.tax.response.TaxOrderPageResponseBody;
import com.paytech.operations.console.bff.service.processor.AuthorizeTaxDocumentDownloadProcessor;
import com.paytech.operations.console.bff.service.processor.GetOrderTaxProcessor;
import com.paytech.operations.console.bff.service.processor.request.AuthorizeTaxDocumentDownloadRequest;
import com.paytech.operations.console.bff.service.processor.request.GetOrderTaxRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 财务 → 税务查询（AC-13、AC-14）的浏览器入口；页面只读
 * <li>Gateway 绑定：两条路由都要求 TAX_VIEW，并经 PLT-I02 实时授权；外部路径 /api/operations/tax/v1/... 转发到同路径去掉
 * /api/operations。</li>
 * <li>详情由 GetOrderTaxProcessor 读取 tax 并组装页面模型；下载由 AuthorizeTaxDocumentDownloadProcessor 读取 tax 单据后经 Platform
 * 签发 60 秒地址。下载无请求体，不接受 Query。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/tax/v1/orders")
public class TaxOrderController {
    /** 与 tax 合同一致的公开订单号格式。 */
    private static final String ORDER_NO = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
    private static final String ID = "[1-9][0-9]{0,18}";

    private final GetOrderTaxProcessor orderTax;
    private final AuthorizeTaxDocumentDownloadProcessor documentDownload;

    /** 订单税务全貌：状态、进度、提示、金额、逐行明细、报价记录、税票、登记、底稿与依据。 */
    @GetMapping("/{orderNo}")
    public TaxOrderPageResponseBody getOrderTax(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("orderNo") @Pattern(regexp = ORDER_NO) String orderNo) {
        requireQueryKeys(servletRequest, Set.of());
        return orderTax.process(new GetOrderTaxRequest(operator, orderNo));
    }

    /** 签发该订单税票的 60 秒下载地址；无请求体。 */
    @PostMapping("/{orderNo}/documents/{documentId}/download")
    public TaxDocumentDownloadResponseBody authorizeDocumentDownload(HttpServletRequest servletRequest,
        OperatorContext operator, @PathVariable("orderNo") @Pattern(regexp = ORDER_NO) String orderNo,
        @PathVariable("documentId") @Pattern(regexp = ID) String documentId) {
        requireQueryKeys(servletRequest, Set.of());
        return documentDownload.process(new AuthorizeTaxDocumentDownloadRequest(operator, orderNo, documentId));
    }
}
