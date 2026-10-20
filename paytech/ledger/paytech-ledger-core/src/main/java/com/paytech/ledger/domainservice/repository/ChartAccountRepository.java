package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.enums.ChartAccountStatus;

/** t_chart_account：会计科目读取与维护。 */
public interface ChartAccountRepository {

    /** 插入科目；同一科目表内编号或代码重复时以对应错误码拒绝。 */
    void insert(ChartAccount account);

    /** 按主键读取科目，不存在返回空。 */
    ChartAccount findById(long id);

    /** 按主键升序读取一组科目；空集合返回空列表。 */
    List<ChartAccount> findByIds(List<Long> ids);

    /** 按科目表和科目代码读取，不存在返回空。 */
    ChartAccount findByChartAndCode(long chartId, String accountCode);

    /** 按科目表和科目编号读取，不存在返回空。 */
    ChartAccount findByChartAndNo(long chartId, String accountNo);

    /** 读取科目表下的科目；postingAllowed、accountStatus 为空时不过滤，按科目编号升序。 */
    List<ChartAccount> findByChart(long chartId, Boolean postingAllowed, ChartAccountStatus accountStatus);

    /** 读取直接下级科目，按科目编号升序。 */
    List<ChartAccount> findChildren(long parentAccountId);

    /**
     * 修改名称、编号与上级，成功后同步原对象
     * <li>编号与同表其他科目冲突时以 CHART_ACCOUNT_NO_DUPLICATE 拒绝；行数不为 1 时并发冲突。</li>
     */
    void updateStructure(ChartAccount account, String accountName, String accountNo, Long parentAccountId);

    /** 以原状态为条件改变启停状态，成功后同步原对象；状态已被并发改变时并发冲突。 */
    void updateStatus(ChartAccount account, ChartAccountStatus to);
}
