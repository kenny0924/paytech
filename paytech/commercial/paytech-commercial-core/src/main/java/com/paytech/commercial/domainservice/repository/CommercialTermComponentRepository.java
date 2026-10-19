package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.domain.CommercialCheckoutScope;

/** t_commercial_term_component 的本地仓储；不负责其他表的加载或事务。 */
public interface CommercialTermComponentRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_component 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermComponent> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 批量插入商业条款组件；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param rows 已准备 ID 与全部字段的新行
     */
    void insertAll(List<CommercialTermComponent> rows);

    /**
     * 按主键读取。
     *
     * @param termComponentId 组件 ID
     * @return 商业条款组件，不存在为 null
     */
    CommercialTermComponent findById(long termComponentId);

    /**
     * 按唯一键（价格、组件代码）读取。
     *
     * @param termPriceId 价格 ID
     * @param componentCode 组件代码
     * @return 商业条款组件，不存在为 null
     */
    CommercialTermComponent findByUniqueKey(long termPriceId, String componentCode);

    /**
     * 读取一组价格的全部组件；空集合不访问数据库。
     *
     * @param termPriceIds 价格 ID
     * @return 按 term_component_id 升序的商业条款组件
     */
    List<CommercialTermComponent> findByPriceIds(Collection<Long> termPriceIds);

    /**
     * 删除一组价格的全部组件，用于 DRAFT 内整体替换；空集合不访问数据库。
     *
     * @param termPriceIds 价格 ID
     * @return 删除行数
     */
    int deleteByPriceIds(Collection<Long> termPriceIds);
}
