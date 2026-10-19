package com.paytech.ledger.integration.billing;

import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.BillingFactProgress;

/** 读取 billing 计费事实与账簿主体的发布进度；必须在数据库事务外调用。 */
public interface BillingFactClient {

    /**
     * 查询账簿主体的发布进度
     *
     * @param book ledger 账簿（主体类型、主体标识与币种即 billing 进度接口的 entityType、entityId、currency）
     * @return 未发布计数、已发布计数与已发布最大 factId
     * @throws RuntimeException billing 不可用、拒绝或响应不合法；调用方按失败关闭处理
     */
    BillingFactProgress getProgress(LedgerBook book);

    /**
     * 读取 account 入账批次引用的计费事实头、外部引用与全部明细行
     *
     * @param factId 计费事实主键
     * @param factVersion 入账批次记录的事实版本
     * @return 事实载荷，明细行按行号升序；字段语义同 BillingFactRecorded Payload
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException billing 返回的版本与批次不一致（等待重投）
     * @throws RuntimeException billing 不可用或响应不合法
     */
    BillingFactPayload getFact(long factId, long factVersion);
}
