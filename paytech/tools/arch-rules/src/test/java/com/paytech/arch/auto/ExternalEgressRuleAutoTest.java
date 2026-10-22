package com.paytech.arch.auto;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.rule.ExternalEgressRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;

import org.junit.jupiter.api.Test;

/**
 * R15 唯一对外出口：外部 SDK 依赖与引用只在例外模块，JDK HttpClient 直接发送只在 channel-connector-protocol 出站包。
 *
 * @see com.paytech.arch.rule.ExternalEgressRule#check(ServiceArchitectureScope)
 * @see com.paytech.arch.rule.ExternalEgressRule#checkDependencyGraph(java.util.Collection)
 * @author Claude
 */
class ExternalEgressRuleAutoTest {

    /** 出站包 fixture：代替真实的 com.paytech.channel.connector.protocol.ext.outbound。 */
    private static final String OUTBOUND = Fixtures.classes("r15.pass.outbound");

    /** 白名单模块声明外部 SDK、test 范围与 -test 模块的 SDK 依赖都不计入。 */
    @Test
    void test_normalFlow() {
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r15-pass"))).none();
    }

    /** 未登记模块依赖 AWS SDK、OkHttp（含 runtime 范围），以及白名单模块依赖未登记的 SDK，均违规。 */
    @Test
    void test_sdkDependencyOutsideAllowlist() {
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r15-violation")))
            .allFrom("R15")
            .count(3)
            .contains("paytech-ledger-core", "software.amazon.awssdk:s3")
            .contains("paytech-ledger-core", "com.squareup.okhttp3:okhttp")
            .contains("paytech-platform-support-delivery", "io.minio:minio");
    }

    /** 出站包的类直接发送、protocol 其他包只构造 HttpClient、例外模块引用 AWS SDK，均通过。 */
    @Test
    void test_allowedSendAndSdkUse() {
        ServiceArchitectureScope connector = Fixtures.scope("channel-connector")
            .module(Fixtures.module("channel-connector", "protocol"), OUTBOUND)
            .module(Fixtures.module("channel-connector", "protocol"), Fixtures.classes("r15.pass.protocolcore"))
            .build();
        ViolationChecker.of(new ExternalEgressRule(OUTBOUND).check(connector)).none();

        ServiceArchitectureScope platform = Fixtures.scope("platform-support")
            .module(Fixtures.module("platform-support", "delivery"), Fixtures.classes("r15.pass.deliverycore"))
            .build();
        ViolationChecker.of(new ExternalEgressRule(OUTBOUND).check(platform)).none();
    }

    /** core 直接调用 send / sendAsync、protocol 出站包以外直接 send、core 引用 AWS SDK，均违规。 */
    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("channel-connector")
            .module(Fixtures.module("channel-connector", "core"), Fixtures.classes("r15.violation.connectorcore"))
            .module(Fixtures.module("channel-connector", "protocol"), Fixtures.classes("r15.violation.protocolother"))
            .build();

        ViolationChecker.of(new ExternalEgressRule(OUTBOUND).check(scope))
            .allFrom("R15")
            .containsMessage("DirectChannelCaller", "HttpClient.send(")
            .containsMessage("DirectChannelCaller", "HttpClient.sendAsync(")
            .containsMessage("ProtocolShortcut", "HttpClient.send(")
            .containsMessage("GeocodeSdkCaller", "software.amazon.awssdk.fixture.SdkClientStub");
    }

    /** 出站包名相同但不归属 channel-connector-protocol 模块的类不能直接发送。 */
    @Test
    void test_outboundPackageOutsideProtocolModule() {
        ServiceArchitectureScope scope = Fixtures.scope("channel-connector")
            .module(Fixtures.module("channel-connector", "core"), OUTBOUND)
            .build();

        ViolationChecker.of(new ExternalEgressRule(OUTBOUND).check(scope))
            .allFrom("R15")
            .count(1)
            .containsMessage("ChannelSender", "HttpClient.send(");
    }
}
