package com.paytech.risk.test.mocker.control;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.test.mapper.control.UnderwritingTestMapper;

/**
 * 自动承保规则的本地数据 Mocker
 * <li>可让初始化规则退役（无 ACTIVE 规则），或换成带排除 MCC 清单的测试规则版本。</li>
 * <li>Clear 删除测试规则、恢复初始化规则为 ACTIVE，并删除本测试环境的全部承保版本。</li>
 *
 * @author Claude
 */
@TestComponent
public class UnderwritingRuleMocker implements Clear {
    /** 测试规则版本 ID（低于 Snowflake 范围，不与初始化行冲突）。 */
    public static final long TEST_RULE_VERSION_ID = 290901L;

    /** 测试规则版本号。 */
    public static final int TEST_RULE_VERSION_NO = 2;

    private final UnderwritingTestMapper mapper;

    public UnderwritingRuleMocker(UnderwritingTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 没有 ACTIVE 规则。 */
    public void mockNoActiveRule() {
        mapper.retireInitialRule();
    }

    /**
     * 用带排除清单的 v2 替换初始化规则，USD 阈值仍为 50,000
     *
     * @param excludedMccCodes 排除的 MCC，升序英文逗号分隔
     */
    public void mockRuleExcluding(String excludedMccCodes) {
        mapper.retireInitialRule();
        mapper.insertActiveRule(TEST_RULE_VERSION_ID, TEST_RULE_VERSION_NO, excludedMccCodes, 290902L, "USD", "50000");
    }

    /** 恢复规则初始化状态并删除承保版本。 */
    @Override
    public void clear() {
        mapper.deleteUnderwritings();
        mapper.deleteTestRules();
        mapper.restoreInitialRule();
    }
}
