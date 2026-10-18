package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;

import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteManagement;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteRoot;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayTargetManagement;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.RouteDetails;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.RouteQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.TargetQuery;

/** Gateway Target 和 Route 当前配置的管理仓储。 */
public interface GatewayConfigurationManagementRepository {

    List<GatewayTargetManagement> targets(TargetQuery query, int offset, int size);

    long targetCount(TargetQuery query);

    GatewayTargetManagement target(String code);

    int updateTargetStatus(long id, GatewayConfigurationStatus status, int version, long actorId, Instant now);

    List<GatewayRouteManagement> routes(RouteQuery query, int offset, int size);

    long routeCount(RouteQuery query);

    RouteDetails route(String code);

    GatewayRouteRoot routeRoot(String code);

    int updateRouteStatus(long id, GatewayConfigurationStatus status, int version, long actorId, Instant now);

    boolean permissionActive(long permissionId);

    /** 新建或 CAS 更新 Target 主记录。 */
    int insertTarget(TargetRow row, long actorId, Instant now);

    /** 保留 Target 固定身份、状态及创建信息。 */
    int updateTarget(TargetRow row, int version, long actorId, Instant now);

    /** 新建完整 Route 的主记录，子项由同一事务保存。 */
    int insertRoute(RouteRow row, long actorId, Instant now);

    /** 完整配置 CAS 更新，状态不改变。 */
    int updateRoute(RouteRow row, int version, long actorId, Instant now);

    /** 替换三个子集合；不允许独立于主记录事务调用。 */
    void replaceRouteChildren(long routeId, List<HeaderRow> headers, List<QueryParameterRow> queries,
        List<GuardRuleRow> guards);

    /** 在绑定之前锁定权限身份，防止并发停用越过校验。 */
    PermissionIdentity permissionIdentity(long id);

    /** 权限当前身份与启用事实，不包含持有人。 */
    record PermissionIdentity(long permissionId, String permissionCode, boolean active) {}
}
