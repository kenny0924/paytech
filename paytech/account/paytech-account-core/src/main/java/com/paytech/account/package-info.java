/**
 * 账户服务（account-service）业务实现：Client 与 Local Partner 分类余额查询。
 * <li>本期与 ledger 共连 mor_ledger 且只读 {@code t_account}、{@code t_chart_account}、{@code t_ledger_book}；第 11b 步改为消费经济事实维护自有余额表。</li>
 */
package com.paytech.account;
