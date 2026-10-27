package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.AddressFormatComponent;
import java.util.Collection;
import java.util.List;

/** t_address_format_component 仓储。 */
public interface AddressFormatComponentRepository {
    /**
     * 给定地址格式的全部组件，按格式与展示顺序
     * 
     * @param formatCodes 地址格式代码
     * @return 组件；空集合不访问数据库
     */
    List<AddressFormatComponent> findByFormats(Collection<String> formatCodes);
}
