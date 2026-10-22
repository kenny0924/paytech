package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R15：唯一对外出口（2026-10-20 统一对外渠道出口决定，technique 第 6.6 节）。
 *
 * <p>
 * 规则一：外部 SDK（AWS SDK、MinIO、OkHttp、Apache HttpClient 5）只允许出现在 {@link #SDK_ALLOWLIST} 登记的模块：POM 非 test
 * 依赖（依赖图部分，Reactor 级执行）与生产类引用（类部分，服务级执行）都检查。{@code -test} 模块不进入制品，不检查；
 * 传递依赖不计入依赖图部分，由类部分拦截实际引用。
 *
 * <p>
 * 规则二：直接调用 {@code java.net.http.HttpClient#send / sendAsync} 只允许出现在 channel-connector-protocol 的
 * {@value #OUTBOUND_PACKAGE} 包。经 {@code JdkClientHttpRequestFactory} / {@code HttpServiceClientFactory} 构造的内部
 * RestClient 由框架类调用 send，不受影响。只检查各模块 {@code target/classes} 的生产类，测试类不在范围内。
 *
 * <p>
 * 白名单与 {@code agent-docs/decisions/2026-10-22-unified-outbound-exception-list.md} 逐项对应，新增例外须同步修改两处。门禁只能拦截常见写法，
 * 不能替代代码评审与网络层出口强制。
 *
 * @author Claude
 */
public final class ExternalEgressRule implements ArchitectureRule, DependencyGraphRule {

    /** AWS SDK v2。 */
    public static final String AWS_SDK = "software.amazon.awssdk";

    /** MinIO Java SDK。 */
    public static final String MINIO = "io.minio";

    /** OkHttp。 */
    public static final String OKHTTP = "com.squareup.okhttp3";

    /** Apache HttpClient 5 客户端。 */
    public static final String HTTP_CLIENT5 = "org.apache.httpcomponents.client5";

    /** Apache HttpClient 5 核心。 */
    public static final String HTTP_CORE5 = "org.apache.httpcomponents.core5";

    /** 外部 SDK 的 Maven groupId 到其 Java 包前缀。 */
    public static final Map<String, List<String>> SDK_PACKAGES = Map.of(AWS_SDK, List.of("software.amazon.awssdk"),
        MINIO, List.of("io.minio"), OKHTTP, List.of("okhttp3"), HTTP_CLIENT5, List.of("org.apache.hc.client5"),
        HTTP_CORE5, List.of("org.apache.hc.core5"));

    /**
     * 允许依赖外部 SDK 的模块（{@code groupId:artifactId} → 允许的 SDK groupId）
     * <li>platform-support-delivery：O3 通知 SES v2 / SQS（AWS SDK）、O4 Client Webhook 推送（Apache HttpClient 5 + 地址防护）。</li>
     * <li>platform-support-file-document：O5 对象存储 S3（AWS SDK）/ 自建 MinIO（MinIO SDK，其 OkHttp 为传递依赖）。</li>
     * <li>acquiring-app、counterparty-app：Apache HttpClient 5 只作为 Spring Boot 内部 RestClient 传输（{@code factory: http-components}，
     * 目标为 {@code lb://} 内部服务，关闭自动重试），不是对外出口，登记为 I1 / I2。</li>
     */
    public static final Map<String, Set<String>> SDK_ALLOWLIST = Map.of(
        "com.paytech:paytech-platform-support-delivery", Set.of(AWS_SDK, HTTP_CLIENT5, HTTP_CORE5),
        "com.paytech:paytech-platform-support-file-document", Set.of(AWS_SDK, MINIO, OKHTTP),
        "com.paytech:paytech-acquiring-app", Set.of(HTTP_CLIENT5, HTTP_CORE5),
        "com.paytech:paytech-counterparty-app", Set.of(HTTP_CLIENT5, HTTP_CORE5));

    /** 唯一允许直接调用 JDK HttpClient 发送请求的模块。 */
    public static final String OUTBOUND_ARTIFACT = "com.paytech:paytech-channel-connector-protocol";

    /** 唯一允许直接调用 JDK HttpClient 发送请求的包。 */
    public static final String OUTBOUND_PACKAGE = "com.paytech.channel.connector.protocol.ext.outbound";

    private static final String JDK_HTTP_CLIENT = "java.net.http.HttpClient";

    private static final Set<String> SEND_METHODS = Set.of("send", "sendAsync");

    private final String outboundPackage;

    /**
     * 出站包为 {@value #OUTBOUND_PACKAGE}。
     */
    public ExternalEgressRule() {
        this(OUTBOUND_PACKAGE);
    }

    /**
     * 指定唯一允许直接发送的包；仍要求该包的类归属 {@value #OUTBOUND_ARTIFACT}。
     *
     * @param outboundPackage 出站包名
     */
    public ExternalEgressRule(String outboundPackage) {
        this.outboundPackage = outboundPackage;
    }

    @Override
    public String id() {
        return "R15";
    }

    @Override
    public String description() {
        return "外部 SDK 只允许出现在出口例外模块，直接调用 JDK HttpClient 发送只允许在 channel-connector-protocol ext.outbound";
    }

    /**
     * 依赖图部分：非 test 模块声明的非 test 依赖中，外部 SDK 只允许出现在白名单模块的允许范围内。
     *
     * @param modules 模块集合
     * @return 违规
     */
    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            if (module.identity().role() == ModuleRole.TEST) {
                continue;
            }
            Set<String> allowed = SDK_ALLOWLIST.getOrDefault(module.coordinate(), Set.of());
            for (MavenDependency dependency : module.productionDependencies()) {
                if (SDK_PACKAGES.containsKey(dependency.groupId()) && !allowed.contains(dependency.groupId())) {
                    violations.add(new RuleViolation(id(), module.coordinate(), "模块依赖了外部 SDK "
                        + dependency.coordinate() + "；对外第三方调用应经 channel-connector，确属出口例外须登记例外清单与 R15 白名单"));
                }
            }
        }
        return violations;
    }

    /**
     * 服务级：依赖图部分加生产类的外部 SDK 引用与 JDK HttpClient 发送调用。
     *
     * @param scope 检查对象
     * @return 违规
     */
    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<RuleViolation> violations = new ArrayList<>(checkDependencyGraph(scope.targetModules()));
        for (Map.Entry<String, List<String>> sdk : SDK_PACKAGES.entrySet()) {
            String groupId = sdk.getKey();
            violations.addAll(ArchRuleEvaluation.violations(id(),
                noClasses().that(scope.ownedBy(scope.serviceName() + " 中未登记 " + groupId + " 例外的模块的类",
                        identity -> scope.serviceName().equals(identity.service())
                            && !SDK_ALLOWLIST.getOrDefault(identity.coordinate(), Set.of()).contains(groupId)))
                    .should().dependOnClassesThat().resideInAnyPackage(sdk.getValue().stream()
                        .map(prefix -> prefix + "..").toArray(String[]::new))
                    .because(description()),
                scope.classes()));
        }
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedBy(scope.serviceName() + " 中出站包以外的类",
                    identity -> scope.serviceName().equals(identity.service())).and(outsideOutboundPackage(scope,
                    outboundPackage)))
                .should().callMethodWhere(jdkHttpClientSend())
                .because(description()),
            scope.classes()));
        return violations;
    }

    /** channel-connector-protocol 出站包以外的类（包内类也必须归属 protocol 模块）。 */
    private static DescribedPredicate<JavaClass> outsideOutboundPackage(ServiceArchitectureScope scope,
        String outboundPackage) {
        return DescribedPredicate.describe("不在 " + outboundPackage + " 包",
            javaClass -> !(javaClass.getPackageName().equals(outboundPackage) && scope.ownership().ownerOf(javaClass)
                .map(ModuleIdentity::coordinate).filter(OUTBOUND_ARTIFACT::equals).isPresent()));
    }

    /** 目标为 java.net.http.HttpClient 的 send / sendAsync。 */
    private static DescribedPredicate<JavaMethodCall> jdkHttpClientSend() {
        return DescribedPredicate.describe("java.net.http.HttpClient#send/sendAsync",
            call -> SEND_METHODS.contains(call.getTarget().getName())
                && call.getTarget().getOwner().isAssignableTo(JDK_HTTP_CLIENT));
    }
}
