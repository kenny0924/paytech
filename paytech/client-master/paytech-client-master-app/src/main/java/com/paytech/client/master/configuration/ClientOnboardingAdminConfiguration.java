package com.paytech.client.master.configuration;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.client.master.domainservice.repository.CatalogItemRepository;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.repository.IdentifierTypeRepository;
import com.paytech.client.master.domainservice.repository.JurisdictionRepository;
import com.paytech.client.master.domainservice.repository.LocalPartnerRepository;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaFieldRepository;
import com.paytech.client.master.domainservice.repository.OnboardingSchemaRepository;
import com.paytech.client.master.domainservice.repository.OrganizationTypeRepository;
import com.paytech.client.master.domainservice.repository.PartnerOnboardingCodeRepository;
import com.paytech.client.master.domainservice.repository.PartnerRoleRepository;
import com.paytech.client.master.domainservice.repository.RelationTypeRepository;
import com.paytech.client.master.domainservice.repository.RuleSettingRepository;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.domainservice.service.OnboardingAdminQueryService;
import com.paytech.client.master.domainservice.service.OnboardingCodeService;
import com.paytech.client.master.domainservice.service.impl.OnboardingAdminQueryServiceImpl;
import com.paytech.client.master.domainservice.service.impl.OnboardingCodeServiceImpl;
import com.paytech.client.master.service.processor.GetClientAdminDetailProcessor;
import com.paytech.client.master.service.processor.IssueOnboardingCodeProcessor;
import com.paytech.client.master.service.processor.RevokeOnboardingCodeProcessor;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;

/** CM-10 / CM-11 / CM-12 运营入驻配置查看、Client 列表详情与入驻码；随草稿与补偿开关同时开启。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class ClientOnboardingAdminConfiguration {
    /** 运营只读查询。 */
    @Bean
    public OnboardingAdminQueryService onboardingAdminQueryService(ClientRepository clients, CatalogItemRepository catalogItems,
        RuleSettingRepository ruleSettings, JurisdictionRepository jurisdictions, LocalPartnerRepository localPartners,
        RelationTypeRepository relationTypes, OnboardingSchemaRepository schemas, IdentifierTypeRepository identifierTypes,
        ClientApplicationRepository applications, OrganizationTypeRepository organizationTypes,
        OnboardingSchemaFieldRepository schemaFields, PartnerOnboardingCodeRepository onboardingCodes) {
        return new OnboardingAdminQueryServiceImpl(clients, catalogItems, ruleSettings, jurisdictions, localPartners, relationTypes,
            schemas, identifierTypes, applications, organizationTypes, schemaFields, onboardingCodes);
    }

    /** Client 详情：快照在 REPEATABLE READ 只读事务内读取。 */
    @Bean
    public GetClientAdminDetailProcessor getClientAdminDetailProcessor(OnboardingAdminQueryService queries, ClientDraftService drafts,
        PlatformTransactionManager transactions) {
        return new GetClientAdminDetailProcessor(queries, drafts, ClientDraftConfiguration.reads(transactions));
    }

    /** 入驻码本地短事务。 */
    @Bean
    public OnboardingCodeService onboardingCodeService(PlatformTransactionManager transactions, PartnerOnboardingCodeRepository codes) {
        return new OnboardingCodeServiceImpl(ClientDraftConfiguration.writes(transactions), codes);
    }

    /** 签发入驻码；与 Partner 角色维护共用资源锁。 */
    @Bean
    public IssueOnboardingCodeProcessor issueOnboardingCodeProcessor(Clock clock, IdGenerator ids, RedisResourceLockProperties locks,
        OnboardingCodeService codes, PartnerRoleRepository partnerRoles, LocalPartnerRepository localPartners) {
        return new IssueOnboardingCodeProcessor(clock, ids, locks.getWaitTime(), locks.getLeaseTime(), codes, partnerRoles, localPartners);
    }

    /** 作废入驻码。 */
    @Bean
    public RevokeOnboardingCodeProcessor revokeOnboardingCodeProcessor(Clock clock, RedisResourceLockProperties locks,
        OnboardingCodeService service, PartnerOnboardingCodeRepository codes) {
        return new RevokeOnboardingCodeProcessor(clock, locks.getWaitTime(), locks.getLeaseTime(), service, codes);
    }
}
