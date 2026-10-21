/**
 * 对账服务对外合同：内部 HTTP 接口（{@code /reconciliation/v1}，按能力分包）、稳定 Code 枚举（{@code enums}）与发给 billing 的
 * MQ 事件载荷（{@code event}）。
 * <li>运营写接口的操作人取自 {@link com.paytech.reconciliation.api.ReconciliationHeaders} 的 Gateway 身份头，仅经 Operations BFF 转发；
 * 清算账户解析与当前银行账户查询是服务间内部接口，不经 Gateway。</li>
 */
package com.paytech.reconciliation.api;
