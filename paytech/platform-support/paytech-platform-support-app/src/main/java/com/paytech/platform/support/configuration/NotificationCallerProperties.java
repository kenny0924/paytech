package com.paytech.platform.support.configuration;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 通知内部 HTTP 调用方白名单；仅在 notification.enabled=true 时绑定，变更需重启。
 * <li>过渡信任：X-Caller-Service 可在私网内伪造，白名单只把影响限制在该调用方被授权的模板</li>
 * <li>默认为空，全部调用方拒绝</li>
 */
@ConfigurationProperties("notification.http")
public class NotificationCallerProperties {

    /**
     * 调用方服务名到可用普通邮件模板编码
     * <li>服务名格式 [a-z][a-z0-9-]{0,63}，与 Header 精确比较；模板编码格式 [A-Z][A-Z0-9_]{0,63}</li>
     * <li>每个调用方至少一个模板，列表内不重复</li>
     */
    private final Map<String, Set<String>> callers;

    /** 构造时校验服务名与模板编码；任一不合法拒绝启动。 */
    public NotificationCallerProperties(Map<String, List<String>> callers) {
        this.callers = callers == null ? Map.of() : Map.copyOf(validate(callers));
    }

    /**
     * 校验调用方可以使用该模板。
     * <li>未登记调用方与越权模板返回同一提示，不暴露白名单内容</li>
     *
     * @param callerService 调用方服务名
     * @param templateCode 普通邮件模板编码
     */
    public void authorize(String callerService, String templateCode) {
        Set<String> templates = callerService == null ? null : callers.get(callerService);
        if (templates == null || !templates.contains(templateCode)) {
            throw new BizCheckFailException("调用方未获准使用该通知模板");
        }
    }

    /**
     * 拒绝把只用于即时发送的模板授权给 HTTP 调用方。
     *
     * @param transientTemplates notification.transient-template-codes
     */
    public void rejectTemplates(Collection<String> transientTemplates) {
        callers.forEach((caller, templates) -> {
            if (templates.stream().anyMatch(transientTemplates::contains)) {
                throw new IllegalArgumentException("notification.http.callers." + caller + " 不能使用即时发送模板");
            }
        });
    }

    /** 逐个调用方校验并转为不可变集合。 */
    static Map<String, Set<String>> validate(Map<String, List<String>> callers) {
        var result = new java.util.HashMap<String, Set<String>>();
        callers.forEach((caller, templates) -> result.put(caller, templates(caller, templates)));
        return result;
    }

    /** 单个调用方的模板列表：非空、格式合法、不重复。 */
    static Set<String> templates(String caller, List<String> templates) {
        if (caller == null || !caller.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("notification.http.callers 调用方服务名不合法");
        }
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("notification.http.callers." + caller + " 至少配置一个模板");
        }
        var unique = new HashSet<String>();
        for (String template : templates) {
            if (template == null || !template.matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw new IllegalArgumentException("notification.http.callers." + caller + " 模板编码不合法");
            }
            if (!unique.add(template)) {
                throw new IllegalArgumentException("notification.http.callers." + caller + " 模板编码重复");
            }
        }
        return Set.copyOf(unique);
    }
}
