package com.paytech.payment.execution.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;

/** 支付工具 SQL 映射 */
@Mapper
public interface PaymentInstrumentTokenMapper {

    /** 按原提交范围查询工具 */
    PaymentInstrumentToken findTokenBySubmission(@Param("clientId") long clientId, @Param("intentId") long intentId,
        @Param("sourceSubmissionId") long sourceSubmissionId);

    /** 在支付意图仍可接受工具时保存绑定 */
    int insertToken(PaymentInstrumentToken token);

    /** 查询工具凭据对应的原记录。 */
    PaymentInstrumentToken findTokenByValue(@Param("token") String token);

    /** 按内部 ID 查询原工具。 */
    PaymentInstrumentToken findToken(@Param("tokenId") long tokenId);

    /** 条件锁定可用工具，条件不匹配返回空。 */
    Long lockAvailableToken(PaymentInstrumentToken token);

}
