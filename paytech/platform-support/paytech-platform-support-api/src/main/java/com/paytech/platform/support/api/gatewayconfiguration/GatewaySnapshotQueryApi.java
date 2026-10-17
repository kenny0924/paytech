package com.paytech.platform.support.api.gatewayconfiguration;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;

/**
 * PLT-I20 全局公共 Gateway 配置快照查询接口。
 *
 * <p>供 Gateway 后台刷新任务经部署限制的私有 HTTP 网络调用，不注册公共路由、不经过 BFF，
 * 不接收浏览器身份、API Credential 或调用方身份 Header。
 */
@HttpExchange("/gateway-config/v1")
public interface GatewaySnapshotQueryApi {

    /**
     * 查询全部公共入口的完整当前有效配置快照。
     *
     * <p>目标、路由及子配置来自同一只读数据库快照，完整校验通过后才返回。
     * 未初始化 Target 目录或当前配置无法形成完整有效快照时，由公共异常处理返回
     * {@code 500 INTERNAL_ERROR}；Gateway 保留其上一份有效快照，不启用部分结果。
     *
     * @return HTTP 200 及 {@code schemaVersion=1} 的完整快照
     *         <li>只包含 ACTIVE Target 及自身、所属 Target 均为 ACTIVE 的 Route。</li>
     *         <li>已初始化目录的空路由集合是合法结果，表示关闭全部公共路由。</li>
     *         <li>每次成功均返回完整响应，不使用 ETag、If-None-Match 或 304 条件缓存。</li>
     */
    @GetExchange("/public-snapshot")
    GatewayPublicSnapshotResponseBody queryPublicSnapshot();
}
