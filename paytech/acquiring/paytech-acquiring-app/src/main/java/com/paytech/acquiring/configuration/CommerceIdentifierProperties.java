package com.paytech.acquiring.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Commerce 实例 ID 节点配置。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "acquiring.identifier")
public class CommerceIdentifierProperties {

    /**
     * Snowflake 区域编号。
     * <li>必填，范围 0～31；由实例部署环境提供，修改后需重启。</li>
     */
    @NotNull
    private Long regionId;

    /**
     * Snowflake 节点编号。
     * <li>必填，范围 0～31；同一区域内每个实例唯一，修改后需重启。</li>
     */
    @NotNull
    private Long workerId;
}
