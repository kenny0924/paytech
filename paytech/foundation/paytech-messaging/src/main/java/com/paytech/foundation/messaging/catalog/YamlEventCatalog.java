package com.paytech.foundation.messaging.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.EventCatalog;
import com.paytech.foundation.messaging.api.EventCatalogEntry;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * 读取并校验 YAML 事件目录（design/08 §5，文件 {@code paytech/foundation/paytech-messaging/catalog/events.yaml}）。
 * <li>eventType 唯一且符合格式；producer、consumers 必须是 01 §3 的服务名；consumers 不含 producer 且不重复。</li>
 * <li>strictOrder 必须是 YAML 布尔值；schemaVersion 为正整数；其余字段必填。</li>
 *
 * @author Claude
 */
public final class YamlEventCatalog implements EventCatalog {

    /** 打包进 Jar 的目录位置。 */
    public static final String CLASSPATH_LOCATION = "catalog/events.yaml";

    /** 01 §3 的服务名清单。 */
    public static final Set<String> SERVICE_NAMES = Set.of("acquiring", "consumer", "payment", "channel-connector",
        "card", "client-master", "commercial", "risk-decision", "compliance", "billing", "account", "ledger",
        "reconciliation", "tax", "payout", "dispute", "fx", "platform-support", "workflow-approval",
        "api-gateway", "client-console-bff", "operations-console-bff", "vault");

    private static final Set<String> FIELDS = Set.of("eventType", "schemaVersion", "producer", "aggregateType",
        "aggregateVersionField", "consumers", "strictOrder", "payloadSchemaRef");

    private static final Pattern FIELD_NAME = Pattern.compile("[a-z][A-Za-z0-9_]{0,63}");

    private final Map<String, EventCatalogEntry> entries;

    private YamlEventCatalog(Map<String, EventCatalogEntry> entries) {
        this.entries = entries;
    }

    /**
     * 从类路径读取 {@value #CLASSPATH_LOCATION}。
     *
     * @return 已校验的目录
     * @throws IllegalStateException 文件缺失或任一登记不合法
     */
    public static YamlEventCatalog fromClasspath() {
        try (InputStream input = YamlEventCatalog.class.getClassLoader().getResourceAsStream(CLASSPATH_LOCATION)) {
            if (input == null) {
                throw new IllegalStateException("事件目录不存在: classpath:" + CLASSPATH_LOCATION);
            }
            return read(input, SERVICE_NAMES);
        } catch (IOException exception) {
            throw new IllegalStateException("事件目录读取失败: classpath:" + CLASSPATH_LOCATION, exception);
        }
    }

    /**
     * 读取并校验一份目录。
     *
     * @param input YAML 内容
     * @param serviceNames 允许出现在 producer / consumers 中的服务名；生产目录使用 {@link #SERVICE_NAMES}
     * @return 已校验的目录
     * @throws IllegalStateException YAML 不合法或任一登记不合法
     */
    public static YamlEventCatalog read(InputStream input, Set<String> serviceNames) {
        JsonNode root;
        try {
            root = YAMLMapper.builder().build().readTree(input);
        } catch (JacksonException exception) {
            throw new IllegalStateException("事件目录不是合法 YAML", exception);
        }
        JsonNode events = root == null ? null : root.get("events");
        if (events == null || !events.isArray()) {
            throw new IllegalStateException("事件目录必须包含 events 列表");
        }
        Map<String, EventCatalogEntry> entries = new LinkedHashMap<>();
        for (int index = 0; index < events.size(); index++) {
            EventCatalogEntry entry = entry(events.get(index), "events[" + index + "]", serviceNames);
            if (entries.putIfAbsent(entry.eventType(), entry) != null) {
                throw new IllegalStateException("事件目录 eventType 重复: " + entry.eventType());
            }
        }
        return new YamlEventCatalog(entries);
    }

    @Override
    public Optional<EventCatalogEntry> find(String eventType) {
        return Optional.ofNullable(entries.get(eventType));
    }

    @Override
    public List<EventCatalogEntry> entries() {
        return List.copyOf(entries.values());
    }

    private static EventCatalogEntry entry(JsonNode node, String path, Set<String> serviceNames) {
        if (node == null || !node.isObject()) {
            throw invalid(path, "必须是对象");
        }
        for (String field : node.propertyNames()) {
            if (!FIELDS.contains(field)) {
                throw invalid(path, "未知字段 " + field);
            }
        }
        String eventType = text(node, "eventType", path);
        if (!AggregateKey.EVENT_TYPE.matcher(eventType).matches()) {
            throw invalid(path, "eventType 格式非法: " + eventType);
        }
        String producer = service(text(node, "producer", path), path, serviceNames);
        String aggregateType = text(node, "aggregateType", path);
        if (!AggregateKey.AGGREGATE_TYPE.matcher(aggregateType).matches()) {
            throw invalid(path, "aggregateType 格式非法: " + aggregateType);
        }
        String versionField = text(node, "aggregateVersionField", path);
        if (!FIELD_NAME.matcher(versionField).matches()) {
            throw invalid(path, "aggregateVersionField 格式非法: " + versionField);
        }
        return new EventCatalogEntry(eventType, schemaVersion(node, path), producer, aggregateType, versionField,
            consumers(node, producer, path, serviceNames), strictOrder(node, path), text(node, "payloadSchemaRef", path));
    }

    private static int schemaVersion(JsonNode node, String path) {
        JsonNode value = node.get("schemaVersion");
        if (value == null || !value.isInt() || value.intValue() < 1) {
            throw invalid(path, "schemaVersion 必须是正整数");
        }
        return value.intValue();
    }

    private static boolean strictOrder(JsonNode node, String path) {
        JsonNode value = node.get("strictOrder");
        if (value == null || !value.isBoolean()) {
            throw invalid(path, "strictOrder 必须是布尔值");
        }
        return value.booleanValue();
    }

    private static List<String> consumers(JsonNode node, String producer, String path, Set<String> serviceNames) {
        JsonNode value = node.get("consumers");
        if (value == null || !value.isArray()) {
            throw invalid(path, "consumers 必须是列表");
        }
        List<String> consumers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isString()) {
                throw invalid(path, "consumers 只能包含服务名");
            }
            String consumer = service(item.asString(), path, serviceNames);
            if (consumer.equals(producer) || !seen.add(consumer)) {
                throw invalid(path, "consumers 重复或包含 producer: " + consumer);
            }
            consumers.add(consumer);
        }
        return consumers;
    }

    private static String service(String name, String path, Set<String> serviceNames) {
        if (!serviceNames.contains(name)) {
            throw invalid(path, "不是 01 §3 的服务名: " + name);
        }
        return name;
    }

    private static String text(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw invalid(path, field + " 必须是非空字符串");
        }
        return value.asString();
    }

    private static IllegalStateException invalid(String path, String reason) {
        return new IllegalStateException("事件目录 " + path + " 不合法: " + reason);
    }
}
