package com.paytech.platform.support.localization.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 多语言写入口的资源锁与导入文件参数（localization.*）；启动时读取，修改需重启。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("localization")
public class LocalizationProperties {

    /**
     * TRY_LOCK 等待时间
     * <li>localization:application:{编码} 与 localization:locale:{代码} 共用；默认 500ms，超时返回 409。</li>
     */
    @NotNull
    private Duration lockWaitTime = Duration.ofMillis(500);

    /**
     * 资源锁租约
     * <li>发布在锁内生成并上传语言包，默认 120s；版本 CAS、修订号 CAS 与应用行锁仍为最终保护。</li>
     */
    @NotNull
    private Duration lockLeaseTime = Duration.ofSeconds(120);

    /**
     * 导入文件最大字节数
     * <li>key 导入与翻译导入共用，默认 2 MiB，与文件上传原因码的大小上限一致（技术方案 3.5）。</li>
     */
    @Min(1)
    @Max(16 * 1024 * 1024)
    private long importMaxFileBytes = 2L * 1024 * 1024;
}
