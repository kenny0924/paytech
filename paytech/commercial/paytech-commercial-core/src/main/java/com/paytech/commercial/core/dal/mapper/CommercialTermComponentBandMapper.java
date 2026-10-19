package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;

/** t_commercial_term_component_band 的持久化查询。 */
public interface CommercialTermComponentBandMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_component_band 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermComponentBand> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 批量插入完整记录，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<CommercialTermComponentBand> rows);

    /** 按主键读取，不存在返回 null。 */
    CommercialTermComponentBand selectById(@Param("termBandId") long termBandId);

    /** 读取一组组件的全部档位，按 term_band_id 升序；termComponentIds 必须非空。 */
    List<CommercialTermComponentBand> selectByComponentIds(@Param("termComponentIds") Collection<Long> termComponentIds);

    /** 删除一组组件的全部档位，用于 DRAFT 内整体替换，返回删除行数；termComponentIds 必须非空。 */
    int deleteByComponentIds(@Param("termComponentIds") Collection<Long> termComponentIds);
}
