package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;

/** t_pricing_profile_cond 的本地仓储；不负责其他表的加载或事务。 */
public interface PricingProfileCondRepository {
    /**
     * 批量插入定价画像条件；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param rows 已准备 ID 与全部字段的新行
     */
    void insertAll(List<PricingProfileCond> rows);

    /**
     * 按主键读取。
     *
     * @param profileConditionId 条件行 ID
     * @return 定价画像条件，不存在为 null
     */
    PricingProfileCond findById(long profileConditionId);

    /**
     * 读取一个画像的全部条件。
     *
     * @param pricingProfileId 画像 ID
     * @return 按 profile_condition_id 升序的定价画像条件
     */
    List<PricingProfileCond> findByProfileId(long pricingProfileId);

    /**
     * 读取一组画像的全部条件；空集合不访问数据库。
     *
     * @param pricingProfileIds 画像 ID
     * @return 按 profile_condition_id 升序的定价画像条件
     */
    List<PricingProfileCond> findByProfileIds(Collection<Long> pricingProfileIds);

    /**
     * 删除一个画像的全部条件，用于 DRAFT 内整体替换。
     *
     * @param pricingProfileId 画像 ID
     * @return 删除行数
     */
    int deleteByProfileId(long pricingProfileId);
}
