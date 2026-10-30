package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.control.core.configuration.LimitTierProperties;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.domain.limittier.LimitTierEstablishContext;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierException.Reason;
import com.paytech.risk.control.domain.limittier.TierClientProfile;
import com.paytech.risk.control.domain.limittier.TierMigrationResult;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierRepository;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.repository.RiskListRepository;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.service.processor.request.MigrateClientTiersRequest;

import lombok.AllArgsConstructor;

/**
 * 存量 Client 迁移为 STANDARD（L12，内部接口）
 * <li>只接受白名单调用方（risk.limit-tier.migration-callers，默认空即关闭）。</li>
 * <li>已有档位的 Client 跳过；每个 Client 一个短事务写档位行、MIGRATION 记录与 TIER_STANDARD 成员，执行人记为申请人与建档人。</li>
 * <li>不取档位锁：只对无档位 Client 建档，并发建档由档位行 client_id 唯一索引兜底，重复执行幂等。</li>
 */
@Component
@AllArgsConstructor
public class MigrateClientTiersProcessor extends AbstractProcessor<MigrateClientTiersRequest, List<TierMigrationResult>> {
    private final Clock clock;
    private final RiskListRepository lists;
    private final LimitTierProperties properties;
    private final ClientLimitTierService service;
    private final ClientLimitTierRepository tiers;
    private final ControlIdentifierGenerator identifiers;
    private final ClientUnderwritingRepository underwritings;

    /** 调用方须在当前白名单内；只依赖输入与配置，处理前判断。 */
    @Override
    protected void validate(MigrateClientTiersRequest request) {
        if (request.getCallerService() == null || !properties.getMigrationCallers().contains(request.getCallerService())) {
            throw new LimitTierException(Reason.MIGRATION_FORBIDDEN,
                "调用方无权执行限额档位迁移: " + request.getCallerService());
        }
    }

    @Override
    protected List<TierMigrationResult> processRequest(MigrateClientTiersRequest request) {
        List<TierMigrationResult> results = new ArrayList<>();
        for (TierClientProfile client : request.getClients()) {
            results.add(migrate(client, request));
        }
        return results;
    }

    /** 迁移一个 Client：已有档位跳过，否则独立短事务建档。 */
    TierMigrationResult migrate(TierClientProfile client, MigrateClientTiersRequest request) {
        if (tiers.findByClientId(client.clientId()).isPresent()) {
            return new TierMigrationResult(client.clientId(), false);
        }
        ClientUnderwriting effective = underwritings.findEffective(client.clientId()).orElse(null);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LimitTierEstablishContext context = LimitTierCommands.establishment(identifiers, lists, client,
            LimitTierCommands.Establishment.migration(request.getReason()),
            effective == null ? null : effective.getUnderwritingId(), effective == null ? null : effective.getCrr(),
            request.getExecutedBy(), now);
        return new TierMigrationResult(client.clientId(), service.establish(context));
    }
}
