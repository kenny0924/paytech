package com.paytech.commerce.test.mocker.local;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commerce.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.commerce.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.dal.OrderQueryTestMapper;

/** 查询前置数据直接入本地库；身份登记和全局 Clear 复用现有数据 Mocker。 */
@TestComponent
public final class OrderQueryDataMocker {
    private final CheckoutDataMocker data;
    private final OrderQueryTestMapper mapper;
    private final CommerceIdentifierGenerator identifiers;
    private final TransactionTemplate transaction;

    /** 数据准备使用短事务，HTTP 请求在该事务提交之后执行。 */
    public OrderQueryDataMocker(CheckoutDataMocker data, OrderQueryTestMapper mapper,
        CommerceIdentifierGenerator identifiers, PlatformTransactionManager transactions) {
        this.data = data;
        this.mapper = mapper;
        this.identifiers = identifiers;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** 预留所有根资源供失败后清理；各测试的默认数据独立构建。 */
    public GetOrderDataBuilder template() {
        return GetOrderDataBuilder.template().orderId(data.reserveOrderId()).clientId(data.clientId());
    }

    /** 直接提交本例的位置变更，供下一次 HTTP 查询验证当前表示。 */
    public void updateRefundPosition(OrderRefundPosition refund) {
        if (mapper.updateRefundPosition(refund) != 1) {
            throw new IllegalStateException("前置订单没有退款金额位置");
        }
    }

    /** 不调用任何生产创建/支付命令，缺省的投影按用例意图不插入。 */
    public void mock(Fixture fixture) {
        transaction.executeWithoutResult(status -> {
            mapper.insertOrder(fixture.getOrder());
            for (var item : fixture.getItems()) {
                item.orderItemId(identifiers.nextId());
                mapper.insertItem(item);
            }
            if (fixture.getSession() != null) {
                mapper.insertSession(fixture.getSession());
            }
            if (fixture.getRefund() != null) {
                mapper.insertRefund(fixture.getRefund());
            }
            if (fixture.getFunds() != null) {
                mapper.insertFunds(fixture.getFunds());
            }
            if (fixture.getPayment() != null) {
                mapper.insertPayment(fixture.getOrder().getOrderId(), fixture.getPayment());
            }
            if (fixture.getContextCountry() != null) {
                mapper.insertContext(fixture);
            }
        });
    }
}
