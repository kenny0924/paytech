package com.paytech.client.master.domainservice.repository.impl;

import java.time.Instant;

import com.paytech.client.master.core.dal.dataobject.PartyRole;
import com.paytech.client.master.core.dal.mapper.PartyRoleMapper;
import com.paytech.client.master.domain.enums.PartyRoleCode;
import com.paytech.client.master.domainservice.repository.PartyRoleRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 PartyRoleMapper 写 t_party_role。 */
@AllArgsConstructor
public class PartyRoleRepositoryImpl implements PartyRoleRepository {
    private final PartyRoleMapper mapper;

    /** 插入一条 ACTIVE 角色索引行。 */
    @Override
    public void insert(PartyRole role) {
        mapper.insert(role);
    }

    /** 0 行视为已结束，多于 1 行为系统异常。 */
    @Override
    public void endActive(PartyRoleCode roleCode, String subjectId, Instant now) {
        if (mapper.endActive(roleCode, subjectId, now) > 1) {
            throw new ErrorException("法律主体角色索引存在多条有效行: " + roleCode.getCode() + ":" + subjectId);
        }
    }
}
