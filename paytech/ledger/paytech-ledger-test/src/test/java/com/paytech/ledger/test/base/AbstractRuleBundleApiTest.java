package com.paytech.ledger.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;
import com.paytech.ledger.test.dal.AccountingTestMapper;

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

    /** 编制人（以 X-Gateway-Actor-Subject 发送）。 */
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
        return RuleVersionRequestBuilder.template(pastEffectiveFrom());
    }

    /** 以模板编制人创建规则版本。 */
    protected LedgerResponseChecker createVersion(String bundleCode, RuleVersionRequestBuilder builder) {
        return postAs(PATH + "/" + bundleCode + "/versions", MAKER_ID, builder.build());
    }

    /** 在标准 Bundle 下创建规则版本。 */
    protected LedgerResponseChecker createVersion(RuleVersionRequestBuilder builder) {
        return createVersion(BUNDLE_CODE, builder);
    }

    /** 以指定提交人（操作人 Header）提交规则版本。 */
    protected LedgerResponseChecker submit(long bundleVer, String makerId) {
        return postAs(versionPath(bundleVer) + "/submit", makerId, null);
    }

    /** 以指定复核人（操作人 Header）批准规则版本。 */
    protected LedgerResponseChecker approve(long bundleVer, String checkerId) {
        return postAs(versionPath(bundleVer) + "/approve", checkerId, null);
    }

    /** 以指定复核人（操作人 Header）驳回规则版本。 */
    protected LedgerResponseChecker reject(long bundleVer, String checkerId) {
        return postAs(versionPath(bundleVer) + "/reject", checkerId, null);
    }

    /** 以指定操作人（Header）作废规则草稿。 */
    protected LedgerResponseChecker voidVersion(long bundleVer, String makerId) {
        return postAs(versionPath(bundleVer) + "/void", makerId, null);
    }

    /** 读取全部规则集。 */
    protected LedgerResponseChecker listBundles() {
        return get(PATH);
    }

    /** 读取 Bundle 及版本摘要。 */
    protected LedgerResponseChecker getBundle(String bundleCode) {
        return get(PATH + "/" + bundleCode);
    }

    /** 读取版本详情。 */
    protected LedgerResponseChecker getVersion(long bundleVer) {
        return get(versionPath(bundleVer));
    }

    /** 创建并以模板编制人提交，返回提交响应。 */
    protected LedgerResponseChecker createSubmitted(RuleVersionRequestBuilder builder) {
        long bundleVer = Long.parseLong(createVersion(builder).assertSuccess(201).text("bundleVer"));
        return submit(bundleVer, MAKER_ID).assertSuccess(200);
    }

    /** Bundle 5001 当前版本指针与乐观锁版本。 */
    protected void assertPointer(long currentVerId, long version) {
        Map<String, Object> pointer = testMapper.selectRuleBundlePointer(BUNDLE_ID);
        assertEquals(currentVerId, ((Number)pointer.get("current_ver_id")).longValue(), "current_ver_id");
        assertEquals(version, ((Number)pointer.get("version")).longValue(), "Bundle version");
    }

    /** 版本资源路径。 */
    protected String versionPath(long bundleVer) {
        return PATH + "/" + BUNDLE_CODE + "/versions/" + bundleVer;
    }
}
