package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;
import com.paytech.platform.support.accesscontrol.domainservice.repository.GatewayConfigurationManagementRepository.PermissionIdentity;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/** Gateway Target 和 Route 管理查询、Root 锁与 Version CAS。 */
public interface GatewayConfigurationManagementMapper {

    List<GatewayTargetManagement> targets(@Param("code") String code, @Param("channel") GatewayRouteChannel channel,
        @Param("status") GatewayConfigurationStatus status, @Param("offset") int offset, @Param("size") int size);

    long targetCount(@Param("code") String code, @Param("channel") GatewayRouteChannel channel,
        @Param("status") GatewayConfigurationStatus status);

    GatewayTargetManagement target(String code);

    int updateTargetStatus(@Param("id") long id, @Param("status") GatewayConfigurationStatus status,
        @Param("version") int version, @Param("actorId") long actorId, @Param("now") Instant now);

    List<GatewayRouteManagement> routes(@Param("code") String code, @Param("channel") GatewayRouteChannel channel,
        @Param("status") GatewayConfigurationStatus status, @Param("targetCode") String targetCode, @Param("requiredScopeCode") String requiredScopeCode,
        @Param("offset") int offset, @Param("size") int size);

    long routeCount(@Param("code") String code, @Param("channel") GatewayRouteChannel channel,
        @Param("status") GatewayConfigurationStatus status, @Param("targetCode") String targetCode, @Param("requiredScopeCode") String requiredScopeCode);

    GatewayRouteManagement route(String code);

    GatewayRouteRoot routeRoot(String code);

    List<GatewayRouteHeader> headers(long routeId);

    List<GatewayRouteQueryParam> queryParameters(long routeId);

    List<GatewayGuardRule> guardRules(long routeId);

    int updateRouteStatus(@Param("id") long id, @Param("status") GatewayConfigurationStatus status,
        @Param("version") int version, @Param("actorId") long actorId, @Param("now") Instant now);

    boolean permissionActive(long permissionId);

    /** 插入已编译通过的 Target。 */
    int insertTarget(@Param("row") TargetRow row, @Param("actorId") long actorId, @Param("now") Instant now);

    /** CAS 更新服务发现名。 */
    int updateTarget(@Param("row") TargetRow row, @Param("version") int version,
        @Param("actorId") long actorId, @Param("now") Instant now);

    /** 插入已编译通过的 Route 主记录。 */
    int insertRoute(@Param("row") RouteRow row, @Param("actorId") long actorId, @Param("now") Instant now);

    /** CAS 更新配置字段，保留固定身份与状态。 */
    int updateRoute(@Param("row") RouteRow row, @Param("version") int version,
        @Param("actorId") long actorId, @Param("now") Instant now);

    /** 全量子集合替换的删除部分。 */
    void deleteHeaders(long routeId);
    void deleteQueries(long routeId);
    void deleteGuards(long routeId);

    /** 使用新主键保存独立子项，不接受请求指定数据库主键。 */
    void insertHeader(@Param("id") long id, @Param("row") HeaderRow row);
    void insertQuery(@Param("id") long id, @Param("row") QueryParameterRow row);
    void insertGuard(@Param("id") long id, @Param("row") GuardRuleRow row);

    /** 权限行共享锁在配置写入之前取得。 */
    PermissionIdentity permissionIdentity(long id);
}
