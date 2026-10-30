package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionRecipientView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantPermissionRecipientRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.TenantPermissionRecipientService;

import lombok.AllArgsConstructor;

/** 只读本库一次组合查询，不开事务；角色授予以当前 UTC 微秒时刻判断是否生效。 */
@Service
@AllArgsConstructor
public class TenantPermissionRecipientServiceImpl implements TenantPermissionRecipientService {
    private final Clock clock;
    private final TenantPermissionRecipientRepository recipients;

    /** 委托组合查询。 */
    @Override
    public List<TenantPermissionRecipientView> findRecipients(AccessTenantType tenantType, long ownerRefId,
        String permissionCode, int limit) {
        return recipients.findRecipients(tenantType, ownerRefId, permissionCode,
            clock.instant().truncatedTo(ChronoUnit.MICROS), limit);
    }
}
