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
import com.paytech.reconciliation.service.processor.SuggestBankMatchesProcessor;
import com.paytech.reconciliation.service.processor.request.SuggestBankMatchesRequest;

/**
 * 本地任务 BANK_MATCH_SUGGEST：为一个银行账户的未匹配流水出一对一匹配建议（设计 F2，technique D-10）
 * <li>事件键 {@code cash-account:<cashAccountId>}；业务子类型是触发来源：{@code BANK_STATEMENT-<id>}（对账单导入）、
 * {@code EXPECTED_MOVEMENT-<id>}（预期银行流水生成，「钱比账单先到」）。不同来源各自登记，避免执行中再次登记被当作重复而丢失唤醒。</li>
 * <li>建议锁竞争为可重试失败；建议逻辑由 {@link SuggestBankMatchesProcessor} 负责，本 Handler 只解析身份并委托。</li>
 * <li>处理器经 {@link ObjectProvider} 在执行时取得，避免与补偿登记器的启动循环（同 ChannelStatementMatchHandler）。</li>
 */
@Component
public class BankMatchSuggestHandler implements CompensationEventHandler {
    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "BANK_MATCH_SUGGEST";

    /** 事件键前缀。 */
    static final String KEY_PREFIX = "cash-account:";

    /** 合法事件键。 */
    static final Pattern KEY = Pattern.compile("cash-account:[1-9][0-9]{0,18}");

    /** 合法业务子类型。 */
    static final Pattern TRIGGER = Pattern.compile("(BANK_STATEMENT|EXPECTED_MOVEMENT)-[1-9][0-9]{0,18}");

    private final ObjectProvider<SuggestBankMatchesProcessor> suggestBankMatchesProcessor;

    /** 以延迟取得的建议处理器构造。 */
    public BankMatchSuggestHandler(ObjectProvider<SuggestBankMatchesProcessor> suggestBankMatchesProcessor) {
        this.suggestBankMatchesProcessor = suggestBankMatchesProcessor;
    }

    /**
     * 纯工厂：银行对账单完成导入时登记
     *
     * @param cashAccountId 银行账户主键
     * @param bankStatementId 银行对账单主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildStatementEvent(long cashAccountId, long bankStatementId) {
        return new CompensationEventRequest(KEY_PREFIX + cashAccountId, EVENT_TYPE, "BANK_STATEMENT-" + bankStatementId);
    }

    /**
     * 纯工厂：预期银行流水生成时登记
     *
     * @param cashAccountId 银行账户主键
     * @param expectedMovementId 预期银行流水主键
     * @return 登记请求
     */
    public static CompensationEventRequest buildMovementEvent(long cashAccountId, long expectedMovementId) {
        return new CompensationEventRequest(KEY_PREFIX + cashAccountId, EVENT_TYPE,
            "EXPECTED_MOVEMENT-" + expectedMovementId);
    }

    /** 补偿事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 60 秒（短于补偿默认 Lease 2 分钟），最多 10 次，2 秒起指数退避至 10 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(60), 10, Duration.ofSeconds(2), Duration.ofMinutes(10),
            2, 0.2);
    }

    /**
     * 解析银行账户并出建议
     * <li>事件身份非法为终态失败；建议锁竞争或版本冲突为可重试失败。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (event.payloadJson() != null || !KEY.matcher(event.eventKey()).matches()
            || !TRIGGER.matcher(event.subEventType()).matches()) {
            return CompensationHandlerResult.terminalFailure("BANK_MATCH_SUGGEST_INVALID",
                "Bank match suggestion event is malformed");
        }
        long cashAccountId = Long.parseLong(event.eventKey().substring(KEY_PREFIX.length()));
        try {
            suggestBankMatchesProcessor.getObject().process(new SuggestBankMatchesRequest(cashAccountId));
        } catch (ConcurrentRequestException contended) {
            return CompensationHandlerResult.retryableFailure("BANK_MATCH_SUGGEST_CONTENDED",
                "Bank match suggestion is running, retry later");
        }
        return CompensationHandlerResult.success();
    }
}
