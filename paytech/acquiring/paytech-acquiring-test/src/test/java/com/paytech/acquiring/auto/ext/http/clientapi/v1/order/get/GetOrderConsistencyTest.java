package com.paytech.acquiring.auto.ext.http.clientapi.v1.order.get;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.NOW;
import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.decimal;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.acquiring.api.client.v1.order.ClientOrderApi;
import com.paytech.acquiring.domain.enums.FundsAvailabilityStatus;
import com.paytech.acquiring.test.base.order.AbstractGetOrderApiTest;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;

/**
 * 不完整或冲突的持久化事实必须报系统错误，不能生成看似成功的订单详情。
 *
 * @see ClientOrderApi#getOrder(long, long, String)
 */
class GetOrderConsistencyTest extends AbstractGetOrderApiTest {
    /** 各例从完整成功订单开始，只破坏所声明的一项事实。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().paid().build();
    }

    /** 缺失事实与金额/身份冲突均保留在数据库中，查询报错但不尝试修复。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentFacts")
    void test_inconsistentFacts(String scenario, Consumer<Fixture> change) {
        change.accept(fixture);
        orderData.mock(fixture);
        invoke().assertInternalError();
    }

    /** 同一订单的金额不能混用不同币种。 */
    @ParameterizedTest
    @ValueSource(strings = {"item", "refund", "funds", "payment"})
    void test_currencyMismatch(String source) {
        switch (source) {
            case "item" -> fixture.getItems().getFirst().currency("EUR");
            case "refund" -> fixture.getRefund().setCurrency("EUR");
            case "funds" -> fixture.setFunds(funds().currency("EUR").build());
            case "payment" -> fixture.getPayment().setCurrency("EUR");
            default -> throw new AssertionError(source);
        }
        orderData.mock(fixture);
        invoke().assertInternalError();
    }

    /** 成熟状态必须与金额、时刻匹配；未成熟状态不能带最终可用事实。 */
    @ParameterizedTest
    @CsvSource({"AVAILABLE,,true", "AVAILABLE,1,false", "AVAILABLE,0,true", "AVAILABLE,-1,true", "FULLY_OFFSET,1,true",
        "FULLY_OFFSET,-1,true", "FULLY_OFFSET,0,false", "PENDING,0,false", "PENDING,,true"})
    void test_inconsistentFunds(String status, String amount, boolean hasAvailableAt) {
        fixture.setFunds(funds().fundsAvailabilityStatus(FundsAvailabilityStatus.valueOf(status))
            .availableAmount(amount == null ? null : decimal(amount)).availableAt(hasAvailableAt ? NOW : null).build());
        orderData.mock(fixture);
        invoke().assertInternalError();
    }

    /** 已开放支付的两种创建方式都必须有完整最终金额。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_payableOrderWithoutFinalAmounts(boolean directPay) {
        fixture = directPay ? orderData.template().directPay().ready().build() : orderData.template().ready().build();
        fixture.getOrder().setTotalAmount(null);
        fixture.getOrder().setTaxAmount(null);
        fixture.getOrder().setRoundingAmount(null);
        orderData.mock(fixture);
        invoke().assertInternalError();
    }

    /** 没有冻结国家时只能读取基础国家，不能默认一个国家或重新调用 CCT。 */
    @ParameterizedTest
    @ValueSource(strings = {"missing", "lowercase"})
    void test_invalidConsumerCountry(String scenario) {
        fixture.setContextCountry(null);
        fixture.getOrder().setConsumerCountryCode("missing".equals(scenario) ? null : "us");
        orderData.mock(fixture);
        invoke().assertInternalError();
    }

    /** 链接未到期时，损坏的 Token/Origin 不能作为有效支付链接返回。 */
    @ParameterizedTest
    @ValueSource(strings = {"token", "origin"})
    void test_invalidCheckoutCoordinates(String field) {
        if ("token".equals(field)) {
            fixture.getSession().setPublicToken("chk_invalid");
        } else {
            fixture.getSession().setPublicOrigin("http://checkout.local.test");
        }
        orderData.mock(fixture);
        invoke().assertInternalError();
    }

    private static Stream<Arguments> inconsistentFacts() {
        return Stream.of(invalid("Hosted Checkout 缺少 Session", f -> f.setSession(null)),
            invalid("没有商品", f -> f.setItems(List.of())), invalid("缺少退款仓位", f -> f.setRefund(null)),
            invalid("缺少资金投影", f -> f.setFunds(null)),
            invalid("退款基数为负", f -> f.getRefund().setRefundableBaseAmount(decimal("-1"))),
            invalid("退款预占为负", f -> f.getRefund().setReservedAmount(decimal("-1"))),
            invalid("已退金额为负", f -> f.getRefund().setRefundedAmount(decimal("-1"))),
            invalid("退款余额透支", f -> f.getRefund().setReservedAmount(decimal("50.01"))),
            invalid("捕获金额为负", f -> f.getPayment().setCapturedAmount(decimal("-1"))),
            invalid("Attempt 状态缺少身份", f -> f.getPayment().setLastAttemptId(null)),
            invalid("Attempt 身份缺少状态", f -> f.getPayment().setLastAttemptStatus(null)),
            invalid("已支付但无支付投影", f -> f.setPayment(null)),
            invalid("已支付但无成功 Attempt", f -> f.getPayment().setSuccessfulAttemptId(null)),
            invalid("已支付但无支付时刻", f -> f.getOrder().setPaidAt(null)),
            invalid("最终金额缺税额", f -> f.getOrder().setTaxAmount(null)),
            invalid("最终金额缺舍入额", f -> f.getOrder().setRoundingAmount(null)),
            invalid("最终金额缺总额", f -> f.getOrder().setTotalAmount(null)),
            invalid("最终金额不平衡", f -> f.getOrder().setTotalAmount(decimal("51"))),
            invalid("美元商品金额超过币种精度", f -> f.getItems().getFirst().unitAmount(decimal("0.001"))));
    }

    private static Arguments invalid(String scenario, Consumer<Fixture> change) {
        return Arguments.of(scenario, change);
    }
}
