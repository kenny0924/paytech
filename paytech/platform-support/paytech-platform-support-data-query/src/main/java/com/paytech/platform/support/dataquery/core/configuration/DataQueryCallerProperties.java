package com.paytech.platform.support.dataquery.core.configuration;

import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 执行接口的调用方白名单（前缀 data-query.http）；变更需重启。
 * <li>过渡信任：X-Caller-Service 可在私网内伪造，携带内部 Token 时以 Token sub 为准。</li>
 * <li>默认为空，全部调用方拒绝；各环境配置 operations-console-bff。由应用配置以 EnableConfigurationProperties 注册（构造器绑定）。</li>
 */
@ConfigurationProperties(prefix = "data-query.http")
public class DataQueryCallerProperties {

    /**
     * 允许调用执行接口的服务名
     * <li>格式 [a-z][a-z0-9-]{0,63}，与调用方精确比较。</li>
     */
    private final Set<String> callers;

    /**
     * 构造时校验服务名格式；不合法拒绝启动。
     *
     * @param callers 服务名列表
     */
    public DataQueryCallerProperties(List<String> callers) {
        if (callers != null && callers.stream().anyMatch(caller -> caller == null
            || !caller.matches("[a-z][a-z0-9-]{0,63}"))) {
            throw new IllegalArgumentException("data-query.http.callers 调用方服务名不合法");
        }
        this.callers = callers == null ? Set.of() : Set.copyOf(callers);
    }

    /**
     * 校验调用方已登记。
     *
     * @param callerService 调用方服务名
     * @throws BizCheckFailException 未登记
     */
    public void authorize(String callerService) {
        if (callerService == null || !callers.contains(callerService)) {
            throw new BizCheckFailException("调用方未获准执行数据查询: " + callerService);
        }
    }
}
