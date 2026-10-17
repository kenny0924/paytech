package com.paytech.commerce.test.mocker.local;

import java.time.Instant;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commerce.core.dal.dataobject.customer.ConsumerAcceptance;
import com.paytech.commerce.domain.enums.ConsumerAcceptanceType;
import com.paytech.commerce.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.dal.OrderCancellationTestMapper;

/** 复用订单 Fixture 和全局清理，仅增加取消场景需要的前置事实。 */
@TestComponent
public class OrderCancellationDataMocker {
    private final OrderQueryDataMocker orders;
    private final OrderCancellationTestMapper mapper;
    private final CommerceIdentifierGenerator identifiers;

    private final TransactionTemplate transaction;

    /** 准备事务在 HTTP 请求前提交，不包住被测业务。 */
    public OrderCancellationDataMocker(OrderQueryDataMocker orders, OrderCancellationTestMapper mapper,
        CommerceIdentifierGenerator identifiers, PlatformTransactionManager manager) {
        this.orders = orders;
        this.mapper = mapper;
        this.identifiers = identifiers;
        this.transaction = new TransactionTemplate(manager);
    }

    /** 原样保存 Fixture 的状态和历史时刻，不修复用例构造的异常记录。 */
    public void mock(Fixture fixture) {
        transaction.executeWithoutResult(status -> {
            orders.mock(fixture);
            mapper.orderCancellationFacts(fixture.getOrder());
            if (fixture.getSession() != null) {
                mapper.sessionCancellationFacts(fixture.getSession());
            }
        });
    }

    /** 已存在合同的订单保存三份接受和网络证据，模拟 Intent 响应丢失后的本地事实。 */
    public void mockAcceptances(long orderId, Instant now) {
        transaction.executeWithoutResult(status -> {
            mapper.insertEvidence(orderId, now);
            for (var type : ConsumerAcceptanceType.values()) {
                mapper.insertAcceptance(ConsumerAcceptance.builder().consumerAcceptanceId(identifiers.nextId())
                    .consumerContractId(orderId).networkEvidenceId(orderId).acceptanceType(type)
                    .acceptedAt(now).createTime(now).build());
            }
        });
    }

    /** 竞争由真实数据库版本变化表达，不替换 Repository。 */
    public void advanceSessionVersion(long orderId) {
        mapper.advanceSessionVersion(orderId);
    }

    /** 支付投影版本变化必须触发取消 SQL 的最后一道校验。 */
    public void advancePaymentVersion(long orderId) {
        mapper.advancePaymentVersion(orderId);
    }
}
