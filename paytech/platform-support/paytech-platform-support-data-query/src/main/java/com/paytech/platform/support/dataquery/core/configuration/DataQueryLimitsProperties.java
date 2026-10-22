package com.paytech.platform.support.dataquery.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import com.paytech.platform.support.dataquery.template.TemplateLimits;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据查询的系统硬上限；模板约束与请求都不能超过这些值。
 * <li>前缀 data-query.limits；修改需重启。已保存模板不会因上限调低而自动失效，执行时仍按 min(模板, 上限) 生效。</li>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "data-query.limits")
public class DataQueryLimitsProperties {

    /**
     * 单次最多返回的行数
     * <li>默认 10000；模板 maxRows、maxPageSize 不能超过它。</li>
     */
    private int maxRows = 10000;

    /**
     * 单次查询超时上限
     * <li>默认 8s：须低于 BFF（9s）与 Gateway（10s）的响应超时，否则调用方先超时而查询仍在执行；
     * 模板 timeoutSeconds 不能超过它；退役连接池最多再等待该时长加 10 秒后关闭。</li>
     */
    private Duration maxTimeout = Duration.ofSeconds(8);

    /**
     * OFFSET 分页的最大偏移行数
     * <li>默认 100000。</li>
     */
    private long maxOffset = 100000;

    /**
     * 单次结果的累计字节上限
     * <li>默认 10MB，按值的 UTF-8 字节估算；超过时中止并返回 DATA_QUERY_RESULT_TOO_LARGE。</li>
     */
    private DataSize maxResponseBytes = DataSize.ofMegabytes(10);

    /**
     * 每个数据源连接池的最大连接数上限
     * <li>默认 10。</li>
     */
    private int maxPoolSize = 10;

    /**
     * 集合参数最大元素数
     * <li>默认 1000。</li>
     */
    private int maxListSize = 1000;

    /**
     * 模板正文最大长度，按 UTF-8 字节
     * <li>默认 48KB：Gateway 请求 Body 上限为 65536 字节，保存与试运行请求还要容纳参数、列与约束。</li>
     */
    private DataSize maxBodyLength = DataSize.ofKilobytes(48);

    /**
     * 展开校验全列举的组合数上限
     * <li>默认 256；超过时改用覆盖集，保证每个分支至少出现一次。</li>
     */
    private int maxValidationCombinations = 256;

    /**
     * 转换为模板引擎使用的上限。
     *
     * @return 模板相关上限
     */
    public TemplateLimits templateLimits() {
        return new TemplateLimits(Math.toIntExact(maxBodyLength.toBytes()), maxListSize, maxValidationCombinations);
    }

    /**
     * 超时上限的整秒数。
     *
     * @return 秒数，至少 1
     */
    public int maxTimeoutSeconds() {
        return Math.toIntExact(maxTimeout.toSeconds());
    }

    /** 启动时拒绝非正值与超出 int 的大小。 */
    @AssertTrue(message = "data-query.limits 各项必须为正数，max-timeout 至少 1s，大小不超过 2GB")
    public boolean isLimitsValid() {
        return maxRows > 0 && maxTimeout != null && maxTimeout.toSeconds() >= 1 && maxOffset >= 0
            && maxResponseBytes != null && maxResponseBytes.toBytes() > 0 && maxPoolSize > 0 && maxListSize > 0
            && maxBodyLength != null && maxBodyLength.toBytes() > 0 && maxBodyLength.toBytes() <= Integer.MAX_VALUE
            && maxValidationCombinations > 0;
    }
}
