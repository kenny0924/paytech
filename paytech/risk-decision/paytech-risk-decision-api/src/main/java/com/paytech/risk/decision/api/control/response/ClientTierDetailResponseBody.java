package com.paytech.risk.decision.api.control.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Client 当前档位详情（L2）：列表字段加并发版本、受限前档位、观察期进度、本档位按币种额度与已用
 * <li>无档位时只有 clientId，tier 为空，额度为空集合。</li>
 */
@Getter
@Setter
public class ClientTierDetailResponseBody extends ClientTierSummaryBody {

    /** 档位并发版本；无档位为空。 */
    private Integer version;

    /** 进入 RESTRICTED 前的档位；非 RESTRICTED 为空。 */
    private String previousTier;

    /** 观察期进度；非 OBSERVATION 为空。 */
    private ClientTierObservationBody observation;

    /** 本档位已配置币种的额度与已用（放行累计），按币种代码排序。 */
    private List<ClientTierQuotaBody> quotas;

    /** 在任一档位配置过、但本档位未配置的币种，按代码排序。 */
    private List<String> unconfiguredCurrencies;

    /** 本档位未配置币种的处理：DENY（拒绝）或 ALLOW（不限，仅 STANDARD）；无档位为空。 */
    private String unconfiguredCurrencyPolicy;
}
