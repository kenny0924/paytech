package com.paytech.commercial.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.paytech.channel.connector.api.readiness.ChannelIdentityReadinessApi;
import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.classification.ClientBusinessClassificationApi;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.commercial.core.configuration.CommercialRelationshipSnapshotProperties;
import com.paytech.commercial.domainservice.repository.*;
import com.paytech.commercial.domainservice.service.AgreementPartyQueryService;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.commercial.domainservice.service.impl.AgreementPartyQueryServiceImpl;
import com.paytech.commercial.domainservice.service.impl.CheckoutQueryServiceImpl;
import com.paytech.commercial.integration.ChannelIdentityReadinessClient;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.commercial.integration.ClientClassificationClient;
import com.paytech.commercial.integration.ClientMasterCheckoutClient;
import com.paytech.commercial.integration.impl.ChannelIdentityReadinessClientImpl;
import com.paytech.commercial.integration.impl.ClientClassificationClientImpl;
import com.paytech.commercial.integration.impl.ClientMasterCheckoutClientImpl;
import com.paytech.commercial.integration.impl.ComplianceQualificationClientImpl;
import com.paytech.commercial.service.processor.CheckoutResolutionProcessor;
import com.paytech.compliance.api.QualificationEvaluationApi;
import com.paytech.compliance.api.enums.QualificationErrorCode;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;

/**
 * 两段式 Checkout 解析与合同参与方查询的装配：本库只读 Service、client-master 客户端、compliance 资格客户端与 Processor
 * <li>开通项前置门禁 ④ 的 client-master 经营分类与 channel-connector 渠道身份就绪客户端同处同法装配。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@EnableConfigurationProperties(CommercialRelationshipSnapshotProperties.class)
public class CheckoutResolutionConfiguration {
    /** client-master 服务发现名。 */
    static final String CLIENT_MASTER = "client-master-service";

    /** compliance 服务发现名。 */
    static final String COMPLIANCE = "compliance-service";

    /** channel-connector 服务发现名。 */
    static final String CHANNEL_CONNECTOR = "channel-connector-service";

    /** 多表加载共用同一个有界的只读事务（REPEATABLE READ，10 秒），仓储不自行创建事务。 */
    @Bean
    public CheckoutQueryService checkoutQueryService(DataSourceTransactionManager transactions,
        AgreementRepository agreementRepository, AgreementPartyRepository agreementPartyRepository,
        CommercialTermPlanRepository commercialTermPlanRepository, CommercialTermPriceRepository commercialTermPriceRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        PricingProfileRepository pricingProfileRepository,
        CommercialTermVersionRepository commercialTermVersionRepository,
        CommercialTermComponentRepository commercialTermComponentRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository,
        CommercialTermComponentBandRepository commercialTermComponentBandRepository,
        AgreementProductRepository agreementProductRepository, AgreementCapabilityRepository agreementCapabilityRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository,
        ProductCapabilityRepository productCapabilityRepository, PricingProfileCondRepository pricingProfileCondRepository) {
        TransactionTemplate reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        reads.setTimeout(10);
        return new CheckoutQueryServiceImpl(reads, agreementRepository, agreementPartyRepository,
            commercialTermPlanRepository, commercialTermPriceRepository, agreementDocumentRefRepository,
            businessChangeRecordRepository, pricingProfileRepository, commercialTermVersionRepository,
            commercialTermComponentRepository, agreementOperatingEntityRepository, commercialTermComponentBandRepository,
            agreementProductRepository, agreementCapabilityRepository, agreementCapabilityLimitRepository,
            productCapabilityRepository, pricingProfileCondRepository);
    }

    /** 合同参与方引用查询，供 client-master 人员共享标记。 */
    @Bean
    public AgreementPartyQueryService agreementPartyQueryService(AgreementPartyRepository repository) {
        return new AgreementPartyQueryServiceImpl(repository);
    }

    /**
     * client-master 两段式解析客户端：服务发现名 client-master-service，连接 2 秒、读取 3 秒，禁止重定向，不重试。
     * <li>注册 client-master 的候选错误码，使 Client 不存在、不合格与配置冲突可区分；其余失败由适配器失败关闭。</li>
     */
    @Bean
    public ClientMasterCheckoutClient clientMasterCheckoutClient(ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api = HttpServiceClientFactory.create(discoveryTransport(discovery, CLIENT_MASTER),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(1048576), tokens,
                CLIENT_MASTER),
            CheckoutCandidateErrorCode.values()).createClient(ClientMasterCheckoutApi.class);
        return new ClientMasterCheckoutClientImpl(api);
    }

    /**
     * compliance 角色资格判定客户端：服务发现名 compliance-service，连接 2 秒、读取 3 秒，禁止重定向，不重试。
     * <li>登记 compliance 的资格错误码作为合同错误；适配器对任何失败一律失败关闭为 OWNER_UNAVAILABLE。</li>
     */
    @Bean
    public CheckoutQualificationClient checkoutQualification(ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api = HttpServiceClientFactory.create(discoveryTransport(discovery, COMPLIANCE),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(262144), tokens,
                COMPLIANCE),
            QualificationErrorCode.values()).createClient(QualificationEvaluationApi.class);
        return new ComplianceQualificationClientImpl(api);
    }

    /**
     * client-master 经营分类客户端（开通项前置门禁 ④ 的主 MCC）：服务发现名 client-master-service，连接 2 秒、读取 3 秒，禁止重定向，不重试。
     * <li>不登记合同错误码：任何失败（含 Client 不存在）由适配器失败关闭为 OWNER_UNAVAILABLE。</li>
     */
    @Bean
    public ClientClassificationClient clientClassificationClient(ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api = HttpServiceClientFactory.create(discoveryTransport(discovery, CLIENT_MASTER),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), tokens,
                CLIENT_MASTER)).createClient(ClientBusinessClassificationApi.class);
        return new ClientClassificationClientImpl(api);
    }

    /**
     * channel-connector 渠道身份就绪客户端（开通项前置门禁 ④）：服务发现名 channel-connector-service，连接 2 秒、读取 3 秒，禁止重定向，不重试。
     * <li>不登记合同错误码：任何失败（含配置损坏 500）由适配器失败关闭为 OWNER_UNAVAILABLE。</li>
     */
    @Bean
    public ChannelIdentityReadinessClient channelIdentityReadinessClient(ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api = HttpServiceClientFactory.create(discoveryTransport(discovery, CHANNEL_CONNECTOR),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(262144), tokens,
                CHANNEL_CONNECTOR)).createClient(ChannelIdentityReadinessApi.class);
        return new ChannelIdentityReadinessClientImpl(api);
    }

    /** 装配候选、本库快照、事务外资格检查与复核的入口；快照开关决定响应的生效模型版本。 */
    @Bean
    public CheckoutResolutionProcessor checkoutResolution(CheckoutQueryService service, ClientMasterCheckoutClient clientMaster,
        CheckoutQualificationClient qualification, CommercialRelationshipSnapshotProperties relationshipSnapshot) {
        return new CheckoutResolutionProcessor(service, clientMaster, qualification, relationshipSnapshot);
    }

    /**
     * 私网服务发现传输：每次请求按发现名选实例。
     *
     * @param discovery 负载均衡客户端
     * @param service 服务发现名
     * @return 未附加错误边界的 RestClient
     */
    static RestClient discoveryTransport(ObjectProvider<LoadBalancerClient> discovery, String service) {
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl("http://" + service).requestFactory(transport)
            .requestInterceptor((request, body, execution) -> {
                LoadBalancerClient balancer = discovery.getObject();
                var instance = balancer.choose(service);
                if (instance == null) {
                    throw new ResourceAccessException(service + " discovery is unavailable");
                }
                return balancer.execute(service, instance,
                    new LoadBalancerRequestFactory(balancer).createRequest(request, body, execution));
            }).build();
    }
}
