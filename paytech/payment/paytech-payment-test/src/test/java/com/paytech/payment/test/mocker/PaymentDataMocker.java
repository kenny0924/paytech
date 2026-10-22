package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.dao.DataAccessException;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.payment.test.dal.PaymentTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * mor_payment 的空基线与清理：只触碰本库 11 张表（payment 10 + 补偿，10 §3.1）；卡表与 CVV 键第 12 步随卡域迁入 card（mor_card）。
 * <li>本机 mor_payment 若仍保留第 12 步前的卡 4 表（不 DROP，见第 12 步报告），本清理器不读写它们。</li>
 * <li>Payment 测试不再安排渠道数据；渠道候选与执行由 {@link ChannelConnectorMocker} 以 channel-connector-api DTO 配置。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class PaymentDataMocker implements Clear {
    private static final String DATABASE = "mor_payment";

    private final PaymentTestMapper mapper;
    private boolean reusable = true;
    private boolean cleanupOwned;

    /** 拒绝共享库和脏基线；缺表时明确提示手工初始化入口。 */
    public void assertEmpty() {
        assertTrue(reusable, "测试库清理失败，须人工核查后重新运行");
        try {
            assertEquals(DATABASE, mapper.databaseName());
            assertEquals(0, mapper.countExecutionRows() + mapper.countSchemaOnlyRows(), "专用库非空，不自动删除原有数据");
            cleanupOwned = true;
        } catch (DataAccessException failure) {
            throw new AssertionError("请先人工审查并应用 paytech-payment-test/src/test/resources/init/ddl.sql 到 mor-local MySQL",
                failure);
        }
    }

    /** 把处理中的尝试置为已授权未捕获、无当前动作；用于 AUTHORIZE 尚未接入渠道时的授权后事实。 */
    public void authorizedAwaitingCapture(long attemptId, BigDecimal amount) {
        assertEquals(1, mapper.authorizeAwaitingCapture(attemptId, amount), "尝试不处于可置为已授权的状态");
    }

    /** 按依赖顺序清理本库全部表，任一失败即禁止环境复用。 */
    @Override
    public void clear() {
        if (!cleanupOwned) {
            return;
        }
        assertEquals(DATABASE, mapper.databaseName());
        try {
            assertAll(mapper::clearCompensationEvents, mapper::clearConsumedEvents,
                mapper::clearConsumedAggregateVersions, mapper::clearChannelRefunds, mapper::clearRefundPositions,
                mapper::clearDuplicatePaymentCases, mapper::clearTransactionRefs, mapper::clearActions,
                mapper::clearRouteCandidateEvals, mapper::clearRoutingDecisions, mapper::clearAttempts,
                mapper::clearInstrumentTokens, mapper::clearIntents);
            assertEquals(0, mapper.countExecutionRows() + mapper.countSchemaOnlyRows());
            cleanupOwned = false;
        } catch (RuntimeException | AssertionError failure) {
            reusable = false;
            throw failure;
        }
    }

}
