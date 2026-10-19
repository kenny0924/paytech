package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;

/** t_commercial_term_price 的持久化查询。 */
public interface CommercialTermPriceMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_price 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermPrice> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 批量插入完整记录，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<CommercialTermPrice> rows);

    /** 按主键读取，不存在返回 null。 */
    CommercialTermPrice selectById(@Param("termPriceId") long termPriceId);

    /** 按唯一键（版本、OE、币种、画像）读取，不存在返回 null。 */
    CommercialTermPrice selectByUniqueKey(@Param("termVersionId") long termVersionId, @Param("entityNo") String entityNo, @Param("currency") String currency, @Param("pricingProfileCode") String pricingProfileCode);

    /** 读取一个版本的全部价格，按 term_price_id 升序。 */
    List<CommercialTermPrice> selectByVersionId(@Param("termVersionId") long termVersionId);

    /** 删除一个版本的全部价格，用于 DRAFT 内整体替换；组件与档位由调用方先删除，返回删除行数。 */
    int deleteByVersionId(@Param("termVersionId") long termVersionId);
}
