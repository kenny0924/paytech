package com.paytech.ledger.app.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.ledger.api.request.CreateChartAccountRequestBody;
import com.paytech.ledger.api.request.UpdateChartAccountRequestBody;
import com.paytech.ledger.api.response.ChartAccountChangeResponseBody;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.domain.view.ChartAccountChangeView;
import com.paytech.ledger.service.processor.request.ChartAccountStatusRequest;
import com.paytech.ledger.service.processor.request.CreateChartAccountRequest;
import com.paytech.ledger.service.processor.request.UpdateChartAccountRequest;

/** 科目维护与变更记录接口的 Wire 模型转换：字符串 ID 转 Long，Code 转内部枚举，操作人取自 Header。 */
@Component
public class ChartOfAccountsConverter {

    /** 转换新增科目请求。 */
    public CreateChartAccountRequest toRequest(String chartCode, CreateChartAccountRequestBody body,
        String operatorId) {
        CreateChartAccountRequest request = new CreateChartAccountRequest();
        request.setChartCode(chartCode);
        request.setAccountNo(body.getAccountNo());
        request.setAccountCode(body.getAccountCode());
        request.setAccountName(body.getAccountName());
        request.setAccountClass(CodeEnumConverter.parse(AccountClass.class, body.getAccountClass()));
        request.setNormalSide(CodeEnumConverter.parse(NormalSide.class, body.getNormalSide()));
        request.setPostingAllowed(body.getPostingAllowed());
        request.setParentAccountNo(body.getParentAccountNo());
        request.setOperatorId(operatorId);
        return request;
    }

    /** 转换修改科目请求。 */
    public UpdateChartAccountRequest toRequest(String chartCode, String chartAccountId,
        UpdateChartAccountRequestBody body, String operatorId) {
        UpdateChartAccountRequest request = new UpdateChartAccountRequest();
        request.setChartCode(chartCode);
        request.setChartAccountId(Long.valueOf(chartAccountId));
        request.setAccountName(body.getAccountName());
        request.setAccountNo(body.getAccountNo());
        request.setParentAccountNo(body.getParentAccountNo());
        request.setOperatorId(operatorId);
        return request;
    }

    /** 转换停用 / 启用请求。 */
    public ChartAccountStatusRequest toStatusRequest(String chartCode, String chartAccountId, String operatorId) {
        ChartAccountStatusRequest request = new ChartAccountStatusRequest();
        request.setChartCode(chartCode);
        request.setChartAccountId(Long.valueOf(chartAccountId));
        request.setOperatorId(operatorId);
        return request;
    }

    /** 转换一条变更记录。 */
    public ChartAccountChangeResponseBody toResponseBody(ChartAccountChangeView view) {
        ChartAccountChangeResponseBody body = new ChartAccountChangeResponseBody();
        body.setChangeId(view.getChangeId().toString());
        body.setChartAccountId(view.getChartAccountId().toString());
        body.setAccountNo(view.getAfterAccountNo());
        body.setChangeType(view.getChangeType().getCode());
        body.setBeforeAccountNo(view.getBeforeAccountNo());
        body.setAfterAccountNo(view.getAfterAccountNo());
        body.setBeforeParentAccountNo(view.getBeforeParentAccountNo());
        body.setAfterParentAccountNo(view.getAfterParentAccountNo());
        body.setBeforeAccountName(view.getBeforeAccountName());
        body.setAfterAccountName(view.getAfterAccountName());
        body.setBeforeAccountStatus(code(view.getBeforeAccountStatus()));
        body.setAfterAccountStatus(code(view.getAfterAccountStatus()));
        body.setOperatorId(view.getOperatorId());
        body.setChangeTime(view.getChangeTime());
        return body;
    }

    /** 可空状态的稳定 Code。 */
    String code(ChartAccountStatus status) {
        return status == null ? null : status.getCode();
    }
}
