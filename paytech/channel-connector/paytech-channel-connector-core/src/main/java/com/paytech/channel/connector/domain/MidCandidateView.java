package com.paytech.channel.connector.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Channel 内部业务合同。 */
@Getter
@Setter
public class MidCandidateView {
    /** 候选渠道 ID。 */
    private Long channelId;

    /** 候选账号 ID。 */
    private Long channelAccountId;

    /** 候选 MID ID。 */
    private Long midId;

    /** 本次维度下逐代码解析的能力；没有秘密或 Vault Token。 */
    private List<CapabilityView> capabilities;

    /** 明确支持的渠道操作代码：{@code ACTION.*} 为 true 的能力去掉前缀，按能力代码升序。 */
    private Set<String> supportedOperations;

    /** 本次维度匹配到的金额下限（AMOUNT.MIN），未声明为 null。 */
    private BigDecimal minAmount;

    /** 本次维度匹配到的金额上限（AMOUNT.MAX），未声明为 null。 */
    private BigDecimal maxAmount;
}
