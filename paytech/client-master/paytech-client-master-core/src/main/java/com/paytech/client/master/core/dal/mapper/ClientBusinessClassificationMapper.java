package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.ClientBusinessClassification;

/** t_client_business_classification 的持久化查询。 */
public interface ClientBusinessClassificationMapper {
    /** 读取本 Client Checkout 范围内的 client_business_classification 记录，保留历史和未生效事实供业务判断。 */
    List<ClientBusinessClassification> findForCheckout(@Param("clientId") long clientId);

    /** 某经营资料的全部分类。 */
    List<com.paytech.client.master.core.dal.dataobject.ClientClassification> findByProfile(@Param("businessProfileId") long businessProfileId);

    /** 删除某经营资料的全部分类，返回影响行数。 */
    int deleteByProfile(@Param("businessProfileId") long businessProfileId);

    /** 新增一条分类，返回影响行数。 */
    int insert(@Param("row") com.paytech.client.master.core.dal.dataobject.ClientClassification row);
}
