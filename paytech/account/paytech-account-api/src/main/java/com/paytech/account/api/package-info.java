/**
 * 账户服务（account，Owner 明细账）对外合同
 * <li>HTTP：Owner 余额查询（{@code BalanceQueryApi}）、余额桶移动命令（{@code BalanceMovementApi}）、过账进度 / 批次 / 按流重建
 * （{@code AccountPostingApi}）与 Owner 账户及流水查询（{@code OwnerAccountQueryApi}）；均为内部接口，未接 Gateway。</li>
 * <li>事件：AccountPosted Payload（{@code event.AccountPostedPayload}），ledger 消费后过账控制科目。</li>
 * <li>代码：Owner 类型、主体类型、余额桶、借贷方向、Owner 账户类型目录、移动来源类型、批次来源与事实应用结果，以及标准科目代码。</li>
 */
package com.paytech.account.api;
