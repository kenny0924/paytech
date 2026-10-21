package com.paytech.reconciliation.service.handler;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.reconciliation.service.processor.MatchChannelStatementProcessor;
import com.paytech.reconciliation.service.processor.request.MatchChannelStatementRequest;

/**
 * 本地任务 CHANNEL_STATEMENT_MATCH：锁账单逐行匹配、开异常、尝试对平（technique D-10）
 * <li>事件键 {@code channel-statement:<statementId>}；业务子类型是触发来源：{@value #TRIGGER_IMPORT}（完成导入）、
 * {@code PLATFORM_TXN-<id>}（新平台流水到达）、{@code RESOLUTION-<id>}（处置应用后重算门禁）。不同来源各自登记，避免任务执行中再次登记被
 * 当作重复而丢失唤醒。</li>
 * <li>账单锁竞争为可重试失败；匹配逻辑与事务由 {@link MatchChannelStatementProcessor} 负责，本 Handler 只解析身份并委托。</li>
 * <li>处理器经 {@link ObjectProvider} 在执行时取得：处理器依赖的本地服务要登记补偿任务，而补偿登记器在启动时收集全部 Handler，直接构造器注入
 * 会形成启动循环。</li>
 */
@Component
public class ChannelStatementMatchHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "CHANNEL_STATEMENT_MATCH";

    /** 触发来源：账单完成导入。 */
    public static final String TRIGGER_IMPORT = "IMPORT";

    /** 事件键前缀。 */
    static final String KEY_PREFIX = "channel-statement:";

    /** 合法业务子类型。 */
    static final Pattern TRIGGER = Pattern.compile("IMPORT|PLATFORM_TXN-[1-9][0-9]{0,18}|RESOLUTION-[1-9][0-9]{0,18}");

    /** 合法事件键。 */
    static final Pattern KEY = Pattern.compile("channel-statement:[1-9][0-9]{0,18}");

    private final ObjectProvider<MatchChannelStatementProcessor> matchChannelStatementProcessor;

    /** 以延迟取得的匹配处理器构造。 */
    public ChannelStatementMatchHandler(ObjectProvider<MatchChannelStatementProcessor> matchChannelStatementProcessor) {
        this.matchChannelStatementProcessor = matchChannelStatementProcessor;
    }

    /**
     * 纯工厂：账单完成导入时登记
     *
     * @param statementId 渠道账单主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long statementId) {
        return new CompensationEventRequest(KEY_PREFIX + statementId, EVENT_TYPE, TRIGGER_IMPORT);
    }

    /**
     * 纯工厂：新平台流水到达时为同交易号的账单登记
     *
     * @param statementId 渠道账单主键
     * @param platformTxnId 新平台流水主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildPlatformTxnEvent(long statementId, long platformTxnId) {
        return new CompensationEventRequest(KEY_PREFIX + statementId, EVENT_TYPE, "PLATFORM_TXN-" + platformTxnId);
    }

    /**
     * 纯工厂：处置应用后重算门禁时登记
     *
     * @param statementId 渠道账单主键
     * @param resolutionId 已应用的处置主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildResolutionEvent(long statementId, long resolutionId) {
        return new CompensationEventRequest(KEY_PREFIX + statementId, EVENT_TYPE, "RESOLUTION-" + resolutionId);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /**
     * 每次尝试 100 秒（须短于补偿 Lease，默认 2 分钟），最多 10 次，2 秒起指数退避至 10 分钟
     * <li>2 万行账单的匹配须在该时限内完成；超大账单需同时调大 compensation.lease-duration 与本超时。</li>
     */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(100), 10, Duration.ofSeconds(2), Duration.ofMinutes(10),
            2, 0.2);
    }

    /**
     * 解析账单 ID 并执行匹配
     * <li>事件身份非法为终态失败；账单锁竞争或版本冲突为可重试失败；账单已不在 MATCHING 时匹配处理器直接返回。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (event.payloadJson() != null || !KEY.matcher(event.eventKey()).matches()
            || !TRIGGER.matcher(event.subEventType()).matches()) {
            return CompensationHandlerResult.terminalFailure("CHANNEL_STATEMENT_MATCH_INVALID",
                "Channel statement match event is malformed");
        }
        long statementId = Long.parseLong(event.eventKey().substring(KEY_PREFIX.length()));
        try {
            matchChannelStatementProcessor.getObject().process(new MatchChannelStatementRequest(statementId));
        } catch (ConcurrentRequestException contended) {
            return CompensationHandlerResult.retryableFailure("CHANNEL_STATEMENT_MATCH_CONTENDED",
                "Channel statement is being processed, retry later");
        }
        return CompensationHandlerResult.success();
    }
}
