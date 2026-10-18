package com.paytech.client.master.core.dal.mapper;

import org.apache.ibatis.annotations.Param;
import com.paytech.client.master.core.dal.dataobject.ClientProjectionHistory;

/** 只访问 CCT 本地 Client、申请和生命周期历史。 */
public interface ClientProjectionMapper {
    /** 按任务固定的历史行恢复来源。 */
    ClientProjectionHistory source(@Param("changeId") long changeId);
    /** 核对 Platform 更高位置是否确实对应本地的同一原始事实。 */
    ClientProjectionHistory sourceAt(@Param("clientId") long clientId, @Param("version") int version);
    /** 某 Client 当前最新的生命周期历史。 */
    ClientProjectionHistory latest(@Param("clientId") long clientId);
    /** 追加一条 UPDATE 历史版本，返回影响行数。 */
    int insertVersion(@Param("row") ClientProjectionHistory row, @Param("snapshot") String snapshot);
    /** 以前一版本为条件推进历史根的当前版本，返回影响行数。 */
    int advanceRoot(@Param("row") ClientProjectionHistory row);
}
