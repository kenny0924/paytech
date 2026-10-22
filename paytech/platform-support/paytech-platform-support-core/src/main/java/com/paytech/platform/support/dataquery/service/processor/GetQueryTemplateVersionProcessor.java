package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionDetail;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.service.processor.request.GetQueryTemplateVersionRequest;

import lombok.AllArgsConstructor;

/** 模板某个版本的内容：复核 DATA_QUERY_READ，版本不存在返回 404。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetQueryTemplateVersionProcessor
    extends AbstractProcessor<GetQueryTemplateVersionRequest, QueryTemplateVersionDetail> {

    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;

    @Override
    protected QueryTemplateVersionDetail processRequest(GetQueryTemplateVersionRequest request) {
        support.requirePermission(request.getActor(), request.getSession(), DataQueryManagementSupport.READ_PERMISSION);
        QueryTemplateVersionDetail version = templates.findQueryTemplateVersion(request.getTemplateId(),
            request.getVersionNo());
        if (version == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return version;
    }
}
