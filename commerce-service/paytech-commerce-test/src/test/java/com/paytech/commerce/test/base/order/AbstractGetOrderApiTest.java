package com.paytech.commerce.test.base.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.core.dal.dataobject.order.OrderFundsAvailabilityProjection;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.checker.order.GetOrderResponseChecker;
import com.paytech.commerce.test.checker.order.OrderQueryChecker;
import com.paytech.commerce.test.mocker.local.OrderQueryDataMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/** 订单详情测试共用真实 HTTP 调用及只读断言，不放测试方法或通用业务成功 Mock。 */
public abstract class AbstractGetOrderApiTest extends MockTestBase {
    protected static final String REQUEST_ID = "Commerce.GetOrder.Test";

    @Autowired
    protected OrderQueryDataMocker orderData;

    @Autowired
    protected OrderQueryChecker orderChecker;

    protected Fixture fixture;

    /** 复制当前投影的必要事实供用例显式改变，不查询数据库或修复负例。 */
    protected OrderFundsAvailabilityProjection.OrderFundsAvailabilityProjectionBuilder funds() {
        var funds = fixture.getFunds();
        return OrderFundsAvailabilityProjection.builder()
            .orderFundsAvailabilityProjectionId(funds.orderFundsAvailabilityProjectionId()).orderId(funds.orderId())
            .fundsAvailabilityStatus(funds.fundsAvailabilityStatus()).currency(funds.currency())
            .availableAmount(funds.availableAmount()).scheduledAt(funds.scheduledAt()).availableAt(funds.availableAt())
            .asOf(funds.asOf()).version(funds.version()).createTime(funds.createTime()).updateTime(funds.updateTime());
    }

    /** 默认按当前 Client 查询已插入订单，无幂等 Header。 */
    protected GetOrderResponseChecker invoke() {
        return invoke(fixture.getOrder().getOrderNo());
    }

    /** 保留原始大小写及空白，不修复负例参数。 */
    protected GetOrderResponseChecker invoke(String orderNo) {
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", orderNo);
    }

    /** 每次调用都比较数据库前后快照并显式检查 CCT 零调用。 */
    protected GetOrderResponseChecker invoke(String clientId, String credentialId, String orderNo) {
        var before = orderChecker.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        // RestTestClient 负责 URI 编码；提前 encode 会把空格二次编码成字面值 %20。
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}")
            .buildAndExpand(orderNo).toUriString();
        var response = httpTestInvoker.get(path, TestRequestHeaders.client(REQUEST_ID, clientId, credentialId));
        orderChecker.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        cctCheckoutMocker.assertNoCalls();
        return new GetOrderResponseChecker(response, objectMapper, REQUEST_ID);
    }
}
