package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;

/** t_business_change_version（commercial 实例）的本地仓储；快照只插入，不更新、不删除。 */
public interface BusinessChangeVersionRepository {
    /**
     * 插入一条不可变快照；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建时间截断到微秒后写回对象。</li>
     *
     * @param version 已准备 ID 与全部字段的新快照
     */
    void insert(BusinessChangeVersion version);

    /**
     * 读取一条历史身份的全部快照。
     *
     * @param historyId 历史身份
     * @return 按 version_no 升序的快照
     */
    List<BusinessChangeVersion> findByHistoryId(long historyId);
}
