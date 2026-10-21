package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.domain.CommercialCheckoutScope;

/** t_commercial_term_price 的本地仓储；不负责其他表的加载或事务。 */
public interface CommercialTermPriceRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_price 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermPrice> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 批量插入商业条款价格；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param rows 已准备 ID 与全部字段的新行
     */
    void insertAll(List<CommercialTermPrice> rows);

    /**
     * 按主键读取。
     *
     * @param termPriceId 价格 ID
     * @return 商业条款价格，不存在为 null
     */
    CommercialTermPrice findById(long termPriceId);

    /**
     * 按唯一键（版本、OE、币种、画像）读取。
     *
     * @param termVersionId 版本 ID
     * @param entityNo 准确 OE
     * @param currency ISO 4217 币种
     * @param pricingProfileCode 画像代码
     * @return 商业条款价格，不存在为 null
     */
    CommercialTermPrice findByUniqueKey(long termVersionId, String entityNo, String currency, String pricingProfileCode);

    /**
     * 读取一个版本的全部价格。
     *
     * @param termVersionId 版本 ID
     * @return 按 term_price_id 升序的商业条款价格
     */
    List<CommercialTermPrice> findByVersionId(long termVersionId);

    /**
     * 删除一个版本的全部价格，用于 DRAFT 内整体替换；组件与档位由调用方先删除。
     *
     * @param termVersionId 版本 ID
     * @return 删除行数
     */
    int deleteByVersionId(long termVersionId);

    /**
     * 统计一组版本各自的价格条数；空集合不访问数据库。
     *
     * @param termVersionIds 版本 ID
     * @return 版本 ID → 价格条数；没有价格的版本不出现
     */
    Map<Long, Integer> countByVersionIds(Collection<Long> termVersionIds);

    /**
     * 按一组主键读取；空集合不访问数据库。
     *
     * @param termPriceIds 价格 ID
     * @return 按 term_price_id 升序
     */
    List<CommercialTermPrice> findByIds(Collection<Long> termPriceIds);

    /**
     * 读取一组版本的全部价格；空集合不访问数据库。
     *
     * @param termVersionIds 版本 ID
     * @return 按 term_price_id 升序
     */
    List<CommercialTermPrice> findByVersionIds(Collection<Long> termVersionIds);
}
