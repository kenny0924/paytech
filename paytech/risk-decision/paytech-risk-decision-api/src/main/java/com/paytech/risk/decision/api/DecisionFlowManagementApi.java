package com.paytech.risk.api.decision;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.risk.api.decision.request.SaveFlowRequestBody;
import com.paytech.risk.api.decision.response.DeleteFlowResponseBody;
import com.paytech.risk.api.decision.response.SaveFlowResponseBody;
import com.paytech.risk.api.decision.response.ValidateFlowResponseBody;

import jakarta.validation.Valid;

/** 完整配置保存、只读静态校验与整体删除，不触发真实风控或发布。 */
@HttpExchange("/decision-flows/v1")
public interface DecisionFlowManagementApi {
    /**
     * 保存完整图为新版本，编码唯一约束与根版本保护业务并发。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param body
     *            完整配置；新建的两个基准字段为 null，编辑必须指向当前准确版本
     * @return 本次保存结果；重复创建、过期基准或已删除根返回冲突
     */
    @PostExchange("/flows")
    SaveFlowResponseBody save(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody SaveFlowRequestBody body);

    /**
     * 只读检查合法草稿，配置未完成时正常返回 valid=false。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param body
     *            与保存接口相同的有界图结构，不预留版本或持有写锁
     * @return 最多 100 个定位问题及截断标记；传输格式错误仍返回请求错误
     */
    @PostExchange("/flows/validate")
    ValidateFlowResponseBody validate(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody SaveFlowRequestBody body);

    /**
     * 按根修订号整体删除，不接收 Body，保留编码及不可变历史。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param flowId
     *            原流程管理 ID，正 BIGINT 十进制字符串
     * @param expectedVersion
     *            调用方最后读取的根修订号，过期返回版本冲突
     * @return 本次删除事实；再次删除返回 RISK_FLOW_DELETED
     */
    @DeleteExchange("/flows/{flowId}")
    DeleteFlowResponseBody delete(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("flowId") String flowId,
        @RequestParam("expectedVersion") String expectedVersion);
}
