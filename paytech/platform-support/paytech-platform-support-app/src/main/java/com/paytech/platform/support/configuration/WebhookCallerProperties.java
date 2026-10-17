package com.paytech.platform.support.configuration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.api.enums.WebhookEventType;

/**
 * Webhook 事件内部 HTTP 调用方白名单；仅在 webhook.enabled=true 时绑定，变更需重启。
 * <li>过渡信任：X-Caller-Service 可在私网内伪造，白名单只把影响限制在该调用方被授权的事件类型</li>
 * <li>默认为空，全部调用方拒绝</li>
 */
@ConfigurationProperties("webhook.http")
public class WebhookCallerProperties {

    /**
     * 调用方服务名到可提交的事件类型
     * <li>服务名格式 [a-z][a-z0-9-]{0,63}，与 Header 精确比较；事件类型只能是可订阅的 V1 类型</li>
     * <li>每个调用方至少一个事件类型，列表内不重复</li>
     */
    private final Map<String, Set<String>> callers;

    /** 构造时校验服务名与事件类型；任一不合法拒绝启动。 */
    public WebhookCallerProperties(Map<String, List<String>> callers) {
        this.callers = callers == null ? Map.of() : Map.copyOf(validate(callers));
    }

    /**
     * 校验调用方可以提交该事件类型
     * <li>未登记调用方与越权事件类型返回同一提示，不暴露白名单内容</li>
     *
     * @param callerService 调用方服务名
     * @param eventType envelope.eventType
     */
    public void authorize(String callerService, String eventType) {
        Set<String> eventTypes = callerService == null ? null : callers.get(callerService);
        if (eventTypes == null || eventType == null || !eventTypes.contains(eventType)) {
            throw new BizCheckFailException("调用方未获准提交该 Webhook 事件类型");
        }
    }

    /** 逐个调用方校验。 */
    static Map<String, Set<String>> validate(Map<String, List<String>> callers) {
        var result = new HashMap<String, Set<String>>();
        callers.forEach((caller, eventTypes) -> result.put(caller, eventTypes(caller, eventTypes)));
        return result;
    }

    /** 服务名与事件类型格式；列表非空且不重复。 */
    static Set<String> eventTypes(String caller, List<String> eventTypes) {
        if (caller == null || !caller.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("webhook.http.callers 服务名不合法：" + caller);
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("webhook.http.callers." + caller + " 至少需要一个事件类型");
        }
        var distinct = new HashSet<String>();
        for (String eventType : eventTypes) {
            var type = WebhookEventType.fromCode(eventType);
            if (type == null || !type.isSubscribable() || !distinct.add(eventType)) {
                throw new IllegalArgumentException("webhook.http.callers." + caller + " 事件类型不合法或重复：" + eventType);
            }
        }
        return Set.copyOf(distinct);
    }
}
