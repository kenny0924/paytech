package com.paytech.arch.rule;

import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.MapperXmlScanner;
import com.paytech.arch.support.MapperXmlScanner.TableReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * R4：{@code -core} 中 Mapper XML 只能引用本服务数据库的表；白名单为 {@code table-ownership.yaml}（D16、一库一 Owner）。
 *
 * @author Claude
 */
public final class MapperTableOwnershipRule implements ArchitectureRule {

    @Override
    public String id() {
        return "R4";
    }

    @Override
    public String description() {
        return "-core 的 Mapper XML 只能引用本服务数据库的表";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        Set<String> allowed = scope.tableOwnership().allowedTables(scope.serviceName());
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : scope.targetModules(ModuleRole.CORE)) {
            for (Path mapper : MapperXmlScanner.mapperFiles(module.root(), "src/main/resources")) {
                for (TableReference reference : MapperXmlScanner.tableReferences(mapper)) {
                    if (!allowed.contains(reference.table())) {
                        violations.add(new RuleViolation(id(), mapper + ":" + reference.line(),
                            "引用了不属于 " + scope.serviceName() + " 数据库的表 " + reference.table()));
                    }
                }
            }
        }
        return violations;
    }
}
