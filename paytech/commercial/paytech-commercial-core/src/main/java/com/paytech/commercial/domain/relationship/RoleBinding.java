package com.paytech.commercial.domain.relationship;

import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.commercial.api.enums.RelationshipSubjectType;

import lombok.Value;
import lombok.experimental.Accessors;

/** 商业关系快照中一个角色到主体的绑定。 */
@Value
@Accessors(fluent = true)
public class RoleBinding {
    /** 角色。 */
    RelationshipRole role;

    /** 主体类型。 */
    RelationshipSubjectType subjectType;

    /**
     * 主体标识
     * <li>CLIENT 为 clientId，OPERATING_ENTITY 为 entityNo，LOCAL_PARTNER 为 localPartnerId；PLATFORM 为空。</li>
     */
    String subjectId;

    /** 主体法律主体 Party ID。 */
    Long partyId;
}
