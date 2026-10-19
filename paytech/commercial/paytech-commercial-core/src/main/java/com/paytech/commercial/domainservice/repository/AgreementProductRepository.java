package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;

/** t_agreement_product 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementProductRepository {
    /**
     * 插入合同订阅行；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     *
     * @param agreementProduct 已准备 ID 与全部字段的新行
     */
    void insert(AgreementProduct agreementProduct);

    /**
     * 按主键读取。
     *
     * @param agreementProductId 订阅行 ID
     * @return 订阅行，不存在为 null
     */
    AgreementProduct findById(long agreementProductId);

    /**
     * 按唯一键读取。
     *
     * @param agreementId 合同 ID
     * @param productCode 产品代码
     * @return 订阅行，不存在为 null
     */
    AgreementProduct findByAgreementAndProduct(long agreementId, ProductCode productCode);

    /**
     * 读取一份合同的全部订阅。
     *
     * @param agreementId 合同 ID
     * @return 按主键升序的订阅行
     */
    List<AgreementProduct> findByAgreementId(long agreementId);

    /**
     * 读取一组合同的全部订阅；空集合不访问数据库。
     *
     * @param agreementIds 合同 ID
     * @return 按主键升序的订阅行
     */
    List<AgreementProduct> findByAgreementIds(Collection<Long> agreementIds);

    /**
     * 删除一行订阅；行已不存在时抛出并发冲突。
     *
     * @param agreementProduct 已读取的订阅行
     */
    void delete(AgreementProduct agreementProduct);
}
