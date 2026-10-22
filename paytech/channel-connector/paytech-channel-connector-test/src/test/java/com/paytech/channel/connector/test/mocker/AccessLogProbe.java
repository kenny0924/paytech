package com.paytech.channel.connector.test.mocker;

import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.protocol.domainservice.execution.AtomicApiExecutor;
import com.paytech.foundation.testsupport.api.Clear;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 对外调用访问日志（logger {@value AtomicApiExecutor#ACCESS_LOGGER}）的只读观察：测试上下文内挂一个内存 Appender
 * <li>只记录格式化后的日志行，供断言字段齐全且不含报文、URL 与凭据；每例结束清空。</li>
 */
@TestComponent
public class AccessLogProbe implements Clear {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    /** 挂到访问日志 logger。 */
    public AccessLogProbe() {
        Logger logger = (Logger)LoggerFactory.getLogger(AtomicApiExecutor.ACCESS_LOGGER);
        appender.start();
        logger.addAppender(appender);
    }

    /** 本例记录的访问日志行，按时间顺序。 */
    public synchronized List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** 每例结束清空记录。 */
    @Override
    public synchronized void clear() {
        appender.list.clear();
    }
}
