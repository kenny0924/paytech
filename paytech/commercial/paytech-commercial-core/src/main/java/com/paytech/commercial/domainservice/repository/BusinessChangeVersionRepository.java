package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;
import com.paytech.foundation.web.paging.PagingRequestBody;

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

    /**
     * 读取一组历史身份的全部快照；空集合不访问数据库。
     *
     * @param historyIds 历史身份
     * @return 按 history_id、version_no 升序
     */
    List<BusinessChangeVersion> findByHistoryIds(Collection<Long> historyIds);

    /**
     * 读取一组历史身份的一页快照，按 version_no 倒序；同一版本 Root 快照在前，其余按 history_id 升序。
     *
     * @param historyIds 同一聚合根下的历史身份，非空
     * @param rootHistoryId Root 的历史身份
     * @param paging 已校验的分页
     * @return 当前页快照
     */
    List<BusinessChangeVersion> findPageByHistoryIds(Collection<Long> historyIds, long rootHistoryId, PagingRequestBody paging);

    /**
     * 一组历史身份的快照总数。
     *
     * @param historyIds 历史身份，非空
     * @return 总数
     */
    long countByHistoryIds(Collection<Long> historyIds);
}
