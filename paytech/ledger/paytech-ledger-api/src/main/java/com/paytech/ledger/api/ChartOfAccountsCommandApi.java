package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CreateChartAccountRequestBody;
import com.paytech.ledger.api.request.UpdateChartAccountRequestBody;
import com.paytech.ledger.api.response.ChartAccountResponseBody;
import com.paytech.ledger.api.response.CreateChartAccountResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 维护科目表中的科目（内部能力 Ledger & Account Balance）
 * <li>不审批、直接生效；每次实际变化写一条不可变变更记录，操作人取自 X-Gateway-Actor-Subject。</li>
 * <li>同一科目表的维护与新建账簿（采用科目表）串行；科目表被任一账簿采用后，既有科目的编号与上级冻结（CHART_STRUCTURE_FROZEN），仍可追加科目、改名、停用与启用。</li>
 * <li>Finance 内部接口：操作人身份来自 Operations BFF 填入的 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header，仅经 Operations BFF 转发，不得直接对外发布。</li>
 * <li>需要区分处理的拒绝以 {@link com.paytech.ledger.api.enums.LedgerSetupErrorCode} 返回 400。</li>
 */
@HttpExchange(url = "/chart-of-accounts/v1", accept = "application/json")
public interface ChartOfAccountsCommandApi {

    /**
     * 新增 ACTIVE 科目
     *
     * @param actorAccountId 操作人 Account ID，由 Operations BFF 从会话填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，由 Operations BFF 填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param chartCode 科目表代码；不存在或未启用时 400 CHART_NOT_ACTIVE
     * @param body 请求
     * @return 201 与新科目主键；编号不合规则 CHART_ACCOUNT_NO_INVALID、编号或代码重复、上级无效时 400
     */
    @PostExchange("/{chartCode}/accounts")
    ResponseEntity<CreateChartAccountResponseBody> createChartAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("chartCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String chartCode,
        @Valid @RequestBody CreateChartAccountRequestBody body);

    /**
     * 修改科目名称、编号与上级
     *
     * @param actorAccountId 操作人 Account ID（X-Gateway-Actor-Subject）
     * @param sessionId 操作人会话 ID（X-Gateway-Session-Id）
     * @param chartCode 科目表代码
     * @param chartAccountId 科目主键；不属于该科目表时 400 CHART_ACCOUNT_NOT_FOUND
     * @param body 修改后的完整值；与当前值相同时直接返回、不写变更记录
     * @return 200 与修改后的科目；科目表已被采用时改编号或上级 400 CHART_STRUCTURE_FROZEN
     */
    @PostExchange("/{chartCode}/accounts/{chartAccountId}/update")
    ChartAccountResponseBody updateChartAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("chartCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String chartCode,
        @PathVariable("chartAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String chartAccountId,
        @Valid @RequestBody UpdateChartAccountRequestBody body);

    /**
     * 停用科目
     *
     * @param actorAccountId 操作人 Account ID（X-Gateway-Actor-Subject）
     * @param sessionId 操作人会话 ID（X-Gateway-Session-Id）
     * @param chartCode 科目表代码
     * @param chartAccountId 科目主键
     * @return 200 与停用后的科目；已停用时直接返回；仍被规则、结转规则、账户类型目录或启用中的下级引用时 400 CHART_ACCOUNT_IN_USE
     */
    @PostExchange("/{chartCode}/accounts/{chartAccountId}/disable")
    ChartAccountResponseBody disableChartAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("chartCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String chartCode,
        @PathVariable("chartAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String chartAccountId);

    /**
     * 启用科目
     *
     * @param actorAccountId 操作人 Account ID（X-Gateway-Actor-Subject）
     * @param sessionId 操作人会话 ID（X-Gateway-Session-Id）
     * @param chartCode 科目表代码
     * @param chartAccountId 科目主键
     * @return 200 与启用后的科目；已启用时直接返回；上级已停用时 400 CHART_PARENT_INVALID
     */
    @PostExchange("/{chartCode}/accounts/{chartAccountId}/enable")
    ChartAccountResponseBody enableChartAccount(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("chartCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String chartCode,
        @PathVariable("chartAccountId") @Pattern(regexp = "[1-9][0-9]{0,18}") String chartAccountId);
}
