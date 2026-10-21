package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.AccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartAccountChangeResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartAccountListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartOfAccountsListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateAccountingPeriodRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateAccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateChartAccountRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateChartAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFinanceEntityRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFinanceEntityResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFiscalCalendarRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFiscalCalendarResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.FinanceEntityResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.FiscalCalendarResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.OpenAccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.UpdateChartAccountRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * ledger 账务基础资料的本地端口；业务校验、错误码与事实归 ledger
 * <li>命令以会话 Account ID 与 Session ID 作为操作人 Header 转发；查询不携带操作人。</li>
 *
 * @author Claude
 */
public interface FinanceSetupClient {

    /**
     * 财务主体分页
     *
     * @param status 可选状态
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 财务主体分页
     */
    PagingResponseBody<FinanceEntityResponseBody> listFinanceEntities(String status, int page, int size);

    /**
     * 新建财务主体
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 新主体主键
     */
    CreateFinanceEntityResponseBody createFinanceEntity(OperatorContext context, CreateFinanceEntityRequestBody request);

    /**
     * 财务日历分页
     *
     * @param entityType 可选主体类型
     * @param entityId 可选主体标识
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 财务日历分页
     */
    PagingResponseBody<FiscalCalendarResponseBody> listFiscalCalendars(String entityType, String entityId, int page,
        int size);

    /**
     * 新建财务日历
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 新日历主键
     */
    CreateFiscalCalendarResponseBody createFiscalCalendar(OperatorContext context,
        CreateFiscalCalendarRequestBody request);

    /**
     * 日历下的会计期间分页
     *
     * @param fiscalCalendarId 财务日历主键
     * @param fiscalYear 可选财年
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 会计期间分页
     */
    PagingResponseBody<AccountingPeriodResponseBody> listAccountingPeriods(String fiscalCalendarId, Integer fiscalYear,
        int page, int size);

    /**
     * 新建会计期间
     *
     * @param context 操作人
     * @param fiscalCalendarId 财务日历主键
     * @param request 页面请求
     * @return 新期间主键
     */
    CreateAccountingPeriodResponseBody createAccountingPeriod(OperatorContext context, String fiscalCalendarId,
        CreateAccountingPeriodRequestBody request);

    /**
     * 开放会计期间
     *
     * @param context 操作人
     * @param accountingPeriodId 会计期间主键
     * @return 期间主键与当前状态
     */
    OpenAccountingPeriodResponseBody openAccountingPeriod(OperatorContext context, String accountingPeriodId);

    /**
     * 全部科目表
     *
     * @return 科目表列表
     */
    ChartOfAccountsListResponseBody listChartsOfAccounts();

    /**
     * 科目表下的科目
     *
     * @param chartCode 科目表代码
     * @param postingAllowed 可选是否可过账
     * @param accountStatus 可选启停状态
     * @return 科目列表
     */
    ChartAccountListResponseBody listChartAccounts(String chartCode, Boolean postingAllowed, String accountStatus);

    /**
     * 新增科目
     *
     * @param context 操作人
     * @param chartCode 科目表代码
     * @param request 页面请求
     * @return 新科目主键
     */
    CreateChartAccountResponseBody createChartAccount(OperatorContext context, String chartCode,
        CreateChartAccountRequestBody request);

    /**
     * 修改科目名称、编号与上级
     *
     * @param context 操作人
     * @param chartCode 科目表代码
     * @param chartAccountId 科目主键
     * @param request 页面请求
     * @return 修改后的科目
     */
    ChartAccountResponseBody updateChartAccount(OperatorContext context, String chartCode, String chartAccountId,
        UpdateChartAccountRequestBody request);

    /**
     * 停用科目
     *
     * @param context 操作人
     * @param chartCode 科目表代码
     * @param chartAccountId 科目主键
     * @return 停用后的科目
     */
    ChartAccountResponseBody disableChartAccount(OperatorContext context, String chartCode, String chartAccountId);

    /**
     * 启用科目
     *
     * @param context 操作人
     * @param chartCode 科目表代码
     * @param chartAccountId 科目主键
     * @return 启用后的科目
     */
    ChartAccountResponseBody enableChartAccount(OperatorContext context, String chartCode, String chartAccountId);

    /**
     * 科目变更记录分页，按变更时间倒序
     *
     * @param chartCode 科目表代码
     * @param chartAccountId 可选科目主键
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 变更记录分页
     */
    PagingResponseBody<ChartAccountChangeResponseBody> listChartAccountChanges(String chartCode, String chartAccountId,
        int page, int size);
}
