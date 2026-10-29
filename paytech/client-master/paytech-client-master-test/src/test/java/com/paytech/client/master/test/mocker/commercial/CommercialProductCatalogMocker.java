package com.paytech.client.master.test.mocker.commercial;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductCapabilitySummary;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * commercial 产品能力目录（GET /products/v1/capabilities）的 DTO 配置与调用记录。
 * <li>未配置时 Mock 控制器把调用记为意外调用；全局 BeforeEach 配置“4 个 ACTIVE 支付方式”的公共基线，与 mor-test 目录一致。</li>
 * <li>只模拟列表读取：可开通项其余写入方法一律为意外调用。</li>
 */
@TestComponent
public class CommercialProductCatalogMocker implements Clear {
    /** 可开通项种类：支付方式。 */
    public static final String PAYMENT_METHOD = "PAYMENT_METHOD";
    /** 可开通项种类：功能。 */
    public static final String FEATURE = "FEATURE";
    /** 收单产品。 */
    public static final String ACQUIRING = "ACQUIRING";
    /** 目录状态：ACTIVE。 */
    public static final String ACTIVE = "ACTIVE";
    /** 目录状态：RETIRED。 */
    public static final String RETIRED = "RETIRED";
    /** 基线 ACTIVE 支付方式，按可开通项 ID 升序。 */
    public static final List<String> STANDARD_PAYMENT_METHODS = List.of("CARD", "APPLE_PAY", "GOOGLE_PAY", "STC_PAY");

    /** 一次配置的响应：列表 DTO、HTTP 故障或断连。 */
    public record Reply(ProductCapabilityListResponseBody body, int status, ApiProblemResponse problem, boolean disconnect) {
    }

    /** 一次调用的查询参数。 */
    public record Call(String productCode, String status) {
    }

    private Reply reply;
    private final List<Call> calls = new CopyOnWriteArrayList<>();

    /** 公共基线：4 个 ACTIVE 支付方式。 */
    public synchronized void mockStandardCatalog() {
        mockCapabilities(STANDARD_PAYMENT_METHODS.stream().map(code -> capability(code, PAYMENT_METHOD, ACTIVE)).toList());
    }

    /** 返回给定的可开通项（原样，含非支付方式或非 ACTIVE 行，用于验证过滤）。 */
    public synchronized void mockCapabilities(List<ProductCapabilitySummary> capabilities) {
        var body = new ProductCapabilityListResponseBody();
        body.setCapabilities(new ArrayList<>(capabilities));
        reply = new Reply(body, 200, null, false);
    }

    /** 返回 200 但缺少可开通项列表的不合法响应。 */
    public synchronized void mockMissingList() {
        reply = new Reply(new ProductCapabilityListResponseBody(), 200, null, false);
    }

    /** commercial 返回 503 Problem。 */
    public synchronized void mockUnavailable() {
        reply = new Reply(null, 503, new ApiProblemResponse(ApiProblemProfile.TYPE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            "测试依赖不可用", "SERVICE_UNAVAILABLE", "client-master-commercial-test", "2026-10-29T00:00:00.000000Z", null), false);
    }

    /** commercial 断连。 */
    public synchronized void mockDisconnect() {
        reply = new Reply(null, 0, null, true);
    }

    /**
     * 记录调用并返回本例配置
     *
     * @param productCode 查询的产品代码
     * @param status 查询的状态
     * @return 配置的响应；未配置时为 null
     */
    public synchronized Reply listCapabilities(String productCode, String status) {
        calls.add(new Call(productCode, status));
        return reply;
    }

    /** 调用记录。 */
    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 可开通项行（ID 与名称为测试值）。 */
    public static ProductCapabilitySummary capability(String code, String kind, String status) {
        var row = new ProductCapabilitySummary();
        row.setProductCapabilityId(Long.toString(9_281_200_000_700_000L + Math.abs(code.hashCode() % 1000)));
        row.setProductCode(ACQUIRING);
        row.setCapabilityCode(code);
        row.setCapabilityName(code);
        row.setCapabilityKind(kind);
        row.setPaymentMethodCode(PAYMENT_METHOD.equals(kind) ? code : null);
        row.setDimensions(List.of());
        row.setStatus(status);
        row.setChangeVersion(1L);
        return row;
    }

    /** 每例清空配置与记录。 */
    @Override
    public synchronized void clear() {
        reply = null;
        calls.clear();
    }
}
