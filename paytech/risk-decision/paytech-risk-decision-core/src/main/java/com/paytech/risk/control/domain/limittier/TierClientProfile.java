package com.paytech.risk.control.domain.limittier;

import com.paytech.risk.control.core.enums.UnderwritingClientType;

/**
 * 建档时冻结到档位行的 Client 展示字段（取承保快照或迁移输入）
 *
 * @param clientId Client ID
 * @param clientDisplayName 名称
 * @param clientNo 编号
 * @param clientType 类型
 * @param jurisdictionCode 辖区代码
 */
public record TierClientProfile(long clientId, String clientDisplayName, String clientNo,
    UnderwritingClientType clientType, String jurisdictionCode) { }
