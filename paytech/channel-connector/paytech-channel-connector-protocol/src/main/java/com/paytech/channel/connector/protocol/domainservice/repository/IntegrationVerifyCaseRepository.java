package com.paytech.channel.connector.protocol.domainservice.repository;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import java.time.Instant;
import java.util.List;

/** 验证用例的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationVerifyCaseRepository {

    /** 按接入方与不可变编码读取用例。 */
    IntegrationVerifyCase byCode(long integrationId, String code);

    /** 在调用方事务中锁定用例行，供版本校验及条件更新。 */
    IntegrationVerifyCase lock(long integrationId, String code);

    /** 分页查询，按编码与标识稳定排序。 */
    List<IntegrationVerifyCase> list(long integrationId, String keyword, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(long integrationId, String keyword);

    /** 统计引用某验证账号的用例数量。 */
    long countByAccount(long verifyAccountId);

    /** 插入新用例，接入方内编码由唯一索引保护。 */
    void insert(IntegrationVerifyCase row);

    /** 按原版本更新用例内容，并递增版本。 */
    void update(IntegrationVerifyCase row, String caseName, String description, long verifyAccountId, String stepsJson, long updatedBy, Instant updateTime);

    /** 按原版本删除用例；运行记录保留。 */
    void delete(IntegrationVerifyCase row);
}
