package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.core.dal.mapper.PartnerOnboardingCodeMapper;
import com.paytech.client.master.domain.enums.OnboardingCodeStatus;
import com.paytech.client.master.domainservice.repository.PartnerOnboardingCodeRepository;
import com.paytech.foundation.common.error.ErrorException;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 PartnerOnboardingCodeMapper 访问本表。 */
@AllArgsConstructor
public class PartnerOnboardingCodeRepositoryImpl implements PartnerOnboardingCodeRepository {
    private final PartnerOnboardingCodeMapper mapper;

    /** 必须恰好插入一行。 */
    @Override
    public void insert(PartnerOnboardingCode row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("入驻码写入失败: " + row.getLocalPartnerId());
        }
    }

    /** 按码读取。 */
    @Override
    public PartnerOnboardingCode findByCode(String code) {
        return mapper.findByCode(code);
    }

    /** 按 ID 读取。 */
    @Override
    public PartnerOnboardingCode findById(long codeId) {
        return mapper.findById(codeId);
    }

    /** 新签发在前。 */
    @Override
    public List<PartnerOnboardingCode> findByPartner(long localPartnerId) {
        return List.copyOf(mapper.findByPartner(localPartnerId));
    }

    /** 条件 SQL 成功后同步使用次数与可能的 EXPIRED 状态。 */
    @Override
    public boolean consume(PartnerOnboardingCode code, Instant now) {
        if (mapper.consume(code.getCodeId(), now) != 1) {
            return false;
        }
        code.setUseCount(code.getUseCount() + 1);
        if (code.getMaxUses() != null && code.getUseCount() >= code.getMaxUses()) {
            code.setStatus(OnboardingCodeStatus.EXPIRED);
        }
        code.setUpdateTime(now);
        return true;
    }

    /** 条件 SQL 成功后同步作废信息。 */
    @Override
    public boolean revoke(PartnerOnboardingCode code, long revokedBy, Instant now) {
        if (mapper.revoke(code.getCodeId(), revokedBy, now) != 1) {
            return false;
        }
        code.setStatus(OnboardingCodeStatus.REVOKED);
        code.setRevokedBy(revokedBy);
        code.setRevokedTime(now);
        code.setUpdateTime(now);
        return true;
    }
}
