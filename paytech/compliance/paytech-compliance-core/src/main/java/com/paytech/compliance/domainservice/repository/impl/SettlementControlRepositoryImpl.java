package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.mapper.SettlementControlMapper;
import com.paytech.compliance.core.enums.QualificationRoleType;
import com.paytech.compliance.domainservice.repository.SettlementControlRepository;

import lombok.AllArgsConstructor;

/** t_settlement_control 单表只读访问。 */
@Repository
@AllArgsConstructor
public class SettlementControlRepositoryImpl implements SettlementControlRepository {
    private final SettlementControlMapper mapper;

    /** 给定 Client 中存在生效中结算冻结的 Client ID。 */
    @Override
    public Set<Long> findBlockedClientIds(Collection<Long> clientIds, Instant now) {
        if (clientIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(mapper.findBlockedRoleIds(QualificationRoleType.CLIENT.getCode(), clientIds, now));
    }
}
