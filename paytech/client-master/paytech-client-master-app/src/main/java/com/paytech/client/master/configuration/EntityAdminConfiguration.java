package com.paytech.client.master.configuration;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.EntityAdminQueryService;
import com.paytech.client.master.domainservice.service.EntityAdminService;
import com.paytech.client.master.domainservice.service.impl.EntityAdminQueryServiceImpl;
import com.paytech.client.master.domainservice.service.impl.EntityAdminServiceImpl;
import com.paytech.client.master.service.processor.ChangeAcquiringQualificationProcessor;
import com.paytech.client.master.service.processor.ReplacePartnerRolesProcessor;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;

/**
 * 运营维护 OE 与 Local Partner 主档属性的装配（client-master Admin API 1—7）
 * <li>写入需要 Snowflake ID 与 Redis 资源锁，二者随草稿与补偿开关开启，因此与审核分类 Admin API 同条件注册；
 * 不挂 Risk 审核开关。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class EntityAdminConfiguration {
    /** 运营读取，不开事务。 */
    @Bean
    public EntityAdminQueryService entityAdminQueryService(ClientRepository clientRepository,
        PartnerRoleRepository partnerRoleRepository, LocalPartnerRepository localPartnerRepository,
        OperatingEntityRepository operatingEntityRepository, SellerDisclosureRepository sellerDisclosureRepository,
        EntitySourceRelationRepository entitySourceRelationRepository,
        OperatingEntityScopeRepository operatingEntityScopeRepository) {
        return new EntityAdminQueryServiceImpl(clientRepository, partnerRoleRepository, localPartnerRepository,
            operatingEntityRepository, sellerDisclosureRepository, entitySourceRelationRepository,
            operatingEntityScopeRepository);
    }

    /** 本地原子保存：READ_COMMITTED、5 秒，与入驻写入一致。 */
    @Bean
    public EntityAdminService entityAdminService(PlatformTransactionManager manager, PartyRoleRepository partyRoleRepository,
        PartnerRoleRepository partnerRoleRepository, OperatingEntityRepository operatingEntityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        BusinessChangeVersionRepository businessChangeVersionRepository) {
        return new EntityAdminServiceImpl(ClientSubmissionConfiguration.writes(manager), partyRoleRepository,
            partnerRoleRepository, operatingEntityRepository, businessChangeRecordRepository, businessChangeVersionRepository);
    }

    /** Admin API 3。 */
    @Bean
    public ChangeAcquiringQualificationProcessor changeAcquiringQualificationProcessor(Clock clock, IdGenerator ids,
        RedisResourceLockProperties locks, EntityAdminService entityAdminService, EntityAdminQueryService entityAdminQueryService,
        OperatingEntityRepository operatingEntityRepository, BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new ChangeAcquiringQualificationProcessor(clock, ids, locks.getWaitTime(), locks.getLeaseTime(), entityAdminService,
            entityAdminQueryService, operatingEntityRepository, businessChangeRecordRepository);
    }

    /** Admin API 6。 */
    @Bean
    public ReplacePartnerRolesProcessor replacePartnerRolesProcessor(Clock clock, IdGenerator ids,
        RedisResourceLockProperties locks, EntityAdminService entityAdminService, PartnerRoleRepository partnerRoleRepository,
        LocalPartnerRepository localPartnerRepository, EntityAdminQueryService entityAdminQueryService,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        EntitySourceRelationRepository entitySourceRelationRepository) {
        return new ReplacePartnerRolesProcessor(clock, ids, locks.getWaitTime(), locks.getLeaseTime(), entityAdminService,
            partnerRoleRepository, localPartnerRepository, entityAdminQueryService, businessChangeRecordRepository,
            entitySourceRelationRepository);
    }
}
