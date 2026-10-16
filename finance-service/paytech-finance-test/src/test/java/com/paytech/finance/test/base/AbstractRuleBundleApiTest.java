package com.paytech.finance.test.base;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.finance.accounting.core.enums.ReferenceDataStatus;
import com.paytech.finance.api.accounting.request.ReviewRuleVersionRequestBody;
import com.paytech.finance.api.accounting.request.SubmitRuleVersionRequestBody;
import com.paytech.finance.test.builder.RuleVersionRequestBuilder;
import com.paytech.finance.test.checker.FinanceResponseChecker;
import com.paytech.finance.test.dal.AccountingTestMapper;

/**
 * 记账规则 Bundle 接口测试的公共调用
 * <li>Bundle STANDARD_TRANSACTION（5001）与第 1 版（5101，ACTIVE）来自初始化 DML，每例由清理组件恢复，无需类级准备。</li>
 */
public abstract class AbstractRuleBundleApiTest extends MockTestBase {
    /** 规则 Bundle 接口根路径。 */
    protected static final String PATH = "/accounting-rule-bundles/v1";

    /** 初始化 DML 预置的 Bundle 代码。 */
    protected static final String BUNDLE_CODE = "STANDARD_TRANSACTION";

    /** 初始化 DML 预置的 Bundle 主键。 */
    protected static final long BUNDLE_ID = 5001L;

    /** 初始化 DML 预置的第 1 版主键。 */
    protected static final long INITIAL_VERSION_ID = 5101L;

    /** 初始化 DML 预置版本号。 */
    protected static final long INITIAL_BUNDLE_VER = 1L;

    /** Bundle 启用状态（Bundle 状态目录无独立枚举）。 */
    protected static final String BUNDLE_ACTIVE = ReferenceDataStatus.ACTIVE.getCode();

    /** 编制人。 */
    protected static final String MAKER_ID = "81001";

    /** 与编制人不同的复核人。 */
    protected static final String CHECKER_ID = "81002";

    @Autowired
    protected AccountingTestMapper testMapper;

    /** 一分钟前（微秒精度），批准后立即生效。 */
    protected static Instant pastEffectiveFrom() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.MINUTES);
    }

    /** 标准 12 行、立即生效的请求模板。 */
    protected static RuleVersionRequestBuilder version() {
        return RuleVersionRequestBuilder.template(MAKER_ID, pastEffectiveFrom());
    }

    /** 创建规则版本。 */
    protected FinanceResponseChecker createVersion(String bundleCode, RuleVersionRequestBuilder builder) {
        return post(PATH + "/" + bundleCode + "/versions", builder.build());
    }

    /** 在标准 Bundle 下创建规则版本。 */
    protected FinanceResponseChecker createVersion(RuleVersionRequestBuilder builder) {
        return createVersion(BUNDLE_CODE, builder);
    }

    /** 提交规则版本。 */
    protected FinanceResponseChecker submit(long bundleVer, String makerId) {
        SubmitRuleVersionRequestBody body = new SubmitRuleVersionRequestBody();
        body.setMakerId(makerId);
        return post(versionPath(bundleVer) + "/submit", body);
    }

    /** 批准规则版本。 */
    protected FinanceResponseChecker approve(long bundleVer, String checkerId) {
        return post(versionPath(bundleVer) + "/approve", review(checkerId));
    }

    /** 驳回规则版本。 */
    protected FinanceResponseChecker reject(long bundleVer, String checkerId) {
        return post(versionPath(bundleVer) + "/reject", review(checkerId));
    }

    /** 读取 Bundle 及版本摘要。 */
    protected FinanceResponseChecker getBundle(String bundleCode) {
        return get(PATH + "/" + bundleCode);
    }

    /** 读取版本详情。 */
    protected FinanceResponseChecker getVersion(long bundleVer) {
        return get(versionPath(bundleVer));
    }

    /** 创建并以模板编制人提交，返回提交响应。 */
    protected FinanceResponseChecker createSubmitted(RuleVersionRequestBuilder builder) {
        long bundleVer = Long.parseLong(createVersion(builder).assertSuccess(201).text("bundleVer"));
        return submit(bundleVer, MAKER_ID).assertSuccess(200);
    }

    /** 版本资源路径。 */
    protected String versionPath(long bundleVer) {
        return PATH + "/" + BUNDLE_CODE + "/versions/" + bundleVer;
    }

    /** 复核请求体。 */
    protected ReviewRuleVersionRequestBody review(String checkerId) {
        ReviewRuleVersionRequestBody body = new ReviewRuleVersionRequestBody();
        body.setCheckerId(checkerId);
        return body;
    }
}
