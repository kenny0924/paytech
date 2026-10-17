package com.paytech.commerce.core.dal.dataobject.customer;

import java.time.Instant;

import com.paytech.commerce.domain.enums.ConsumerAcceptanceType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 消费者接受表对象，不关联其他表或复制个人资料。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerAcceptance {
    /** 不可变接受记录主键。 */
    private long consumerAcceptanceId;

    /** 接受的准确消费者合同。 */
    private long consumerContractId;

    /** 本次法律接受网络证据。 */
    private long networkEvidenceId;

    /** 接受的文档类型。 */
    private ConsumerAcceptanceType acceptanceType;

    /** 实际接受时刻，后续提交不改写。 */
    private Instant acceptedAt;

    /** 记录创建时刻。 */
    private Instant createTime;
}

