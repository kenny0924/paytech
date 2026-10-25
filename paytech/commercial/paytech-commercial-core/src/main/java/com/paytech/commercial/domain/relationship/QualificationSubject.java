package com.paytech.commercial.domain.relationship;

import com.paytech.commercial.api.enums.RelationshipSubjectType;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 需要角色资格的一个主体
 * <li>类型只取 CLIENT、OPERATING_ENTITY、LOCAL_PARTNER；OPERATING_ENTITY 以 entityNo 标识，其余以十进制 ID 标识。</li>
 */
@Value
@Accessors(fluent = true)
public class QualificationSubject {
    /** 主体类型。 */
    RelationshipSubjectType subjectType;

    /** 主体标识。 */
    String subjectId;

    /** Client 主体。 */
    public static QualificationSubject client(long clientId) {
        return new QualificationSubject(RelationshipSubjectType.CLIENT, Long.toString(clientId));
    }

    /** 收单 OE 主体。 */
    public static QualificationSubject operatingEntity(String entityNo) {
        return new QualificationSubject(RelationshipSubjectType.OPERATING_ENTITY, entityNo);
    }

    /** OE 提供方主体。 */
    public static QualificationSubject localPartner(long localPartnerId) {
        return new QualificationSubject(RelationshipSubjectType.LOCAL_PARTNER, Long.toString(localPartnerId));
    }
}
