package com.paytech.channel.connector.protocol.core.configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
@ConfigurationProperties(prefix = "channel-connector.protocol.execution")
public class ExternalApiExecutionProperties {

    /**
     * 出站地址允许的协议
     * <li>默认只允许 https；保存渠道出站地址与每次执行前都按此检查。</li>
     * <li>只有本机 mock 配置可加入 http，指向测试服务。</li>
     */
    private Set<String> allowedSchemes = new LinkedHashSet<>(Set.of("https"));

    /**
     * card 服务的应用 context path（第 12 步读卡改由 card-service 提供，design/05 §2.1）
     * <li>读卡初始化函数 PAYMENT_CARD 的请求地址为 http://card-service{本值}/card/v1/channel-card-materials/reveal。</li>
     * <li>默认空串，与 card 应用配置一致（card 无 context path）。修改需重启。</li>
     */
    private String cardContextPath = "";

    /**
     * 支付渠道执行舱的并发上限
     * <li>同时执行（含初始化与全部原子 API 请求）的支付渠道标准 API 调用数，默认 64；拿不到许可立即返回未发出，不排队。</li>
     * <li>与服务渠道执行舱各自独立的 JDK HttpClient 配合，使服务渠道的慢调用不占用支付渠道的执行资源。修改需重启。</li>
     */
    private int paymentMaxConcurrency = 64;

    /**
     * 服务渠道执行舱的并发上限
     * <li>默认 16；拿不到许可时通用执行入口返回 503 CHANNEL_EXECUTION_BUSY，不排队。修改需重启。</li>
     */
    private int serviceMaxConcurrency = 16;

    /**
     * 出站地址放行网段（CIDR 列表）
     * <li>默认空。出站地址默认拦截回环、私网、链路本地、保留与组播地址；落在本列表内的地址放行，用于本机与测试环境的渠道 Mock。</li>
     * <li>保存出站地址时按 IP 字面量判断（localhost 视同 127.0.0.1），执行时按解析后的全部地址判断。格式错误拒绝启动，修改需重启。</li>
     */
    private List<String> trustedNetworks = new ArrayList<>();

    /** 两个执行舱的并发上限都必须为正数。 */
    @AssertTrue(message = "支付与服务渠道执行舱的并发上限必须为正整数")
    public boolean isConcurrencyValid() {
        return paymentMaxConcurrency > 0 && serviceMaxConcurrency > 0;
    }

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
