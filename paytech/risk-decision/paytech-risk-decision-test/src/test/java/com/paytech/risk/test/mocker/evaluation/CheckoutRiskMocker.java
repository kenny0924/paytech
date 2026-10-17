package com.paytech.risk.test.mocker.evaluation;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.test.mapper.evaluation.CheckoutRiskTestMapper;

/**
 * 结账风控的本地数据 Mocker
 * <li>可把结账运行期点版本（220001）超时改为 -1，强制每次评估超时；Clear 先恢复原超时。</li>
 * <li>可把已发布规则引用的变量标为删除，构造运行期读取失败。</li>
 * <li>经业务入口创建的结账评估、快照、执行发布与 T_CHK 前缀配置由 Clear 统一回收，用例无需逐项登记。</li>
 *
 * @author Kenny
 */
@TestComponent
public class CheckoutRiskMocker implements Clear {
    private final CheckoutRiskTestMapper mapper;

    /** 被改写前的点版本超时；为空表示未改写。 */
    private Integer originalTimeoutMillis;

    public CheckoutRiskMocker(CheckoutRiskTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 结账运行期点版本超时改为 -1：任何耗时都超过上限，评估确定记为 TIMED_OUT。 */
    public void mockEvaluationTimeout() {
        if (originalTimeoutMillis == null) {
            originalTimeoutMillis = mapper.pointTimeoutMillis();
        }
        mapper.updatePointTimeoutMillis(-1);
    }

    /** 已发布规则引用的变量被标为删除：执行内容读取失败，评估记为 FAILED。 */
    public void mockVariableDeleted(String variableCode) {
        mapper.markVariableDeleted(variableCode);
    }

    /** 恢复点版本超时并删除本测试环境的结账风控数据。 */
    @Override
    public void clear() {
        if (originalTimeoutMillis != null) {
            mapper.updatePointTimeoutMillis(originalTimeoutMillis);
            originalTimeoutMillis = null;
        }
        mapper.deleteAll();
    }
}
