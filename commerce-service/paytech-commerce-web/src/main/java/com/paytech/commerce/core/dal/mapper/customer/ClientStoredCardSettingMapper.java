package com.paytech.commerce.core.dal.mapper.customer;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Client 存卡开通 SQL 映射。 */
@Mapper
public interface ClientStoredCardSettingMapper {
    /** 读取开通标志；没有记录时返回 null。 */
    Boolean findEnabled(@Param("clientId") long clientId);
}
