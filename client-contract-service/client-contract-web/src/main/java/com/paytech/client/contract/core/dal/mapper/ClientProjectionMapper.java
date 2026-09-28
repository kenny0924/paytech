package com.paytech.client.contract.core.dal.mapper;

import org.apache.ibatis.annotations.Param;
import com.paytech.client.contract.core.dal.dataobject.ClientProjectionHistory;

/** 只访问 CCT 本地 Client、申请和生命周期历史。 */
public interface ClientProjectionMapper {
    /** 按任务固定的历史行恢复来源。 */
    ClientProjectionHistory source(@Param("changeId") long changeId);
    /** 核对 Platform 更高位置是否确实对应本地的同一原始事实。 */
    ClientProjectionHistory sourceAt(@Param("clientId") long clientId, @Param("version") int version);
}
