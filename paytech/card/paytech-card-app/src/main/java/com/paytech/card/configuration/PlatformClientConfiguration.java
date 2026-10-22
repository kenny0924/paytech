package com.paytech.card.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import com.paytech.card.api.CardApiConstants;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.platform.support.api.referencedata.CountryRegionQueryApi;

/**
 * BIN 库导入的 Platform 出站（2026-10-22 BIN 库需求 §3.5.3）：内部文件接口与国家区域映射走服务发现、负载均衡和 Foundation 公共出站边界；
 * 对象存储短时下载地址用独立的下载客户端。
 * <li>Platform 内部文件接口要求内部调用身份：始终带 X-Caller-Service: card-service（Platform DUAL 回退），internal-auth 非 OFF 时另附内部 Token。</li>
 * <li>只在 BIN 库导入任务与创建版本时调用（均在事务外）；不自动重试、不跟随重定向，失败由导入任务按执行策略重试。</li>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = PlatformClientConfiguration.SERVICE_ID,
    types = {InternalFileApi.class, CountryRegionQueryApi.class})
public class PlatformClientConfiguration {
    /** Platform 服务组名，同时是服务发现中的服务 ID。 */
    public static final String SERVICE_ID = "platform-support-service";

    /** 下载连接超时。 */
    static final Duration DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** 等待对象存储响应头的超时；正文按流读取，不受此限。 */
    static final Duration DOWNLOAD_RESPONSE_TIMEOUT = Duration.ofSeconds(10);

    /** 公共错误与 Request ID 处理；文件不存在与状态冲突作为 Platform 合同错误码保留给业务判断。 */
    @Bean
    RestClientHttpServiceGroupConfigurer platformSupportClient(InternalTokenClientOptions internalToken) {
        return HttpServiceClientFactory.configureGroup(SERVICE_ID,
            internalToken.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), SERVICE_ID),
            FileErrorCode.FILE_NOT_FOUND, FileErrorCode.FILE_STATE_CONFLICT);
    }

    /** 内部文件接口以调用方服务名识别来源（DUAL 模式回退）。 */
    @Bean
    RestClientHttpServiceGroupConfigurer platformCallerHeader() {
        return groups -> groups.filterByName(SERVICE_ID).forEachClient(
            (group, builder) -> builder.defaultHeader(CardApiConstants.CALLER_HEADER, CardApiConstants.SERVICE_ID));
    }

    /** 对象存储短时下载地址的客户端：不跟随重定向、不重试，地址不进入日志。 */
    @Bean
    RestClient fileDownloadRestClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(DOWNLOAD_CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(DOWNLOAD_RESPONSE_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
