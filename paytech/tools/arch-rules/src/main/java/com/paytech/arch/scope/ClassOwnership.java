package com.paytech.arch.scope;

import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.pom.PomReader;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.Source;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 判定一个类属于哪个 Maven 模块：以模块而非包名划定边界（design/01 §8）。
 *
 * <p>
 * 先按显式登记的包前缀匹配（类来自指定包导入的场景）；否则按类文件位置判定：Reactor 内
 * {@code <module>/target/classes} 读取该模块 pom.xml，Maven 仓库中的 {@code com/paytech/<artifactId>/<version>/*.jar}
 * 取 artifactId。无法判定的类（JDK、第三方）返回空，不参与归属规则。
 *
 * @author Claude
 */
public final class ClassOwnership {

    private static final String PAYTECH_GROUP = "com.paytech";

    private final Map<String, ModuleIdentity> packageOwners;

    private final PomReader pomReader;

    private final Map<String, Optional<ModuleIdentity>> locationCache = new HashMap<>();

    ClassOwnership(Map<String, ModuleIdentity> packageOwners, PomReader pomReader) {
        Map<String, ModuleIdentity> sorted = new LinkedHashMap<>();
        packageOwners.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, ModuleIdentity> entry) -> entry.getKey().length())
                .reversed())
            .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        this.packageOwners = sorted;
        this.pomReader = pomReader;
    }

    /**
     * 返回类所属模块。
     *
     * @param javaClass 类
     * @return 所属模块；无法判定时为空
     */
    public Optional<ModuleIdentity> ownerOf(JavaClass javaClass) {
        JavaClass base = javaClass.getBaseComponentType();
        String className = base.getName();
        for (Map.Entry<String, ModuleIdentity> entry : packageOwners.entrySet()) {
            if (className.startsWith(entry.getKey() + ".")) {
                return Optional.of(entry.getValue());
            }
        }
        return base.getSource().map(Source::getUri).flatMap(this::ownerOfLocation);
    }

    private Optional<ModuleIdentity> ownerOfLocation(URI uri) {
        String location = uri.toString();
        String container = location.startsWith("jar:") ? location.substring(4, location.indexOf("!/"))
            : location.substring(0, Math.max(location.indexOf("/target/"), 0));
        return locationCache.computeIfAbsent(container, key -> resolve(location));
    }

    private Optional<ModuleIdentity> resolve(String location) {
        if (location.startsWith("jar:")) {
            Path jar = Path.of(URI.create(location.substring(4, location.indexOf("!/"))));
            return ownerOfJar(jar);
        }
        int targetIndex = location.indexOf("/target/");
        if (!location.startsWith("file:") || targetIndex < 0) {
            return Optional.empty();
        }
        return ownerOfModuleRoot(Path.of(URI.create(location.substring(0, targetIndex))));
    }

    private Optional<ModuleIdentity> ownerOfJar(Path jar) {
        Path parent = jar.getParent();
        if (parent != null && "target".equals(String.valueOf(parent.getFileName()))) {
            return ownerOfModuleRoot(parent.getParent());
        }
        Path artifactDirectory = parent == null ? null : parent.getParent();
        if (artifactDirectory == null || !jar.toString().contains("/com/paytech/")) {
            return Optional.empty();
        }
        return Optional.of(ModuleIdentity.classify(PAYTECH_GROUP, artifactDirectory.getFileName().toString()));
    }

    private Optional<ModuleIdentity> ownerOfModuleRoot(Path moduleRoot) {
        if (moduleRoot == null || !Files.isRegularFile(moduleRoot.resolve("pom.xml"))) {
            return Optional.empty();
        }
        return Optional.of(pomReader.readModule(moduleRoot).identity());
    }
}
