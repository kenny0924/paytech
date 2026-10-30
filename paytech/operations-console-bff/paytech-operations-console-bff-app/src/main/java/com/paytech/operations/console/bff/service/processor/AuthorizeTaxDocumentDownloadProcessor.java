package com.paytech.operations.console.bff.service.processor;

import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.TaxClient;
import com.paytech.operations.console.bff.integration.TaxDocumentFileClient;
import com.paytech.operations.console.bff.service.model.tax.response.TaxDocumentDownloadResponseBody;
import com.paytech.operations.console.bff.service.processor.request.AuthorizeTaxDocumentDownloadRequest;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.response.TaxDocumentResponseBody;
import com.paytech.tax.api.response.TaxOrderResponseBody;

import lombok.AllArgsConstructor;

/**
 * 税票下载（AC-14）：先向 tax 读该订单的单据确认税票已开具并取文件地址，再经 Platform 内部文件接口以操作人签发 60 秒下载地址
 * <li>tax 只提供按订单读取单据，下载路径因此带订单号；单据不在该订单的正式交易中按不存在（404）处理，不调用 Platform。</li>
 * <li>单据未开具（RESERVED / VOID）或没有文件地址同样按不存在（404）处理，不调用 Platform；文件地址格式非法按上游响应无效（502）。</li>
 * <li>Platform 内部文件接口不做运营权限判断：页面权限 TAX_VIEW 由 Gateway 路由与 PLT-I02 授权，下载请求带操作人供 Platform 审计；
 * 下载失败（文件服务不可用）不影响税票本身，页面可重试。BFF 不缓存地址。</li>
 */
@Component
@AllArgsConstructor
public class AuthorizeTaxDocumentDownloadProcessor
    extends AbstractProcessor<AuthorizeTaxDocumentDownloadRequest, TaxDocumentDownloadResponseBody> {
    private final TaxClient tax;
    private final TaxDocumentFileClient files;

    @Override
    protected TaxDocumentDownloadResponseBody processRequest(AuthorizeTaxDocumentDownloadRequest request) {
        var document = findDocument(tax.orderTax(request.getOrderNo()), request.getDocumentId());
        if (!TaxDocumentIssueStatus.ISSUED.getCode().equals(document.getIssueStatus())
            || document.getDocumentFileAddress() == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND,
                "税票不存在或尚未开具: 订单 " + request.getOrderNo() + ", 单据 " + request.getDocumentId());
        }
        var download = files.authorizeDownload(request.getOperator(), fileId(document));
        return new TaxDocumentDownloadResponseBody(document.getDocumentId(), document.getDocumentNumber(),
            download.getUrl(), download.getExpiresAt());
    }

    /** 在订单的正式交易单据中按 ID 查找；不存在按 404 处理。 */
    TaxDocumentResponseBody findDocument(TaxOrderResponseBody order, String documentId) {
        return order.getTransactions().stream().filter(Objects::nonNull)
            .flatMap(transaction -> transaction.getDocuments() == null ? Stream.empty()
                : transaction.getDocuments().stream())
            .filter(document -> document != null && documentId.equals(document.getDocumentId())).findFirst()
            .orElseThrow(() -> new FailException(CommonErrorCode.NOT_FOUND,
                "税票不存在: 订单 " + order.getOrderNo() + ", 单据 " + documentId));
    }

    /** 从 tax 单据文件地址解析文件 ID；格式非法说明 tax 响应不满足合同。 */
    static String fileId(TaxDocumentResponseBody document) {
        try {
            return Long.toString(FileAddress.parse(document.getDocumentFileAddress()).fileId());
        } catch (IllegalArgumentException invalid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid);
        }
    }
}
