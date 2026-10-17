package com.paytech.arch.scope;

import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.ownership.TableOwnership;
import com.paytech.arch.pom.PomReader;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 一次规则检查的对象：一个逻辑服务及其 Maven 模块。
 *
 * <p>
 * 规则只对属于 {@link #serviceName()} 的目标模块下结论；其他已登记模块只用于判定依赖目标的归属。类默认从各模块
 * {@code target/classes} 导入，也可以按包导入并显式登记归属。
 *
 * @author Claude
 */
public final class ServiceArchitectureScope {

    private final String serviceName;

    private final List<MavenModule> modules;

    private final ClassOwnership ownership;

    private final List<Path> classDirectories;

    private final List<String> classPackages;

    private final TableOwnership tableOwnership;

    private JavaClasses classes;

    private ServiceArchitectureScope(Builder builder) {
        this.serviceName = builder.serviceName;
        this.modules = List.copyOf(builder.modules);
        this.ownership = new ClassOwnership(builder.packageOwners, builder.pomReader);
        this.classDirectories = List.copyOf(builder.classDirectories);
        this.classPackages = List.copyOf(builder.packageOwners.keySet());
        this.tableOwnership = builder.tableOwnership == null ? TableOwnership.loadDefault() : builder.tableOwnership;
    }

    /**
     * 以服务名与模块根目录创建检查对象，类从各模块 {@code target/classes} 导入。
     *
     * @param serviceName 逻辑服务名
     * @param moduleRoots 模块根目录（含 pom.xml）
     * @return 检查对象
     */
    public static ServiceArchitectureScope of(String serviceName, Collection<Path> moduleRoots) {
        Builder builder = builder(serviceName);
        moduleRoots.forEach(builder::module);
        return builder.build();
    }

    /**
     * 创建构建器。
     *
     * @param serviceName 逻辑服务名
     * @return 构建器
     */
    public static Builder builder(String serviceName) {
        return new Builder(serviceName);
    }

    /**
     * 返回被检查的服务名。
     *
     * @return 服务名
     */
    public String serviceName() {
        return serviceName;
    }

    /**
     * 返回登记的全部模块（含非目标模块）。
     *
     * @return 模块
     */
    public List<MavenModule> modules() {
        return modules;
    }

    /**
     * 返回属于被检查服务的模块。
     *
     * @return 目标模块
     */
    public List<MavenModule> targetModules() {
        return modules.stream().filter(module -> serviceName.equals(module.identity().service())).toList();
    }

    /**
     * 返回属于被检查服务且角色匹配的模块。
     *
     * @param roles 角色
     * @return 目标模块
     */
    public List<MavenModule> targetModules(ModuleRole... roles) {
        return targetModules().stream().filter(module -> module.identity().hasRole(roles)).toList();
    }

    /**
     * 服务目录：目标模块根目录的共同父目录（{@code paytech/<service>}）。
     *
     * @return 服务目录
     */
    public Path serviceDirectory() {
        List<MavenModule> targets = targetModules();
        if (targets.isEmpty()) {
            throw new IllegalStateException("检查对象没有属于服务的模块: " + serviceName);
        }
        Path common = targets.getFirst().root().getParent();
        for (MavenModule module : targets) {
            while (!module.root().startsWith(common)) {
                common = common.getParent();
            }
        }
        return common;
    }

    /**
     * 返回表归属白名单。
     *
     * @return 表归属
     */
    public TableOwnership tableOwnership() {
        return tableOwnership;
    }

    /**
     * 返回类归属判定器。
     *
     * @return 归属判定器
     */
    public ClassOwnership ownership() {
        return ownership;
    }

    /**
     * 返回导入的类（首次调用时导入）。
     *
     * @return 类集合
     */
    public synchronized JavaClasses classes() {
        if (classes == null) {
            ClassFileImporter importer = new ClassFileImporter();
            classes = classPackages.isEmpty()
                ? importer.importPaths(classDirectories.stream().filter(Files::isDirectory).toList())
                : importer.importPackages(classPackages);
        }
        return classes;
    }

    /**
     * 归属于满足条件的模块的类。
     *
     * @param description 规则报告中的描述
     * @param condition 模块条件
     * @return 类断言
     */
    public DescribedPredicate<JavaClass> ownedBy(String description, Predicate<ModuleIdentity> condition) {
        return DescribedPredicate.describe(description,
            javaClass -> ownership.ownerOf(javaClass).filter(condition).isPresent());
    }

    /**
     * 归属于被检查服务指定角色模块的类。
     *
     * @param roles 角色
     * @return 类断言
     */
    public DescribedPredicate<JavaClass> ownedByTarget(ModuleRole... roles) {
        return ownedBy(serviceName + " " + List.of(roles) + " 模块中的类",
            identity -> serviceName.equals(identity.service()) && identity.hasRole(roles));
    }

    /**
     * 归属于其他服务指定角色模块的类。
     *
     * @param roles 角色
     * @return 类断言
     */
    public DescribedPredicate<JavaClass> ownedByOtherService(ModuleRole... roles) {
        return ownedBy("其他服务 " + List.of(roles) + " 模块中的类",
            identity -> identity.isOtherServiceRole(serviceName, roles));
    }

    /**
     * 检查对象构建器。
     *
     * @author Claude
     */
    public static final class Builder {

        private final String serviceName;

        private final PomReader pomReader = new PomReader();

        private final List<MavenModule> modules = new ArrayList<>();

        private final List<Path> classDirectories = new ArrayList<>();

        private final Map<String, ModuleIdentity> packageOwners = new LinkedHashMap<>();

        private TableOwnership tableOwnership;

        private Builder(String serviceName) {
            this.serviceName = serviceName;
        }

        /**
         * 登记模块，类从 {@code target/classes} 导入。
         *
         * @param moduleRoot 模块根目录
         * @return 构建器
         */
        public Builder module(Path moduleRoot) {
            MavenModule module = pomReader.readModule(moduleRoot);
            modules.add(module);
            classDirectories.add(module.root().resolve("target").resolve("classes"));
            return this;
        }

        /**
         * 登记模块，类从指定包导入并归属该模块。
         *
         * @param moduleRoot 模块根目录
         * @param classPackage 该模块的类所在包
         * @return 构建器
         */
        public Builder module(Path moduleRoot, String classPackage) {
            MavenModule module = pomReader.readModule(moduleRoot);
            modules.add(module);
            packageOwners.put(classPackage, module.identity());
            return this;
        }

        /**
         * 替换默认表归属白名单。
         *
         * @param ownership 表归属
         * @return 构建器
         */
        public Builder tableOwnership(TableOwnership ownership) {
            this.tableOwnership = ownership;
            return this;
        }

        /**
         * 创建检查对象。
         *
         * @return 检查对象
         */
        public ServiceArchitectureScope build() {
            if (!classDirectories.isEmpty() && !packageOwners.isEmpty()) {
                throw new IllegalStateException("同一检查对象不能同时按目录与按包导入类: " + serviceName);
            }
            return new ServiceArchitectureScope(this);
        }
    }
}
