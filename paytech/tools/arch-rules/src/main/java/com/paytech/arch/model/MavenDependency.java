package com.paytech.arch.model;

/**
 * POM 中直接声明（或从 Reactor 内父 POM 继承）的依赖。
 *
 * @param groupId Maven groupId
 * @param artifactId Maven artifactId
 * @param scope 依赖范围；未声明时为 compile
 * @author Claude
 */
public record MavenDependency(String groupId, String artifactId, String scope) {

    /**
     * 是否 test 范围。依赖图规则只检查进入制品的依赖，test 范围不计。
     *
     * @return test 范围返回 true
     */
    public boolean isTestScope() {
        return "test".equals(scope);
    }

    /**
     * 返回依赖方的模块归属。
     *
     * @return 模块归属
     */
    public ModuleIdentity identity() {
        return ModuleIdentity.classify(groupId, artifactId);
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
