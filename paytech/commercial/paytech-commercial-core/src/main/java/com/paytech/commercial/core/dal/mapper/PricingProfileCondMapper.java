package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;

/** t_pricing_profile_cond 的持久化访问。 */
public interface PricingProfileCondMapper {
    /** 批量插入完整记录，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<PricingProfileCond> rows);

    /** 按主键读取，不存在返回 null。 */
    PricingProfileCond selectById(@Param("profileConditionId") long profileConditionId);

    /** 读取一个画像的全部条件，按 profile_condition_id 升序。 */
    List<PricingProfileCond> selectByProfileId(@Param("pricingProfileId") long pricingProfileId);

    /** 读取一组画像的全部条件，按 profile_condition_id 升序；pricingProfileIds 必须非空。 */
    List<PricingProfileCond> selectByProfileIds(@Param("pricingProfileIds") Collection<Long> pricingProfileIds);

    /** 删除一个画像的全部条件，用于 DRAFT 内整体替换，返回删除行数。 */
    int deleteByProfileId(@Param("pricingProfileId") long pricingProfileId);
}
