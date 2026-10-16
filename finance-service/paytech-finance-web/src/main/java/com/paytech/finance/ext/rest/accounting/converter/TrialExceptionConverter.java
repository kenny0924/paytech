package com.paytech.finance.ext.rest.accounting.converter;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.domain.view.TrialExceptionView;
import com.paytech.finance.accounting.service.processor.request.ApproveProjectionRebuildRequest;
import com.paytech.finance.accounting.service.processor.request.StartProjectionRebuildRequest;
import com.paytech.finance.api.accounting.request.ApproveProjectionRebuildRequestBody;
import com.paytech.finance.api.accounting.request.StartProjectionRebuildRequestBody;
import com.paytech.finance.api.accounting.response.TrialExceptionResponseBody;

/** 试算异常处置的 Wire 模型与内部请求/结果转换。 */
@Component
public class TrialExceptionConverter {

    /** 转换发起请求。 */
    public StartProjectionRebuildRequest toRequest(String exceptionId, StartProjectionRebuildRequestBody body) {
        StartProjectionRebuildRequest request = new StartProjectionRebuildRequest();
        request.setExceptionId(Long.valueOf(exceptionId));
        request.setMakerId(Long.valueOf(body.getMakerId()));
        return request;
    }

    /** 转换批准请求。 */
    public ApproveProjectionRebuildRequest toRequest(String exceptionId, ApproveProjectionRebuildRequestBody body) {
        ApproveProjectionRebuildRequest request = new ApproveProjectionRebuildRequest();
        request.setExceptionId(Long.valueOf(exceptionId));
        request.setCheckerId(Long.valueOf(body.getCheckerId()));
        return request;
    }

    /** 转换异常结果。 */
    public TrialExceptionResponseBody toResponseBody(TrialExceptionView view) {
        TrialExceptionResponseBody body = new TrialExceptionResponseBody();
        body.setExceptionId(view.getExceptionId().toString());
        body.setRunId(view.getRunId().toString());
        body.setExceptionType(view.getExceptionType().getCode());
        body.setReferenceType(view.getReferenceType().getCode());
        body.setReferenceId(view.getReferenceId().toString());
        body.setDifferenceAmount(view.getDifferenceAmount().toPlainString());
        body.setStatus(view.getStatus().getCode());
        body.setResolutionType(view.getResolutionType());
        body.setResolutionRefId(text(view.getResolutionRefId()));
        body.setResolutionContentHash(view.getResolutionContentHash());
        body.setResolutionMakerId(text(view.getResolutionMakerId()));
        body.setResolutionCheckerId(text(view.getResolutionCheckerId()));
        body.setResolutionTime(view.getResolutionTime());
        return body;
    }

    /** 可空 Long 转十进制字符串。 */
    String text(Long value) {
        return value == null ? null : value.toString();
    }
}
