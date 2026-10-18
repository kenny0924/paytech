package com.paytech.foundation.messaging.test.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;
import com.paytech.foundation.testsupport.api.Clear;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * 记录拓扑包（{@code core.topology}）输出的 WARN 与 ERROR 日志，用于断言重声明失败按次记 WARN、连续失败到阈值只记一次 ERROR；
 * 每例清空，上下文关闭时摘除。
 */
@TestComponent
public final class TopologyLogProbe implements Clear, DisposableBean {

    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MessagingTopologyDeclarer.class.getPackageName());
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
                events.add(event);
            }
        }
    };

    /**
     * 创建并挂到拓扑包 Logger 上。
     */
    public TopologyLogProbe() {
        appender.setName("topology-log-probe");
        appender.start();
        logger.addAppender(appender);
    }

    /**
     * 已记录的 ERROR 事件。
     *
     * @return 按记录顺序
     */
    public List<ILoggingEvent> errors() {
        return at(Level.ERROR);
    }

    /**
     * 已记录的 WARN 事件。
     *
     * @return 按记录顺序
     */
    public List<ILoggingEvent> warnings() {
        return at(Level.WARN);
    }

    @Override
    public void clear() {
        events.clear();
    }

    @Override
    public void destroy() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private List<ILoggingEvent> at(Level level) {
        return events.stream().filter(event -> event.getLevel().equals(level)).toList();
    }
}
