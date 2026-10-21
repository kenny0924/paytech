package com.paytech.channel.connector.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;

/**
 * channel-connector 公开错误码到 HTTP 状态的最小映射
 * <li>公共 NOT_FOUND 返回 404：自 Platform 迁入的外部 API 管理接口以此表示接入方、原子 API、脚本、实现、标准 API、验证账号或运行不存在，
 * 与原 Platform 错误映射的合同一致，Operations BFF 的外部 API 边界依赖该状态。</li>
 * <li>渠道配置既有接口以业务校验失败表示对象不存在，不经此映射，状态保持 400。</li>
 */
@Configuration(proxyBeanMethods = false)
public class ChannelConnectorApiErrorConfiguration {

    @Bean
    ApiErrorStatusResolver channelConnectorApiErrorStatusResolver() {
        return (errorCode, defaultStatus) -> errorCode == CommonErrorCode.NOT_FOUND ? HttpStatus.NOT_FOUND
            : defaultStatus;
    }
}
