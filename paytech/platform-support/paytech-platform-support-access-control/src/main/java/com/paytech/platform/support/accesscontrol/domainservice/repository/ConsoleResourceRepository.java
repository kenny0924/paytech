package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/** Console Resource 本表读取与条件保存，事务由 Processor 持有。 */
public interface ConsoleResourceRepository {
    /** 精确读取 Operations 资源，其他 Channel 不可见。 */
    ConsoleResource resource(String code);

    /** 读取有界 Operations 完整树，保留数据库范围保护。 */
    List<ConsoleResource> resources(int limit);

    /** 插入新资源，业务编码唯一索引为最终保护。 */
    void insert(ConsoleResource resource);

    /** 按原版本保存可变字段；成功后同步原对象。 */
    void update(ConsoleResource resource, String displayName, Long parentResourceId, String frontendRoute,
        String componentKey, long permissionId, int sortOrder, GatewayConfigurationStatus status, Instant updateTime);
}
