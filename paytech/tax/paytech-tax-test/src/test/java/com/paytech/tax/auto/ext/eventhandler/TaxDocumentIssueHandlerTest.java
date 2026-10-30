package com.paytech.tax.auto.ext.eventhandler;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.platform.support.api.files.request.StartFileUploadRequestBody;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.service.handler.TaxDocumentIssueHandler;
import com.paytech.tax.test.base.MockTestBase;
import com.paytech.tax.test.builder.CalculateTaxRequestBuilder;
import com.paytech.tax.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.tax.test.builder.TaxTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 开具 B2C 简化税票（补偿任务 TAX_DOCUMENT_ISSUE，AC-11）
 * <li>正式交易经真实 MQ 登记后，开票任务由生产 Dispatcher 在提交后执行；Platform 文件接口与对象存储直传由 Mock 服务承载。</li>
 * <li>重试用例在首个自动尝试失败（任务 ERROR、单据 RESERVED）后直接调用 Handler 入口完成重试，验证不重新取号。</li>
 *
 * @see TaxDocumentIssueHandler#handle(CompensationEvent)
 */
class TaxDocumentIssueHandlerTest extends MockTestBase {
    /** SA 注册 ID。 */
    static final long SA_REGISTRATION = 1201L;

    @Autowired
    private TaxDocumentIssueHandler handler;

    /** SA 注册（USD 申报）。 */
    @BeforeEach
    void setUpClassMocks() {
        subjects.registered(SA_REGISTRATION, "SA", TaxTestConstants.SEED_POLICY_SA, TaxTestConstants.USD, "Asia/Riyadh",
            FilingFrequency.QUARTERLY);
    }

    /** AC-11：分号 SA-RCPT-年份-000001，PRIVATE 上传 PDF，文件地址与摘要落库，票面不含消费者资料、金额与正式事实一致。 */
    @Test
    void test_issuesReceipt() throws Exception {
        files.acceptUploads(0);

        long transactionId = registerPaidOrder(TaxTestConstants.ORDER_ID);
        Map<String, Object> document = awaitDocument(transactionId, TaxDocumentIssueStatus.ISSUED);

        assertEquals(series() + "-000001", document.get("document_number"));
        assertEquals("/files/v1/" + files.completions().get(0), document.get("document_file_address"));
        byte[] pdf = files.content(files.completions().get(0));
        assertTrue(new String(pdf, 0, 8, StandardCharsets.US_ASCII).startsWith("%PDF-1.4"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)),
            document.get("document_digest"));
        StartFileUploadRequestBody start = files.starts().get(0);
        assertAll(() -> assertEquals(TaxCodes.FILE_REASON_TAX_RECEIPT, start.getReasonCode()),
            () -> assertEquals("PRIVATE", start.getAccessLevel()), () -> assertEquals("application/pdf", start.getMimeType()),
            () -> assertEquals(Integer.toString(pdf.length), start.getSizeBytes()),
            () -> assertEquals(List.of("tax-service"), files.callers()));
        JsonNode content = objectMapper.readTree((String)document.get("document_content_json"));
        assertAll(() -> assertEquals("Test Seller LLC", content.path("seller").path("name").asString()),
            () -> assertEquals("TEST-TRN-SA", content.path("seller").path("taxRegistrationNo").asString()),
            () -> assertEquals("115.00", content.path("totals").path("grossAmount").asString()),
            () -> assertEquals("15.00", content.path("totals").path("taxAmount").asString()),
            () -> assertEquals("0.15", content.path("lines").get(0).path("rate").asString()));
        for (String personal : List.of("buyer", "consumer", "customer", "email", "address", "name")) {
            assertFalse(content.has(personal), "票面不得含消费者资料字段: " + personal);
        }
    }

    /** 两笔交易开票号码不同（同一序列行上串行取号）。 */
    @Test
    void test_twoTransactionsDistinctNumbers() {
        files.acceptUploads(0);

        long first = registerPaidOrder(TaxTestConstants.ORDER_ID);
        long second = registerPaidOrder(TaxTestConstants.ORDER_ID + 1);
        Object firstNumber = awaitDocument(first, TaxDocumentIssueStatus.ISSUED).get("document_number");
        Object secondNumber = awaitDocument(second, TaxDocumentIssueStatus.ISSUED).get("document_number");

        assertNotEquals(firstNumber, secondNumber);
        assertEquals(List.of(series() + "-000001", series() + "-000002"), testMapper.selectDocumentNumbers());
    }

    /** 首次上传失败：任务可重试、单据保持 RESERVED 与号码；重试同一单据成功，不重新取号。 */
    @Test
    void test_uploadFailureRetriesSameNumber() {
        files.acceptUploads(1);

        long transactionId = registerPaidOrder(TaxTestConstants.ORDER_ID);
        Map<String, Object> reserved = awaitDocument(transactionId, TaxDocumentIssueStatus.RESERVED);
        await().atMost(ASYNC_WAIT).until(() -> "ERROR".equals(
            testMapper.selectCompensationEvents(TaxDocumentIssueHandler.EVENT_TYPE).get(0).get("execute_status")));

        CompensationHandlerResult result = handler.handle(new CompensationEvent(1L, Long.toString(transactionId),
            TaxDocumentIssueHandler.EVENT_TYPE, "RECEIPT", null, 2));

        assertEquals(CompensationHandlerResult.success(), result);
        Map<String, Object> issued = testMapper.selectDocument(transactionId);
        assertEquals(TaxDocumentIssueStatus.ISSUED.getCode(), issued.get("issue_status"));
        assertEquals(reserved.get("document_number"), issued.get("document_number"));
        assertEquals(List.of(series() + "-000001"), testMapper.selectDocumentNumbers());
        assertEquals(2, files.starts().size());
    }

    /** 事件身份非法或交易不存在：终态失败，不调用文件服务。 */
    @Test
    void test_invalidOrMissingSourceTerminal() {
        CompensationHandlerResult malformed = handler.handle(new CompensationEvent(1L, "not-a-number",
            TaxDocumentIssueHandler.EVENT_TYPE, "RECEIPT", null, 1));
        CompensationHandlerResult missing = handler.handle(new CompensationEvent(2L, "999999999",
            TaxDocumentIssueHandler.EVENT_TYPE, "RECEIPT", null, 1));

        assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, malformed.outcome());
        assertEquals("TAX_DOCUMENT_EVENT_INVALID", malformed.errorCode());
        assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, missing.outcome());
        assertEquals("TAX_DOCUMENT_SOURCE_MISSING", missing.errorCode());
        files.assertNoCalls();
    }

    /** 计算并经真实 MQ 登记一笔 SA 支付成功，返回正式交易 ID。 */
    long registerPaidOrder(long orderId) {
        String calculationId = post("/tax/v1/calculations", CalculateTaxRequestBuilder.template()
            .order(orderId, "ORD-" + orderId).build()).assertSuccess(200).text("calculationId");
        DomainEvent event = OrderPaymentSucceededEnvelopeBuilder.template().order(orderId)
            .taxCalculation(calculationId, TaxCalculationStatus.READY, "15.00",
                OrderPaymentSucceededEnvelopeBuilder.component("SA", Long.toString(SA_REGISTRATION), "15.00"))
            .build();
        String eventId = acquiringEvents.publish(event);
        await().atMost(ASYNC_WAIT).until(() -> testMapper.countConsumedEvent(eventId) == 1);
        return ((Number)testMapper.selectTransaction(Long.toString(orderId)).get("id")).longValue();
    }

    /** 等到交易的单据进入给定状态。 */
    Map<String, Object> awaitDocument(long transactionId, TaxDocumentIssueStatus status) {
        await().atMost(ASYNC_WAIT).until(() -> {
            Map<String, Object> document = testMapper.selectDocument(transactionId);
            return document != null && status.getCode().equals(document.get("issue_status"));
        });
        return testMapper.selectDocument(transactionId);
    }

    /** 本年度 SA 序列代码（分号时间在 Asia/Riyadh 的年份）。 */
    static String series() {
        return "SA-RCPT-" + Instant.now().atZone(ZoneId.of("Asia/Riyadh")).getYear();
    }
}
