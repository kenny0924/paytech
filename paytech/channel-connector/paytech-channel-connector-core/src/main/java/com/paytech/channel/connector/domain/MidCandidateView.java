package com.paytech.payment.channel.domain;

import java.util.List;
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
}
