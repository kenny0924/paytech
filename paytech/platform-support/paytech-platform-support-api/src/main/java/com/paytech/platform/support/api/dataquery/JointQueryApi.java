package com.paytech.platform.support.api.dataquery;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.dataquery.request.JointQueryStepRequestBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryGraphResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQuerySettingsResponseBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryStepResponseBody;

import jakarta.validation.Valid;

/**
 * 联合查询：从一个业务单号出发，按键类型逐层执行参与模板；本期调用方只有 operations-console-bff，经私网调用。
 * <li>调用方登记与操作人会话校验与 {@link DataQueryExecutionApi} 相同；会话需具备 DATA_QUERY_EXECUTE，无权限返回 403。</li>
 * <li>只读 Platform 数据库与被查库，不写任何表、不登记审计；结果不作为业务执行依据（从库有复制延迟）。</li>
 * <li>展开规则（参与条件、值规范化、入口正则、LINK 提取、单层并发、预算与上限）由服务端执行；
 * 循环状态（visited、层数、累计执行数、总时长、停止）由页面保存，每层调用一次 J3。</li>
 */
@HttpExchange(url = "/data-query/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface JointQueryApi {

    /**
     * 联合查询页初始化数据
     *
     * @param callerService 调用方服务名
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @return 入口键类型（ACTIVE 且 entryAllowed）、泳道分组与护栏
     */
    @GetExchange("/joint-query/settings")
    JointQuerySettingsResponseBody getJointQuerySettings(
        @RequestHeader(DataQueryExecutionApi.CALLER_SERVICE_HEADER) String callerService,
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session);

    /**
     * 从入口键类型出发的可达预览，不执行查询
     *
     * @param callerService 调用方服务名
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param entryKeyType 入口键类型编码；缺失时返回 400 BUSINESS_CHECK_FAILED
     * @return 按泳道分组的可达模板、链路断点与不参与的模板
     *         <li>入口键类型缺失、不存在、非 ACTIVE 或不允许作为入口返回 400 BUSINESS_CHECK_FAILED（GET 不返回 422 字段错误）。</li>
     */
    @GetExchange("/joint-query/graph")
    JointQueryGraphResponseBody getJointQueryGraph(
        @RequestHeader(DataQueryExecutionApi.CALLER_SERVICE_HEADER) String callerService,
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "entryKeyType", required = false) String entryKeyType);

    /**
     * 执行一层
     *
     * @param callerService 调用方服务名
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 层号、本层键值、可选的模板范围与已加载对象
     * @return 本层节点、LINK 值、发现的键、截断与因已加载而未执行的视图
     *         <li>未知或停用键类型、层号或键值不合法返回 422 VALIDATION_ERROR，field 形如 keys[0].values[3]。</li>
     *         <li>本层没有参与模板返回 200 与空 nodes。单个模板失败或被跳过不影响整体，记在节点 status 上。</li>
     *         <li>depth&gt;0 且未指定模板时，“按自身 ID 查”的模板跳过 loadedSubjects 中的值（technique 8.7），
     *         全部跳过的记入 alreadyLoaded，不计入单步执行数与预算。</li>
     *         <li>单层预算默认 8 秒，低于 BFF 9 秒与 Gateway 10 秒的响应超时。</li>
     */
    @PostExchange("/joint-query/steps")
    JointQueryStepResponseBody executeJointQueryStep(
        @RequestHeader(DataQueryExecutionApi.CALLER_SERVICE_HEADER) String callerService,
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody JointQueryStepRequestBody request);
}
