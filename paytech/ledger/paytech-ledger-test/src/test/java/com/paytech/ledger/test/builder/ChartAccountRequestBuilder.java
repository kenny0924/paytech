package com.paytech.ledger.test.builder;

import com.paytech.ledger.api.request.CreateChartAccountRequestBody;
import com.paytech.ledger.api.request.UpdateChartAccountRequestBody;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.NormalSide;

/** 科目维护请求的合法模板；返回可修改的正式 RequestBody，用例按场景覆盖字段（含置空与非法值）。 */
public final class ChartAccountRequestBuilder {

    private ChartAccountRequestBuilder() {
    }

    /** 资产类借方末级科目模板。 */
    public static CreateChartAccountRequestBody leaf(String accountNo, String accountCode, String parentAccountNo) {
        return create(accountNo, accountCode, AccountClass.ASSET, NormalSide.DEBIT, true, parentAccountNo);
    }

    /** 新增科目模板；名称由代码派生。 */
    public static CreateChartAccountRequestBody create(String accountNo, String accountCode, AccountClass accountClass,
        NormalSide normalSide, boolean postingAllowed, String parentAccountNo) {
        CreateChartAccountRequestBody body = new CreateChartAccountRequestBody();
        body.setAccountNo(accountNo);
        body.setAccountCode(accountCode);
        body.setAccountName("科目 " + accountCode);
        body.setAccountClass(accountClass.getCode());
        body.setNormalSide(normalSide.getCode());
        body.setPostingAllowed(postingAllowed);
        body.setParentAccountNo(parentAccountNo);
        return body;
    }

    /** 修改科目模板：三项均为修改后的完整值。 */
    public static UpdateChartAccountRequestBody update(String accountName, String accountNo, String parentAccountNo) {
        UpdateChartAccountRequestBody body = new UpdateChartAccountRequestBody();
        body.setAccountName(accountName);
        body.setAccountNo(accountNo);
        body.setParentAccountNo(parentAccountNo);
        return body;
    }
}
