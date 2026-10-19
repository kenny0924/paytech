package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;

/** t_agreement_product 的持久化访问。 */
public interface AgreementProductMapper {
    /** 插入一行合同订阅，返回影响行数。 */
    int insert(AgreementProduct agreementProduct);

    /** 按主键读取，不存在返回 null。 */
    AgreementProduct selectById(@Param("agreementProductId") long agreementProductId);

    /** 按唯一键（合同、产品）读取，不存在返回 null。 */
    AgreementProduct selectByAgreementAndProduct(@Param("agreementId") long agreementId,
        @Param("productCode") ProductCode productCode);

    /** 读取一份合同的全部订阅，按主键升序。 */
    List<AgreementProduct> selectByAgreementId(@Param("agreementId") long agreementId);

    /** 读取一组合同的全部订阅，按主键升序；agreementIds 必须非空。 */
    List<AgreementProduct> selectByAgreementIds(@Param("agreementIds") Collection<Long> agreementIds);

    /** 按主键删除一行订阅，返回影响行数。 */
    int deleteById(@Param("agreementProductId") long agreementProductId);
}
