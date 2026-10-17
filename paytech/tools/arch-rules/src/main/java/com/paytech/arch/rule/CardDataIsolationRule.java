package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;
import com.paytech.arch.support.MapperXmlScanner;
import com.paytech.arch.support.MapperXmlScanner.TableReference;
import com.paytech.arch.support.ModuleFiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R11：卡域隔离。除 {@code paytech-card-core} 与 {@code paytech-card-app} 外，任何模块的 Mapper XML 不得引用卡域表，任何模块不得
 * import {@code com.paytech.card.core.crypto}（D10、PCI）。
 *
 * <p>
 * 卡域表为 {@code table-ownership.yaml} 中 card 服务拥有的业务表，另以 {@code t_payment_card*}、{@code t_payment_stored_card}
 * 兜底匹配，避免白名单遗漏新表。第 12 步前卡域仍在 payment 进程内，{@code transitionalPlacements} 所列宿主内卡域模块
 * （{@code paytech-payment-card}）与 card-core 同等豁免；宿主的其他模块照常检查。
 *
 * @author Claude
 */
public final class CardDataIsolationRule implements ArchitectureRule {

    private static final Set<String> EXEMPT_ARTIFACTS = Set.of("paytech-card-core", "paytech-card-app");

    /**
     * 卡域加密包。
     */
    public static final String DEFAULT_CRYPTO_PACKAGE = "com.paytech.card.core.crypto";

    private static final Pattern CARD_TABLE_PATTERN = Pattern.compile("t_payment_card\\w*|t_payment_stored_card");

    private final String cryptoPackage;

    private final Pattern cryptoImport;

    /**
     * 保护 {@value #DEFAULT_CRYPTO_PACKAGE}。
     */
    public CardDataIsolationRule() {
        this(DEFAULT_CRYPTO_PACKAGE);
    }

    /**
     * 保护指定的加密包。
     *
     * @param cryptoPackage 加密包名
     */
    public CardDataIsolationRule(String cryptoPackage) {
        this.cryptoPackage = cryptoPackage;
        this.cryptoImport = Pattern.compile(
            "^[ \\t]*import\\s+(?:static\\s+)?" + Pattern.quote(cryptoPackage) + "[.;]", Pattern.MULTILINE);
    }

    @Override
    public String id() {
        return "R11";
    }

    @Override
    public String description() {
        return "除 card-core / card-app 外不得引用卡域表或 import 卡域加密包";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        Set<String> cardTables = Set.copyOf(scope.tableOwnership().cardTables());
        Set<String> exempt = exemptArtifacts(scope);
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : scope.targetModules()) {
            if (exempt.contains(module.artifactId())) {
                continue;
            }
            violations.addAll(checkMappers(module, cardTables));
            violations.addAll(checkImports(module));
        }
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedBy(scope.serviceName() + " 中卡域以外模块的类",
                    identity -> isRestricted(scope.serviceName(), identity, exempt)))
                .should().dependOnClassesThat().resideInAPackage(cryptoPackage + "..")
                .because(description()),
            scope.classes()));
        return violations;
    }

    private static boolean isRestricted(String service, ModuleIdentity identity, Set<String> exempt) {
        return service.equals(identity.service()) && !exempt.contains(identity.artifactId());
    }

    private static Set<String> exemptArtifacts(ServiceArchitectureScope scope) {
        Set<String> exempt = new java.util.TreeSet<>(EXEMPT_ARTIFACTS);
        exempt.addAll(scope.tableOwnership().transitionalCardModules());
        return Set.copyOf(exempt);
    }

    private List<RuleViolation> checkMappers(MavenModule module, Set<String> cardTables) {
        List<RuleViolation> violations = new ArrayList<>();
        for (String directory : List.of("src/main/resources", "src/test/resources")) {
            for (Path mapper : MapperXmlScanner.mapperFiles(module.root(), directory)) {
                for (TableReference reference : MapperXmlScanner.tableReferences(mapper)) {
                    if (cardTables.contains(reference.table())
                        || CARD_TABLE_PATTERN.matcher(reference.table()).matches()) {
                        violations.add(new RuleViolation(id(), mapper + ":" + reference.line(),
                            module.artifactId() + " 引用了卡域表 " + reference.table()));
                    }
                }
            }
        }
        return violations;
    }

    private List<RuleViolation> checkImports(MavenModule module) {
        List<RuleViolation> violations = new ArrayList<>();
        for (String directory : List.of("src/main/java", "src/test/java")) {
            for (Path source : ModuleFiles.list(module.root(), directory, ".java")) {
                String text = ModuleFiles.read(source);
                Matcher matcher = cryptoImport.matcher(text);
                while (matcher.find()) {
                    violations.add(new RuleViolation(id(), source + ":" + ModuleFiles.lineOf(text, matcher.start()),
                        module.artifactId() + " import 了卡域加密包 " + cryptoPackage));
                }
            }
        }
        return violations;
    }
}
