package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.AddressFormatComponent;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_address_format_component 只读 SQL。 */
public interface AddressFormatComponentMapper {
    /** 给定地址格式的全部组件。 */
    List<AddressFormatComponent> findByFormats(@Param("formatCodes") Collection<String> formatCodes);
}
