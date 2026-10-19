package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.BusinessChangeRecordAggregateRootType;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;

/** t_business_change_record（commercial 实例）的本地仓储；不负责其他表的加载或事务。 */
public interface BusinessChangeRecordRepository {
    /** 读取本次 Checkout 范围内合同的 AGREEMENT 根修订。 */
    List<CheckoutRecords.BusinessChangeRecord> findCheckoutRoots(CommercialCheckoutScope scope);

    /**
     * 读取一个聚合根的 Root 当前修订版本（expected_change_version CAS 令牌）。
     *
     * @param rootType 聚合根类型
     * @param rootId 聚合根 ID
     * @return 当前修订版本，Root 不存在为 null
     */
    Long findRootVersion(BusinessChangeRecordAggregateRootType rootType, String rootId);

    /**
     * 插入一条历史身份；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param record 已准备 ID、Tuple 与初始版本的新行
     */
    void insert(BusinessChangeRecord record);

    /**
     * 按唯一键（record_type, record_id）读取。
     *
     * @param recordType 受审计对象类型
     * @param recordId 业务行稳定标识
     * @return 历史身份，不存在为 null
     */
    BusinessChangeRecord findByRecord(BusinessChangeRecordRecordType recordType, String recordId);

    /**
     * 读取一组同类型 Root；空集合不访问数据库。
     *
     * @param rootType 聚合根类型
     * @param rootIds 聚合根 ID
     * @return 按 history_id 升序的 Root，不存在的 ID 不出现
     */
    List<BusinessChangeRecord> findRoots(BusinessChangeRecordAggregateRootType rootType, Collection<String> rootIds);

    /**
     * Root CAS：以 expectedVersion 为条件推进到 expectedVersion + 1；未命中抛出并发冲突，成功后同步原对象的版本与更新时间。
     *
     * @param root 已读取的 Root
     * @param expectedVersion 外部命令携带的 expected_change_version
     * @param now 本次更新时间
     */
    void advanceRoot(BusinessChangeRecord root, long expectedVersion, Instant now);

    /**
     * 推进受审计 Child 的版本指针到 Root 的下一版本；Child 不做独立 CAS，指针不得后退，未命中抛出系统异常。
     *
     * @param child 已读取的 Child
     * @param nextVersion Root 本次的下一版本
     * @param now 本次更新时间
     */
    void advanceChild(BusinessChangeRecord child, long nextVersion, Instant now);
}
