package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.domain.CommercialCheckoutScope;

/** t_commercial_term_component_band 的本地仓储；不负责其他表的加载或事务。 */
public interface CommercialTermComponentBandRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_component_band 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermComponentBand> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 批量插入商业条款档位；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param rows 已准备 ID 与全部字段的新行
     */
    void insertAll(List<CommercialTermComponentBand> rows);

    /**
     * 按主键读取。
     *
     * @param termBandId 档位 ID
     * @return 商业条款档位，不存在为 null
     */
    CommercialTermComponentBand findById(long termBandId);

    /**
     * 读取一组组件的全部档位；空集合不访问数据库。
     *
     * @param termComponentIds 组件 ID
     * @return 按 term_band_id 升序的商业条款档位
     */
    List<CommercialTermComponentBand> findByComponentIds(Collection<Long> termComponentIds);

    /**
     * 删除一组组件的全部档位，用于 DRAFT 内整体替换；空集合不访问数据库。
     *
     * @param termComponentIds 组件 ID
     * @return 删除行数
     */
    int deleteByComponentIds(Collection<Long> termComponentIds);
}
