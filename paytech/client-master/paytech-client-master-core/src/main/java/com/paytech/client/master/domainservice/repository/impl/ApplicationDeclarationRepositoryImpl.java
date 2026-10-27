package com.paytech.client.master.domainservice.repository.impl;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import com.paytech.client.master.core.dal.mapper.ApplicationDeclarationMapper;
import com.paytech.client.master.domainservice.repository.ApplicationDeclarationRepository;
import com.paytech.foundation.common.error.ErrorException;
import java.util.List;
import lombok.AllArgsConstructor;

/** 只通过 ApplicationDeclarationMapper 访问本表。 */
@AllArgsConstructor
public class ApplicationDeclarationRepositoryImpl implements ApplicationDeclarationRepository {
    private final ApplicationDeclarationMapper mapper;

    /** 必须恰好插入一行。 */
    @Override
    public void insert(ApplicationDeclaration row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("申请声明写入失败: " + row.getClientApplicationId());
        }
    }

    /** 按时间先后。 */
    @Override
    public List<ApplicationDeclaration> findByApplication(long applicationId) {
        return List.copyOf(mapper.findByApplication(applicationId));
    }
}
