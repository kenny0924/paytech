package com.paytech.compliance.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.compliance.api.request.CreateSettlementControlRequestBody;
import com.paytech.compliance.api.response.SettlementControlListResponseBody;
import com.paytech.compliance.api.response.SettlementControlResponseBody;

/**
 * 结算资格控制（C3 / D38：合规持有 t_settlement_control 三表，design/07 §2）
 * <li>阶段 0 只定合同与 Schema 归属：compliance 尚未提供 Controller 与 Processor，调用方不得在本步接入。</li>
 * <li>创建由 risk-decision 的 Action Effect 触发，本地事务落表并发布 SettlementRestrictionChanged；读取在出款检查点评估事务前调用，
 * compliance 不可用时评估失败关闭。</li>
 * <li>ID、金额与时间一律为十进制 / UTC 字符串。</li>
 */
@HttpExchange("/compliance/v1/settlement-controls")
public interface SettlementControlApi {

    /**
     * 创建结算资格控制
     *
     * @param caller
     *            私网调用方，固定为 risk-decision-service
     * @param body
     *            控制内容与范围；幂等键为 sourceActionEffectId
     * @return 新建或按幂等键返回的原控制
     *         <li>同一 sourceActionEffectId 内容一致时返回原控制；内容不一致返回 409。</li>
     */
    @PostExchange
    SettlementControlResponseBody create(@RequestHeader("X-Caller-Service") String caller,
        @RequestBody CreateSettlementControlRequestBody body);

    /**
     * 查询角色的结算资格控制集合
     *
     * @param caller
     *            私网调用方，固定为 risk-decision-service
     * @param ownerType
     *            角色类型（role_type）：CLIENT、LOCAL_PARTNER
     * @param ownerId
     *            角色 ID（role_id），十进制字符串
     * @param status
     *            控制状态（status）；出款检查点固定传 ACTIVE
     * @return 控制及范围，按 control_id 升序
     */
    @GetExchange
    SettlementControlListResponseBody list(@RequestHeader("X-Caller-Service") String caller,
        @RequestParam("ownerType") String ownerType, @RequestParam("ownerId") String ownerId,
        @RequestParam("status") String status);
}
