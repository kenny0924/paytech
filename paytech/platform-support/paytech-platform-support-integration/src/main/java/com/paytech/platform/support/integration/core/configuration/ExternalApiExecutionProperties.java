package com.paytech.platform.support.integration.core.configuration;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;

import lombok.Getter;
import lombok.Setter;

/** 外部 API 执行的出站约束（研发计划第 3 步评审点 G5）。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ps.integration.execution")
public class ExternalApiExecutionProperties {

    /**
     * 出站地址允许的协议
     * <li>默认只允许 https；保存接入方出站地址与每次执行前都按此检查。</li>
     * <li>只有本机 mock 配置可加入 http，指向测试服务。</li>
     */
    private Set<String> allowedSchemes = new LinkedHashSet<>(Set.of("https"));

    /**
     * card 服务的应用 context path（第 12 步读卡改由 card-service 提供，design/05 §2.1）
     * <li>读卡初始化函数 PAYMENT_CARD 的请求地址为 http://card-service{本值}/card/v1/channel-card-materials/reveal。</li>
     * <li>默认空串，与 card 应用配置一致（card 无 context path）。修改需重启。</li>
     */
    private String cardContextPath = "";

    /** context path 为空串或以 / 开头、不以 / 结尾的固定路径。 */
    @AssertTrue(message = "cardContextPath 必须为空串或以 / 开头且不以 / 结尾")
    public boolean isCardContextPathValid() {
        return cardContextPath != null && (cardContextPath.isEmpty()
            || cardContextPath.matches("/[A-Za-z0-9._~-]+(/[A-Za-z0-9._~-]+)*"));
    }

    /** 只接受 https 与 http。 */
    @AssertTrue(message = "出站协议只能配置 https 或 http，且不能为空")
    public boolean isAllowedSchemesValid() {
        return allowedSchemes != null && !allowedSchemes.isEmpty()
            && Set.of("https", "http").containsAll(allowedSchemes);
    }
}
