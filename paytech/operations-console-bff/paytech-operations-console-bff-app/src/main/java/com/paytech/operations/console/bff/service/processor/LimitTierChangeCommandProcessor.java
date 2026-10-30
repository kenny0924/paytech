package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.service.model.risklimittier.LabeledTierChangeBody;
import com.paytech.operations.console.bff.service.processor.request.LimitTierChangeCommandRequest;

import lombok.AllArgsConstructor;

/**
 * 撤回、审批与驳回档位变更申请（L5—L9）：页面 Body 单次转发 Risk，回执补显示名
 * <li>审批级别、四眼与撤回人身份由 risk-decision 校验；Risk 的 404 / 409 / 422 原样返回，不重试。</li>
 */
@Component
@AllArgsConstructor
public class LimitTierChangeCommandProcessor extends AbstractProcessor<LimitTierChangeCommandRequest, LabeledTierChangeBody> {
    private final LimitTierLabeler labeler;

    @Override
    protected LabeledTierChangeBody processRequest(LimitTierChangeCommandRequest request) {
        return labeler.label(request.getCommand().get());
    }
}
