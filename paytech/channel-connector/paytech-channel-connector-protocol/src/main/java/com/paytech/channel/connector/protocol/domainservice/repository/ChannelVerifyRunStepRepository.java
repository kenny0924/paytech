package com.paytech.channel.connector.protocol.domainservice.repository;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRunStep;
import java.util.List;

/** 验证运行步骤的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface ChannelVerifyRunStepRepository {

    /** 读取运行的全部步骤，按序号排序。 */
    List<ChannelVerifyRunStep> byRun(long verifyRunId);

    /** 一次插入运行的全部步骤；空列表不访问数据库。 */
    void insertAll(List<ChannelVerifyRunStep> rows);
}
