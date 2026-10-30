package com.paytech.tax.service.handler;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.core.dal.dataobject.TaxDocument;
import com.paytech.tax.domain.document.TaxDocumentIssueContext;
import com.paytech.tax.domain.document.TaxReceiptPdf;
import com.paytech.tax.domainservice.service.TaxDocumentService;
import com.paytech.tax.integration.file.TaxDocumentFileClient;

import lombok.AllArgsConstructor;

/**
 * 正式交易登记提交后开具 B2C 简化税票（补偿任务 TAX_DOCUMENT_ISSUE，technique 9.3、11.5）
 * <li>eventKey 为正式交易 ID，subEventType 为 RECEIPT；Handler 从本库交易、冻结计算、注册与地区包重建票面，补偿记录不保存业务内容。</li>
 * <li>步骤：短事务锁序列取号、插入 RESERVED 单据 → 事务外渲染 PDF 并经 file 服务 start → PUT → complete（PRIVATE）→ 短事务写文件地址、
 * 摘要并转 ISSUED。重试复用已占号的同一单据，不重新取号；已 ISSUED 直接成功。</li>
 * <li>上传失败、序列并发冲突可重试；交易不存在或没有可开票组件为终态失败，保留记录供人工处理。</li>
 */
@Component
@AllArgsConstructor
public class TaxDocumentIssueHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "TAX_DOCUMENT_ISSUE";

    /** 业务子类型：简化税票。 */
    static final String SUB_EVENT_RECEIPT = "RECEIPT";

    /** 税票文件类型。 */
    static final String PDF_MIME_TYPE = "application/pdf";

    private final Clock clock;
    private final TaxDocumentService taxDocumentService;
    private final TaxDocumentFileClient taxDocumentFileClient;

    /**
     * 纯工厂：只能与正式交易在同一本地事务登记
     *
     * @param transactionId 正式交易 ID
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long transactionId) {
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Invalid tax transaction id");
        }
        return new CompensationEventRequest(Long.toString(transactionId), EVENT_TYPE, SUB_EVENT_RECEIPT);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 60 秒，最多 10 次，10 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(60), 10, Duration.ofSeconds(10), Duration.ofMinutes(30),
            2, 0.2);
    }

    /**
     * 分号（首次）→ 渲染并上传 → 标记 ISSUED
     * <li>必须在事务外执行；事件身份非法或来源事实缺失为终态失败，上传与并发冲突可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("TAX_DOCUMENT_TRANSACTION_ACTIVE",
                "Tax document must be issued outside a transaction");
        }
        Long transactionId = parse(event);
        if (transactionId == null) {
            return CompensationHandlerResult.terminalFailure("TAX_DOCUMENT_EVENT_INVALID", "Tax document event is malformed");
        }
        TaxDocumentIssueContext context = taxDocumentService.loadIssueContext(transactionId);
        if (context == null) {
            return CompensationHandlerResult.terminalFailure("TAX_DOCUMENT_SOURCE_MISSING",
                "Tax transaction or taxable component is missing");
        }
        try {
            return issue(context);
        } catch (ConcurrentRequestException | DuplicateKeyException conflict) {
            return CompensationHandlerResult.retryableFailure("TAX_DOCUMENT_CONFLICT", "Tax document numbering conflicted");
        }
    }

    /** 取得或占用单据后上传并标记；已 ISSUED 直接成功。 */
    CompensationHandlerResult issue(TaxDocumentIssueContext context) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        TaxDocument document = context.getDocument() == null ? taxDocumentService.reserveReceipt(context, now)
            : context.getDocument();
        if (document.getIssueStatus() == TaxDocumentIssueStatus.ISSUED) {
            return CompensationHandlerResult.success();
        }
        byte[] pdf = TaxReceiptPdf.render(document.getDocumentContentJson());
        String fileAddress;
        try {
            fileAddress = taxDocumentFileClient.uploadPrivate(document.getDocumentNumber() + ".pdf", PDF_MIME_TYPE, pdf);
        } catch (RuntimeException unavailable) {
            // 不把文件服务返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("TAX_DOCUMENT_UPLOAD_FAILED", "Tax document upload requires retry");
        }
        taxDocumentService.markIssued(document, fileAddress, sha256(pdf), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return CompensationHandlerResult.success();
    }

    /** 事件键为正十进制交易 ID、子类型为 RECEIPT、无 Payload；否则 null。 */
    static Long parse(CompensationEvent event) {
        boolean valid = event.payloadJson() == null && SUB_EVENT_RECEIPT.equals(event.subEventType())
            && event.eventKey().matches("[1-9][0-9]{0,18}");
        return valid ? Long.valueOf(event.eventKey()) : null;
    }

    /** 文件 SHA-256 小写十六进制。 */
    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException missing) {
            throw new ErrorException("运行环境缺少 SHA-256", missing);
        }
    }
}
