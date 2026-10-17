package com.paytech.arch.test;

import com.paytech.arch.scope.ServiceArchitectureScope;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * 定位 {@code src/test/resources/fixtures} 下的 POM 树与文件 fixture，以及 {@code com.paytech.arch.fixture} 下的类 fixture。
 *
 * @author Claude
 */
public final class Fixtures {

    /**
     * 类 fixture 根包。
     */
    public static final String CLASS_ROOT = "com.paytech.arch.fixture";

    private Fixtures() {
    }

    /**
     * 返回 fixture 目录。
     *
     * @param relative 相对 {@code fixtures/} 的路径
     * @return 绝对路径
     */
    public static Path path(String relative) {
        URL url = Fixtures.class.getResource("/fixtures/" + relative);
        if (url == null) {
            throw new IllegalArgumentException("fixture 不存在: " + relative);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("fixture 路径非法: " + relative, exception);
        }
    }

    /**
     * 共享坐标模块 {@code fixtures/modules/<service>/paytech-<service>-<role>}。
     *
     * @param service 服务
     * @param role 角色后缀
     * @return 模块根目录
     */
    public static Path module(String service, String role) {
        return path("modules/" + service + "/paytech-" + service + "-" + role);
    }

    /**
     * Foundation 坐标模块。
     *
     * @param artifactId artifactId
     * @return 模块根目录
     */
    public static Path foundation(String artifactId) {
        return path("modules/foundation/" + artifactId);
    }

    /**
     * 类 fixture 包名。
     *
     * @param relative 相对 {@link #CLASS_ROOT} 的包
     * @return 包名
     */
    public static String classes(String relative) {
        return CLASS_ROOT + "." + relative;
    }

    /**
     * 以共享坐标模块创建构建器入口。
     *
     * @param service 被检查服务
     * @return 构建器
     */
    public static ServiceArchitectureScope.Builder scope(String service) {
        return ServiceArchitectureScope.builder(service);
    }
}
