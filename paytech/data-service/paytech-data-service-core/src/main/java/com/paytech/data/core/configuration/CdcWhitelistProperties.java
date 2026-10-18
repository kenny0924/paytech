package com.paytech.data.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CDC 白名单目录配置。
 *
 * @author Claude
 */
@ConfigurationProperties("data-service.cdc")
public class CdcWhitelistProperties {

    /**
     * 白名单契约文件位置。
     * <li>Spring Resource 写法，正式应用为 {@code classpath:cdc/whitelist.yaml}（构建期从 {@code paytech/data-service/cdc} 打包）</li>
     * <li>必填；缺失时启动失败，不提供默认值</li>
     * <li>启动时读取一次，修改后需重启</li>
     */
    private final String whitelistLocation;

    /**
     * 绑定配置。
     *
     * @param whitelistLocation 白名单契约文件位置
     */
    public CdcWhitelistProperties(String whitelistLocation) {
        this.whitelistLocation = whitelistLocation;
    }

    /**
     * 返回白名单契约文件位置。
     *
     * @return 文件位置
     */
    public String getWhitelistLocation() {
        return whitelistLocation;
    }
}
