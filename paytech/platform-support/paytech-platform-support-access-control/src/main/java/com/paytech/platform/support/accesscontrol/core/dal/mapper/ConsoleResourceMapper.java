package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/** t_console_resource 本表 SQL，禁止接收用例 Context。 */
public interface ConsoleResourceMapper {
    /** 按固定 Channel 和编码读取当前行。 */
    ConsoleResource resource(@Param("code") String code);

    /** 按主键稳定读取完整候选树。 */
    List<ConsoleResource> resources(@Param("limit") int limit);

    /** 插入本表字段，不保存权限派生信息。 */
    int insert(@Param("row") ConsoleResource row);

    /** 当前版本条件更新，不修改编码、Channel、类型和创建时间。 */
    int update(@Param("row") ConsoleResource row, @Param("displayName") String displayName,
        @Param("parentResourceId") Long parentResourceId, @Param("frontendRoute") String frontendRoute,
        @Param("componentKey") String componentKey, @Param("permissionId") long permissionId,
        @Param("sortOrder") int sortOrder, @Param("status") GatewayConfigurationStatus status,
        @Param("updateTime") Instant updateTime);
}
