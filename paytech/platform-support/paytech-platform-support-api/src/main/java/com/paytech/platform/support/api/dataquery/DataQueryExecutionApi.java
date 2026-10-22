package com.paytech.platform.support.api.dataquery;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.dataquery.request.ExecuteDataQueryRequestBody;
import com.paytech.platform.support.api.dataquery.response.DataQueryResultResponseBody;

import jakarta.validation.Valid;

/**
 * 按模板编码执行只读查询；本期调用方只有 operations-console-bff，经私网调用。
 * <li>调用方必须在 data-query.http.callers 中登记（X-Caller-Service，携带内部 Token 时以 Token sub 为准），否则返回 400；
 * 操作人会话需具备 DATA_QUERY_EXECUTE，可执行任意 ACTIVE 模板。</li>
 * <li>结果不作为业务执行依据（从库有复制延迟）；执行不写 Platform 数据库，只记元数据日志与指标。</li>
 */
@HttpExchange(url = "/data-query/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface DataQueryExecutionApi {

    /** 调用方服务名 Header。 */
    String CALLER_SERVICE_HEADER = "X-Caller-Service";

    /**
     * 执行模板当前版本
     *
     * @param callerService 调用方服务名
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param templateCode 模板编码
     * @param request 参数与分页
     * @return 结果，带实际执行的版本号
     *         <li>模板不存在返回 404；模板或数据源不是 ACTIVE 返回 400。</li>
     *         <li>参数错误返回 422 VALIDATION_ERROR，errors[].field 形如 params.conditions[2].minAmount。</li>
     *         <li>超时、结果过大返回 400 DATA_QUERY_TIMEOUT / DATA_QUERY_RESULT_TOO_LARGE；连接失败返回 503 DATA_SOURCE_UNAVAILABLE。</li>
     */
    @PostExchange("/templates/{templateCode}/executions")
    DataQueryResultResponseBody executeDataQuery(
        @RequestHeader(CALLER_SERVICE_HEADER) String callerService,
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateCode") String templateCode,
        @Valid @RequestBody ExecuteDataQueryRequestBody request);
}
