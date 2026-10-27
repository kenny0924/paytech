package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import java.util.List;

/** t_jurisdiction 仓储。 */
public interface JurisdictionRepository {
    /**
     * 按代码读取辖区包
     * 
     * @param jurisdictionCode 辖区代码
     * @return 辖区包；不存在为 null
     */
    Jurisdiction find(String jurisdictionCode);

    /**
     * 全部辖区包（含非 ACTIVE），按代码排序
     * 
     * @return 辖区包列表
     */
    List<Jurisdiction> findAll();
}
