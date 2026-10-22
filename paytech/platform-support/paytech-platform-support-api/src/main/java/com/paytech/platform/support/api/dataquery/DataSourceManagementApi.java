package com.paytech.platform.support.api.dataquery;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.request.ChangeDataSourceStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.request.TestDataSourceConnectionRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateDataSourceRequestBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceConnectionTestResponseBody;
import com.paytech.platform.support.api.dataquery.response.DataSourceResponseBody;

import jakarta.validation.Valid;

/**
 * 数据查询的数据源维护；只供 operations-console-bff 私网调用，操作人由 Gateway 可信 Header 传递。
 * <li>查询需要 DATA_QUERY_READ，写操作与连接测试需要 DATA_SOURCE_MANAGE；无权限返回 403 OPERATIONS_PERMISSION_DENIED。</li>
 * <li>密码只在新建、修改、测试连接时提交，保存为 Vault Token，任何响应都不回显。</li>
 * <li>状态：INACTIVE → ACTIVE ⇄ INACTIVE → DELETED；删除为软删除，编码不能复用。</li>
 * <li>写接口带 expectedVersion，版本不符返回 409；数据源不存在或已删除返回 404。</li>
 */
@HttpExchange(url = "/data-query-admin/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface DataSourceManagementApi {

    /**
     * 分页查询数据源（不含 DELETED）
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param status 可选：INACTIVE、ACTIVE
     * @param engineType 可选：MYSQL、DORIS
     * @param keyword 可选：编码或名称包含该文本，最多 64 字符
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按最后修改时间倒序
     */
    @GetExchange("/data-sources")
    PagingResponseBody<DataSourceResponseBody> listDataSources(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "engineType", required = false) String engineType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 数据源详情
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param dataSourceId 数据源 ID
     * @return 配置、最后一次检查与引用模板数；DELETED 也可查询（用于显示历史版本的数据源）
     */
    @GetExchange("/data-sources/{dataSourceId}")
    DataSourceResponseBody getDataSource(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("dataSourceId") String dataSourceId);

    /**
     * 新建数据源，状态为 INACTIVE，版本为 1
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 编码、连接参数、账号密码与连接池上限
     * @return 保存结果，objectType=DATA_SOURCE
     *         <li>编码已存在（含已删除）返回 400；驱动参数不在白名单、连接池超过上限返回 400。</li>
     *         <li>Vault 不可用返回 503，未写库。</li>
     */
    @PostExchange("/data-sources")
    ConfigurationMutationResponseBody createDataSource(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateDataSourceRequestBody request);

    /**
     * 按原版本修改数据源，版本加 1；各实例在一个刷新周期内切到新配置
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param dataSourceId 数据源 ID
     * @param request 新配置；password 为空表示保留原密码
     * @return 保存结果
     *         <li>ACTIVE 数据源先以新配置做连接检查，未通过返回 400 并列出原因，原配置继续生效。</li>
     *         <li>无变化时不递增版本。</li>
     */
    @PutExchange("/data-sources/{dataSourceId}")
    ConfigurationMutationResponseBody updateDataSource(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("dataSourceId") String dataSourceId,
        @Valid @RequestBody UpdateDataSourceRequestBody request);

    /**
     * 测试草稿连接配置，不保存配置
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 草稿配置；带 dataSourceId 时结果回写该数据源的最后一次检查
     * @return 检查结果；连接失败或权限不通过也返回 200，result=FAILED
     */
    @PostExchange("/data-source-connection-tests")
    DataSourceConnectionTestResponseBody testDataSourceConnection(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody TestDataSourceConnectionRequestBody request);

    /**
     * 启用或停用数据源
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param dataSourceId 数据源 ID
     * @param request 目标状态与原版本
     * @return 保存结果
     *         <li>启用先以已保存配置做连接检查并回写最后一次检查；未通过返回 400 并列出原因，状态不变。</li>
     *         <li>停用后新查询返回 400，进行中的查询正常结束后连接池关闭。目标状态与当前相同时不递增版本。</li>
     */
    @PostExchange("/data-sources/{dataSourceId}/status")
    ConfigurationMutationResponseBody changeDataSourceStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("dataSourceId") String dataSourceId,
        @Valid @RequestBody ChangeDataSourceStatusRequestBody request);

    /**
     * 删除数据源（软删除）
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param dataSourceId 数据源 ID
     * @param expectedVersion 调用方读取的原版本
     * @return 保存结果，status=DELETED
     *         <li>必须为 INACTIVE，且没有 ACTIVE 模板引用，否则返回 400（消息列出引用的模板编码）。</li>
     */
    @DeleteExchange("/data-sources/{dataSourceId}")
    ConfigurationMutationResponseBody deleteDataSource(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("dataSourceId") String dataSourceId,
        @RequestParam("expectedVersion") Integer expectedVersion);
}
