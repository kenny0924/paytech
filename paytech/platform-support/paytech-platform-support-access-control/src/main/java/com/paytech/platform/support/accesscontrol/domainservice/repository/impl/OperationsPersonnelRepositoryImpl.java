package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPersonnel;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsPersonnelMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsPersonnelRepository;

import lombok.AllArgsConstructor;

/** OPERATIONS 人员账号的窄查询实现。 */
@Repository
@AllArgsConstructor
public class OperationsPersonnelRepositoryImpl implements OperationsPersonnelRepository {

    private final OperationsPersonnelMapper mapper;

    /** 按账号批量读取。 */
    @Override
    public List<OperationsPersonnel> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 选人查询；关键字中的 LIKE 通配符按字面匹配。 */
    @Override
    public List<OperationsPersonnel> searchActive(String fragment, String emailToken, int limit) {
        String escaped = fragment == null ? null
            : fragment.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return mapper.searchActive(escaped, emailToken, limit);
    }
}
