package com.paytech.channel.connector.ext.rest.converter;

import com.paytech.channel.connector.api.candidate.request.MidCriteriaBody;
import com.paytech.channel.connector.service.processor.request.MidCriteria;

/** 交易维度的协议转换；null 原样返回，由 Processor 按用途校验。 */
final class MidCriteriaConverter {
    private MidCriteriaConverter() {
    }

    /** 协议维度转为 Processor 维度。 */
    static MidCriteria convert(MidCriteriaBody body) {
        if (body == null) {
            return null;
        }
        var criteria = new MidCriteria();
        criteria.setPaymentMethodCode(body.getPaymentMethodCode());
        criteria.setSchemeCode(body.getSchemeCode());
        criteria.setCountryCode(body.getCountryCode());
        criteria.setCurrency(body.getCurrency());
        criteria.setMccCode(body.getMccCode());
        return criteria;
    }
}
