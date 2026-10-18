package com.paytech.compliance.test.probe;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 捕获 Foundation HTTP 访问日志的只读 Probe
 * <li>只挂载在访问日志 Logger 上观察事件，不改变日志级别和生产输出。</li>
 * <li>用 try-with-resources 包裹，结束时卸载，避免跨用例累积。</li>
 *
 * @author Kenny
 */
public final class HttpLogProbe implements AutoCloseable {
    private final Logger logger = (Logger)LoggerFactory.getLogger(HttpRequestResponseLoggingFilter.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private HttpLogProbe() {
        appender.start();
        logger.addAppender(appender);
    }

    public static HttpLogProbe start() {
        return new HttpLogProbe();
    }

    /** 给定请求 ID 的全部访问日志事件，以键值对形式返回。 */
    public List<Map<String, String>> eventsOf(String requestId) {
        return appender.list.stream().map(HttpLogProbe::keyValues)
            .filter(values -> requestId.equals(values.get("requestId"))).toList();
    }

    /** 全部捕获事件的完整渲染文本，用于确认敏感原值从未出现。 */
    public String rendered() {
        return appender.list.stream().map(event -> event.getFormattedMessage() + " " + keyValues(event))
            .collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs() == null ? Map.of() : event.getKeyValuePairs().stream()
            .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value), (left, right) -> right));
    }
}
