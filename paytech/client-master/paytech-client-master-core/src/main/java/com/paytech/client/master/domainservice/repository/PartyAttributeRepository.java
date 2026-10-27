package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.PartyAttribute;
import java.util.Collection;
import java.util.List;

/** t_party_attribute 仓储。 */
public interface PartyAttributeRepository {
    /**
     * 多份资料的属性值
     * 
     * @param profileIds 资料 ID
     * @return 属性值；空集合不访问数据库
     */
    List<PartyAttribute> findByProfiles(Collection<Long> profileIds);

    /**
     * 写入或覆盖一项属性值
     * 
     * @param row 已准备 ID 与时间的属性值
     */
    void save(PartyAttribute row);

    /**
     * 清空一项属性值；不存在时无操作
     * 
     * @param profileId 资料 ID
     * @param attributeCode 属性代码
     */
    void delete(long profileId, String attributeCode);
}
