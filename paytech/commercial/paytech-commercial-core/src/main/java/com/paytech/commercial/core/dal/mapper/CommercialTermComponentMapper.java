package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;

/** t_commercial_term_component 的持久化查询。 */
public interface CommercialTermComponentMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_component 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermComponent> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 批量插入完整记录，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<CommercialTermComponent> rows);

    /** 按主键读取，不存在返回 null。 */
    CommercialTermComponent selectById(@Param("termComponentId") long termComponentId);

    /** 按唯一键（价格、组件代码）读取，不存在返回 null。 */
    CommercialTermComponent selectByUniqueKey(@Param("termPriceId") long termPriceId, @Param("componentCode") String componentCode);

    /** 读取一组价格的全部组件，按 term_component_id 升序；termPriceIds 必须非空。 */
    List<CommercialTermComponent> selectByPriceIds(@Param("termPriceIds") Collection<Long> termPriceIds);

    /** 删除一组价格的全部组件，用于 DRAFT 内整体替换，返回删除行数；termPriceIds 必须非空。 */
    int deleteByPriceIds(@Param("termPriceIds") Collection<Long> termPriceIds);
}
