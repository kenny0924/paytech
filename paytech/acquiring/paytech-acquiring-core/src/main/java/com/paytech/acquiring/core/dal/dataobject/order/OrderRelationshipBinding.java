package com.paytech.acquiring.core.dal.dataobject.order;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 订单创建时冻结的商业关系角色绑定；一个订单每个角色一行，写入后不可变。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRelationshipBinding {
    /** 本库预生成的绑定主键。 */
    private long bindingId;

    /** 所属 Commerce Order 主键。 */
    private long orderId;

    /**
     * 角色 Code
     * <li>commercial 发布的 RelationshipRole Code；写入前已校验取值且同一订单不重复。</li>
     */
    private String roleCode;

    /**
     * 主体类型 Code
     * <li>commercial 发布的 RelationshipSubjectType Code。</li>
     */
    private String subjectType;

    /**
     * 主体标识
     * <li>CLIENT 为 clientId，OPERATING_ENTITY 为 entityNo，LOCAL_PARTNER 为 localPartnerId；PLATFORM 为空。</li>
     */
    private String subjectId;

    /**
     * 主体法律 Party
     * <li>client-master Party 稳定标识；commercial 未提供时为空。</li>
     */
    private Long partyId;

    /** 所属快照的推导规则版本。 */
    private int snapshotModelVersion;

    /** 创建时间，与订单创建时刻相同。 */
    private Instant createTime;
}
