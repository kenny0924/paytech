package com.paytech.client.master.core.dal.mapper;

import org.apache.ibatis.annotations.Param;

/** t_client_stored_card_setting 的只读映射（自 Commerce 迁入，D42）。 */
public interface ClientStoredCardSettingMapper {
    /** 读取开通标志；没有记录时返回 null。 */
    Boolean findEnabled(@Param("clientId") long clientId);
}
