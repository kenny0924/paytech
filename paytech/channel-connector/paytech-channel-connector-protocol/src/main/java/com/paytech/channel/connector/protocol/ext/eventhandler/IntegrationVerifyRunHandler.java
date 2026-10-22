package com.paytech.channel.connector.protocol.ext.eventhandler;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.channel.connector.protocol.domainservice.service.impl.ChannelVerifyRunServiceImpl;
import com.paytech.channel.connector.protocol.service.processor.ExecuteVerifyRunProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteVerifyRunRequest;

import lombok.AllArgsConstructor;

/**
 * 沙箱验证运行的补偿任务入口（沙箱验证技术方案第 3.6 节）：事件键为运行标识，在事务外委托执行 Processor。
 *
 * <p>
 * 最多执行 2 次：第 1 次真正执行；第 2 次只会在前一次中断（进程退出、超时）后出现，Processor 把运行记为 INTERRUPTED，
 * 不向沙箱重放。超时 110 秒，小于默认 2 分钟 Lease；单次运行总时长应在此之内，超出即中断。
 */
@Component
@AllArgsConstructor
public class IntegrationVerifyRunHandler implements CompensationEventHandler {

    private final ExecuteVerifyRunProcessor processor;

    /** 固定持久化类型。 */
    @Override
    public String eventType() {
        return ChannelVerifyRunServiceImpl.EVENT_TYPE;
    }

    /** 不重试：第二次执行只用于把中断的运行收尾。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(110), 2, Duration.ofSeconds(1), Duration.ofSeconds(1),
            1.0, 0.0);
    }

    /** 事件键必须是正整数运行标识；执行失败（如数据库不可用）留待第二次执行收尾。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || !event.eventKey().matches("[1-9][0-9]{0,18}") || event.payloadJson() != null) {
            return CompensationHandlerResult.terminalFailure("VERIFY_RUN_IDENTITY_INVALID",
                "Verify run identity requires investigation");
        }
        try {
            processor.process(new ExecuteVerifyRunRequest(Long.parseLong(event.eventKey()), event.executeCount()));
            return CompensationHandlerResult.success();
        } catch (RuntimeException unavailable) {
            return CompensationHandlerResult.retryableFailure("VERIFY_RUN_UNAVAILABLE", "Verify run requires recovery");
        }
    }
}
