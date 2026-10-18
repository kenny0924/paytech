package com.paytech.arch.model;

import java.nio.file.Path;
import java.util.List;

/**
 * 从 {@code pom.xml} 读出的模块模型，不调用 Maven。
 *
 * @param root 模块根目录（含 pom.xml）
 * @param groupId Maven groupId（未声明时继承父 POM）
 * @param artifactId Maven artifactId
 * @param packaging 打包类型，默认 jar
 * @param dependencies 直接依赖与 Reactor 内父 POM 继承的依赖
 * @param moduleNames {@code <modules>} 中列出的子模块相对路径
 * @author Claude
 */
public record MavenModule(Path root, String groupId, String artifactId, String packaging,
    List<MavenDependency> dependencies, List<String> moduleNames) {

    /**
     * 返回模块归属。
     *
     * @return 模块归属
     */
    public ModuleIdentity identity() {
        return ModuleIdentity.classify(groupId, artifactId);
    }

    /**
     * 返回进入制品的依赖（排除 test 范围）。
     *
     * @return 非 test 依赖
     */
    public List<MavenDependency> productionDependencies() {
        return dependencies.stream().filter(dependency -> !dependency.isTestScope()).toList();
    }

    /**
     * 返回 {@code groupId:artifactId}。
     *
     * @return Maven 坐标
     */
    public String coordinate() {
        return groupId + ":" + artifactId;
    }
}
