package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.PartyAttribute;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_party_attribute SQL。 */
public interface PartyAttributeMapper {
    /** 多份资料的属性值。 */
    List<PartyAttribute> findByProfiles(@Param("profileIds") Collection<Long> profileIds);

    /** 按 (profile_id, attribute_code) 写入或覆盖。 */
    int upsert(@Param("row") PartyAttribute row);

    /** 删除一项属性。 */
    int delete(@Param("profileId") long profileId, @Param("attributeCode") String attributeCode);
}
