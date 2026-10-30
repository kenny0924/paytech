package com.paytech.tax.app.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.InternalFileApi;

/**
 * 简化税票上传的 Platform 出站（technique 9.3、S-12）：内部文件接口走 platform-support-service 服务组（基址配置在
 * spring.http.serviceclient.platform-support-service，不接服务发现）与 Foundation 公共出站边界；对象存储直传用独立客户端。
 * <li>Platform 内部文件接口要求内部调用身份：始终带 X-Caller-Service: tax-service（Platform DUAL 回退），internal-auth 非 OFF 时另附内部 Token。</li>
 * <li>只在开票补偿任务中调用（事务外）；不自动重试、不跟随重定向，失败由补偿任务按执行策略重试。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = TaxPlatformClientConfiguration.SERVICE_ID, types = InternalFileApi.class)
public class TaxPlatformClientConfiguration {
    /** Platform 服务组名。 */
    public static final String SERVICE_ID = "platform-support-service";

    /** 本服务调用身份。 */
    static final String CALLER_SERVICE = "tax-service";

    /** Platform 过渡信任的调用方 Header。 */
    static final String CALLER_HEADER = "X-Caller-Service";

    /** 直传连接超时。 */
    static final Duration UPLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** 直传等待对象存储响应的超时。 */
    static final Duration UPLOAD_RESPONSE_TIMEOUT = Duration.ofSeconds(20);

    /** 公共错误与 Request ID 处理；文件不存在与状态冲突保留为 Platform 合同错误码。 */
    @Bean
    RestClientHttpServiceGroupConfigurer taxPlatformSupportClient(InternalTokenClientOptions internalToken) {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), SERVICE_ID),
            FileErrorCode.FILE_NOT_FOUND, FileErrorCode.FILE_STATE_CONFLICT);
    }

    /** 内部文件接口以调用方服务名识别来源（DUAL 模式回退）。 */
    @Bean
    RestClientHttpServiceGroupConfigurer taxPlatformCallerHeader() {
        return groups -> groups.filterByName(SERVICE_ID).forEachClient(
            (group, builder) -> builder.defaultHeader(CALLER_HEADER, CALLER_SERVICE));
    }

    /** 对象存储直传客户端：不跟随重定向、不重试，地址不进入日志。 */
    @Bean
    RestClient taxFileUploadRestClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(UPLOAD_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(UPLOAD_RESPONSE_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
