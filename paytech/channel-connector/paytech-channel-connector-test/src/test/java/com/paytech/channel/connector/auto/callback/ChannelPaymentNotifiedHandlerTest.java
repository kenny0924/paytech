package com.paytech.channel.connector.auto.callback;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.event.ChannelPaymentNotifiedPayload;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;
import com.paytech.channel.connector.core.enums.CallbackMappingStatus;
import com.paytech.channel.connector.core.enums.CallbackSignatureStatus;
import com.paytech.channel.connector.core.enums.CallbackStatus;
import com.paytech.channel.connector.core.enums.CallbackTargetType;
import com.paytech.channel.connector.domainservice.repository.ChannelCallbackItemRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCallbackRepository;
import com.paytech.channel.connector.service.handler.ChannelPaymentNotifiedHandler;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;
import com.paytech.channel.connector.test.probe.ChannelEventProbe;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.id.IdGenerator;

import tools.jackson.databind.JsonNode;

/**
 * ChannelPaymentNotified 发布任务入口（3DS 全链路 technique 3.3.5）：从回调信封与唯一回调项重建事件并真实发布到本地 Broker，
 * 收到确认后信封置 PROCESSED；已发布的信封不再发布；未验签、未映射或身份非法为终态失败。共享 Broker 上 payment 的分区拓扑可能已绑定
 * 发布方交换机，因此不在此验证“未路由”分支（由 paytech-messaging 的发布测试覆盖）。
 * 回调行经生产 Repository 插入，不登记补偿任务，避免提交后唤醒的执行器与本例并发处理同一回调。
 *
 * @see ChannelPaymentNotifiedHandler#handle(CompensationEvent)
 */
class ChannelPaymentNotifiedHandlerTest extends ChannelConnectorMockTestBase {
    private static final long ATTEMPT_ID = 730000000000000302L;

    @Autowired
    private ChannelPaymentNotifiedHandler handler;

    @Autowired
    private ChannelCallbackRepository callbacks;

    @Autowired
    private ChannelCallbackItemRepository items;

    @Autowired
    private ChannelTestMapper mapper;

    @Autowired
    private ChannelEventProbe events;

    @Autowired
    private IdGenerator ids;

    private Fixture fixture;

    /** 本类公共：一个启用的支付渠道与账号；绑定事件观察队列。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.activeFixture();
        events.bind();
    }

    /** 删除观察队列。 */
    @AfterEach
    void tearDownClassMocks() {
        events.unbind();
    }

    /** 已映射的验签通知：发布一条信封，回调置 PROCESSED；再次执行视为已发布，不再发布。 */
    @Test
    void test_publishedAndMarkedProcessed() {
        ChannelCallback callback = callback(CallbackSignatureStatus.VERIFIED, CallbackStatus.RECEIVED, CallbackMappingStatus.MAPPED);

        var first = handler.handle(event(callback.getChannelCallbackId()));
        JsonNode envelope = events.awaitEnvelope(callback.getChannelCallbackId());
        var second = handler.handle(event(callback.getChannelCallbackId()));

        assertAll(() -> assertEquals(CompensationHandlerResult.success(), first),
            () -> assertEquals(CompensationHandlerResult.success(), second),
            () -> assertEquals(ChannelPaymentNotifiedPayload.EVENT_TYPE, envelope.path("eventType").asString()),
            () -> assertEquals(Long.toString(ATTEMPT_ID), envelope.path("payload").path("merchantReference").asString()),
            () -> assertEquals(CallbackStatus.PROCESSED.getCode(), mapper.callbacks().getFirst().get("callbackStatus")));
        events.assertNoEnvelope();
    }

    /** 未验签、未映射或身份非法：终态失败，不发布。 */
    @Test
    void test_invalidTerminal() {
        ChannelCallback rejected = callback(CallbackSignatureStatus.REJECTED, CallbackStatus.REJECTED, CallbackMappingStatus.MAPPED);
        ChannelCallback unmatched = callback(CallbackSignatureStatus.VERIFIED, CallbackStatus.PROCESSED,
            CallbackMappingStatus.UNMATCHED);

        assertAll(
            () -> assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE,
                handler.handle(event(rejected.getChannelCallbackId())).outcome()),
            () -> assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE,
                handler.handle(event(unmatched.getChannelCallbackId())).outcome()),
            () -> assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE,
                handler.handle(new CompensationEvent(1, "abc", ChannelPaymentNotifiedHandler.EVENT_TYPE, "-", null, 1)).outcome()),
            () -> assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE,
                handler.handle(event(ids.nextId())).outcome()));
        events.assertNoEnvelope();
    }

    /** 经生产 Repository 插入一条回调信封与唯一回调项（不登记补偿任务）。 */
    private ChannelCallback callback(CallbackSignatureStatus signature, CallbackStatus status, CallbackMappingStatus mapping) {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var callback = new ChannelCallback();
        callback.setChannelCallbackId(ids.nextId());
        callback.setChannelId(fixture.getChannel().getChannelId());
        callback.setChannelAccountId(fixture.getAccount().getChannelAccountId());
        callback.setSignatureStatus(signature);
        callback.setCallbackStatus(status);
        callback.setReceivedAt(now);
        callback.setVersion(1L);
        callback.setCreateTime(now);
        callback.setUpdateTime(now);
        callbacks.insert(callback);
        var item = new ChannelCallbackItem();
        item.setChannelCallbackItemId(ids.nextId());
        item.setChannelCallbackId(callback.getChannelCallbackId());
        item.setItemNo(1);
        item.setEventType("PAYMENT_RESULT");
        item.setTargetType(mapping == CallbackMappingStatus.MAPPED ? CallbackTargetType.PAYMENT_ATTEMPT : CallbackTargetType.UNKNOWN);
        item.setTargetId(mapping == CallbackMappingStatus.MAPPED ? ATTEMPT_ID : null);
        item.setExternalTransactionId("T-HANDLER-1");
        item.setNormalizedStatus("SUCCESS");
        item.setMappingStatus(mapping);
        item.setCreateTime(now);
        items.insert(item);
        return callback;
    }

    /** 指向回调信封的补偿事件。 */
    private static CompensationEvent event(long callbackId) {
        return new CompensationEvent(1, Long.toString(callbackId), ChannelPaymentNotifiedHandler.EVENT_TYPE, "-", null, 1);
    }
}
