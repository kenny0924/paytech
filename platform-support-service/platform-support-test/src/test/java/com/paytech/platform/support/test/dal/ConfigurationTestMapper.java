package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 网关、控制台资源与 API Scope 配置的测试数据准备和只读检查。 */
public interface ConfigurationTestMapper {

    /** 插入网关目标服务。 */
    @Insert("""
        INSERT INTO t_gateway_target(target_id,target_service_code,discovery_service_name,channel,status,version,
            created_by,updated_by,create_time,update_time)
        VALUES(#{id},#{code},#{discovery},#{channel},#{status},1,1,1,#{now},#{now})
        """)
    int insertTarget(@Param("id") long id, @Param("code") String code, @Param("discovery") String discovery,
        @Param("channel") String channel, @Param("status") String status, @Param("now") Instant now);

    /** 插入网关路由。 */
    @Insert("""
        INSERT INTO t_gateway_route(route_id,route_code,channel,http_method,external_path_template,
            downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,
            required_scope_code,request_cookie_mode,response_cookie_mode,body_limit_bytes,body_receive_timeout_millis,
            response_timeout_millis,status,version,created_by,updated_by,create_time,update_time)
        VALUES(#{r.id},#{r.code},#{r.channel},#{r.method},#{r.externalPath},#{r.downstreamPath},#{r.targetId},
            #{r.securityProfile},#{r.authorizationMode},#{r.permissionId},#{r.scopeCode},#{r.requestCookieMode},
            #{r.responseCookieMode},#{r.bodyLimit},5000,10000,#{r.status},1,1,1,#{now},#{now})
        """)
    int insertRoute(@Param("r") Route route, @Param("now") Instant now);

    /** 插入路由 Header 白名单。 */
    @Insert("""
        INSERT INTO t_gateway_route_header(route_header_id,route_id,header_name,header_use)
        VALUES(#{id},#{routeId},#{name},#{use})
        """)
    int insertRouteHeader(@Param("id") long id, @Param("routeId") long routeId, @Param("name") String name,
        @Param("use") String use);

    /** 插入路由 Query 参数白名单。 */
    @Insert("""
        INSERT INTO t_gateway_route_query_param(route_query_param_id,route_id,parameter_name)
        VALUES(#{id},#{routeId},#{name})
        """)
    int insertRouteQuery(@Param("id") long id, @Param("routeId") long routeId, @Param("name") String name);

    /** 插入路由限流或并发规则。 */
    @Insert("""
        INSERT INTO t_gateway_guard_rule(guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,
            refill_period_millis,concurrency_limit)
        VALUES(#{id},#{routeId},#{type},#{dimension},#{capacity},#{refill},#{period},#{concurrency})
        """)
    int insertGuard(@Param("id") long id, @Param("routeId") long routeId, @Param("type") String type,
        @Param("dimension") String dimension, @Param("capacity") Long capacity, @Param("refill") Long refill,
        @Param("period") Long period, @Param("concurrency") Long concurrency);

    /** 插入控制台资源。 */
    @Insert("""
        INSERT INTO t_console_resource(resource_id,resource_code,channel,resource_type,parent_resource_id,
            display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time)
        VALUES(#{id},#{code},#{channel},#{type},#{parentId},#{name},#{route},#{component},#{permissionId},#{sort},
            #{status},1,#{now},#{now})
        """)
    int insertResource(@Param("id") long id, @Param("code") String code, @Param("channel") String channel,
        @Param("type") String type, @Param("parentId") Long parentId, @Param("name") String name,
        @Param("route") String route, @Param("component") String component, @Param("permissionId") long permissionId,
        @Param("sort") int sort, @Param("status") String status, @Param("now") Instant now);

    /** 插入 Client API Scope。 */
    @Insert("""
        INSERT INTO t_api_scope(api_scope_id,scope_code,display_name,description,version,create_time,update_time)
        VALUES(#{id},#{code},#{name},#{description},1,#{now},#{now})
        """)
    int insertApiScope(@Param("id") long id, @Param("code") String code, @Param("name") String name,
        @Param("description") String description, @Param("now") Instant now);

    /** 修改路由状态。 */
    @Update("UPDATE t_gateway_route SET status=#{status},version=version+1 WHERE route_id=#{id}")
    int updateRouteStatus(@Param("id") long id, @Param("status") String status);

    /** 修改目标状态。 */
    @Update("UPDATE t_gateway_target SET status=#{status},version=version+1 WHERE target_id=#{id}")
    int updateTargetStatus(@Param("id") long id, @Param("status") String status);

    /** 按编码读取路由。 */
    @Select("SELECT * FROM t_gateway_route WHERE route_code=#{code}")
    Map<String, Object> route(String code);

    /** 按编码读取目标。 */
    @Select("SELECT * FROM t_gateway_target WHERE target_service_code=#{code}")
    Map<String, Object> target(String code);

    /** 读取路由的 Header 白名单。 */
    @Select("SELECT header_name,header_use FROM t_gateway_route_header WHERE route_id=#{routeId} ORDER BY header_use,header_name")
    List<Map<String, Object>> routeHeaders(long routeId);

    /** 读取路由的 Query 白名单。 */
    @Select("SELECT parameter_name FROM t_gateway_route_query_param WHERE route_id=#{routeId} ORDER BY parameter_name")
    List<String> routeQueries(long routeId);

    /** 读取路由的 Guard 规则。 */
    @Select("SELECT * FROM t_gateway_guard_rule WHERE route_id=#{routeId} ORDER BY rule_type,dimension")
    List<Map<String, Object>> routeGuards(long routeId);

    /** 按编码读取控制台资源。 */
    @Select("SELECT * FROM t_console_resource WHERE resource_code=#{code}")
    Map<String, Object> resource(String code);

    /** 按编码读取权限。 */
    @Select("SELECT * FROM t_permission WHERE permission_code=#{code}")
    Map<String, Object> permission(String code);

    /** 按编码读取 API Scope。 */
    @Select("SELECT * FROM t_api_scope WHERE scope_code=#{code}")
    Map<String, Object> apiScope(String code);

    /**
     * 网关路由测试参数。
     *
     * @param permissionId HUMAN_PERMISSION 绑定的权限，其他模式为 null
     * @param scopeCode API_SCOPE 绑定的 Scope，其他模式为 null
     */
    record Route(long id, String code, String channel, String method, String externalPath, String downstreamPath,
        long targetId, String securityProfile, String authorizationMode, Long permissionId, String scopeCode,
        String requestCookieMode, String responseCookieMode, long bodyLimit, String status) {
    }
}
