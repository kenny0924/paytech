package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPersonnel;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsPersonnelCandidate;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsPersonnelInfo;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPersonnelRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsPersonnelService;

import lombok.AllArgsConstructor;

/** 显示名取登录名，没有时取脱敏邮箱（Q11）。 */
@Service
@AllArgsConstructor
public class OperationsPersonnelServiceImpl implements OperationsPersonnelService {

    private final OperationsPersonnelRepository repository;

    /** 批量读取并转换为领域结果。 */
    @Override
    public Map<Long, OperationsPersonnelInfo> personnel(Collection<Long> accountIds) {
        Map<Long, OperationsPersonnelInfo> result = new LinkedHashMap<>();
        for (OperationsPersonnel row : repository.findByIds(accountIds)) {
            result.put(row.getAccountId(), new OperationsPersonnelInfo(row.getAccountId(), displayName(row),
                Boolean.TRUE.equals(row.getActive()), row.getEmailVaultToken()));
        }
        return result;
    }

    /** 选人查询并转换为领域结果。 */
    @Override
    public List<OperationsPersonnelCandidate> searchActive(String fragment, String emailToken, int limit) {
        return repository.searchActive(fragment, emailToken, limit).stream()
            .map(row -> new OperationsPersonnelCandidate(row.getAccountId(), row.getLoginName(), row.getEmailMasked(),
                displayName(row)))
            .toList();
    }

    /** 显示名：登录名，没有时为脱敏邮箱（Q11）。 */
    static String displayName(OperationsPersonnel row) {
        return row.getLoginName() != null ? row.getLoginName() : row.getEmailMasked();
    }
}
