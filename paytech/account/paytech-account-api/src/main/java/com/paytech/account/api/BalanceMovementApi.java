package com.paytech.account.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.account.api.request.BalanceMovementRequestBody;
import com.paytech.account.api.response.BalanceMovementResponseBody;

import jakarta.validation.Valid;

/**
 * Owner 账户余额桶移动命令（明细账 Owner：account）
 * <li>同一账户的两个桶之间转移金额，生成一个 BALANCE_MOVEMENT 过账批次（两条流水）并发布 AccountPosted；ledger 控制科目不变，只推进进度。</li>
 * <li>以 {@code (sourceType, sourceRef)} 作为业务操作身份：首次 201，同来源重复请求 200 且 replayed = true，返回原移动结果，不比较也不重新执行。</li>
 * <li>内部接口：供后续出款 / 缴税切片调用；未接 Gateway 与统一身份前不得对外发布。</li>
 */
@HttpExchange(url = "/balance-movements/v1", accept = "application/json")
public interface BalanceMovementApi {

    /**
     * 在同一 Owner 账户的两个余额桶之间移动金额
     *
     * @param body 移动请求
     * @return 首次执行 201、同来源重放 200，含移动、批次主键与批次序号；
     *         <li>余额不足 400 BALANCE_INSUFFICIENT；账户不存在 400 ACCOUNT_NOT_FOUND；账户冻结或关闭 400 ACCOUNT_NOT_ACTIVE；
     *         来源类型、桶方向或科目与 Owner 组合不允许 400 MOVEMENT_NOT_ALLOWED。</li>
     *         <li>同一流或账户并发写冲突 409，调用方可用同一来源重试。</li>
     */
    @PostExchange
    ResponseEntity<BalanceMovementResponseBody> moveBalance(@Valid @RequestBody BalanceMovementRequestBody body);
}
