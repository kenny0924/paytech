package com.paytech.risk.test.base;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.test.checker.decision.VariableDataChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker;

/**
 * 统一变量管理接口测试的公共入口辅助
 * <li>只提供路径、Gateway 可信身份 Header 与 Request ID；不含 @Test，各场景类自行组织用例。</li>
 *
 * @author Kenny
 */
public abstract class AbstractVariableApiTest extends MockTestBase {
    /** 变量目录与单变量资源根路径。 */
    protected static final String VARIABLES = "/decision-variables/v1/variables";

    /** 可信操作者 Account ID，写入的审计字段取自该值。 */
    protected static final long ACTOR_ID = 70001L;

    @Autowired
    protected VariableMocker variables;

    @Autowired
    protected VariableDataChecker variableData;

    /** 单个变量的资源路径。 */
    protected static String variablePath(String code) {
        return VARIABLES + "/" + code;
    }

    /** Gateway 转发的 Operations 可信身份及新的 Request ID。 */
    protected static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal("risk-variable-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR_ID));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    /** 与服务端命令时间同精度的当前时刻，用于断言写入时间下界。 */
    protected static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
