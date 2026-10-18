package com.paytech.platform.support.delivery.domain;

import java.util.LinkedHashSet;
import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.enums.WebhookEventType;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 端点订阅事件类型的校验与 JSON 数组读写。 */
public final class WebhookEventTypes {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 只承载静态方法。 */
    private WebhookEventTypes() {
    }

    /**
     * 校验订阅列表：非空、不重复，只能是可订阅的 V1 事件类型
     *
     * @param eventTypes 请求中的事件类型
     * @return 保持请求顺序的列表
     */
    public static List<String> validate(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new InvalidParameterException("eventTypes 不能为空");
        }
        var distinct = new LinkedHashSet<String>();
        for (String eventType : eventTypes) {
            var type = WebhookEventType.fromCode(eventType);
            if (type == null || !type.isSubscribable()) {
                throw new InvalidParameterException("eventTypes 只能是 V1 公开事件类型");
            }
            if (!distinct.add(eventType)) {
                throw new InvalidParameterException("eventTypes 不能重复");
            }
        }
        return List.copyOf(distinct);
    }

    /** 保存用 JSON 数组。 */
    public static String format(List<String> eventTypes) {
        return JSON.writeValueAsString(eventTypes);
    }

    /** 读取已保存的 JSON 数组；格式不符说明数据被破坏。 */
    public static List<String> parse(String json) {
        try {
            return List.copyOf(JSON.readValue(json, new TypeReference<List<String>>() {}));
        } catch (JacksonException | NullPointerException malformed) {
            throw new ErrorException("Webhook 端点订阅数据不合法", malformed);
        }
    }
}
