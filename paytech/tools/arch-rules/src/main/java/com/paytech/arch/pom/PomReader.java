package com.paytech.arch.pom;

import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * 以纯 Java DOM 读取 {@code pom.xml}，不调用 Maven、不解析远程父 POM。
 *
 * <p>
 * 只读取规则需要的事实：坐标、打包类型、项目级 {@code <dependencies>}、{@code <modules>}。父 POM 若能经
 * {@code relativePath}（缺省 {@code ../pom.xml}）在本地找到且坐标一致，则继承其 groupId 与 {@code <dependencies>}；
 * {@code dependencyManagement}、Profile 中的依赖不计入。
 *
 * @author Claude
 */
public final class PomReader {

    private static final String DEFAULT_PARENT_PATH = "../pom.xml";

    private final Map<Path, MavenModule> cache = new HashMap<>();

    /**
     * 读取模块根目录下的 pom.xml。
     *
     * @param moduleRoot 模块根目录
     * @return 模块模型
     */
    public MavenModule readModule(Path moduleRoot) {
        Path pomFile = moduleRoot.toAbsolutePath().normalize().resolve("pom.xml");
        MavenModule cached = cache.get(pomFile);
        if (cached != null) {
            return cached;
        }
        MavenModule module = parse(pomFile);
        cache.put(pomFile, module);
        return module;
    }

    /**
     * 自聚合 POM 起递归读取 {@code <modules>}，返回包括聚合 POM 自身在内的全部模块。
     *
     * @param aggregatorRoot 聚合 POM 所在目录
     * @return 按 {@code <modules>} 深度优先顺序排列的模块
     */
    public List<MavenModule> readReactor(Path aggregatorRoot) {
        Map<Path, MavenModule> modules = new LinkedHashMap<>();
        collect(aggregatorRoot.toAbsolutePath().normalize(), modules);
        return List.copyOf(modules.values());
    }

    private void collect(Path moduleRoot, Map<Path, MavenModule> modules) {
        if (modules.containsKey(moduleRoot)) {
            return;
        }
        MavenModule module = readModule(moduleRoot);
        modules.put(moduleRoot, module);
        for (String child : module.moduleNames()) {
            collect(moduleRoot.resolve(child).normalize(), modules);
        }
    }

    private MavenModule parse(Path pomFile) {
        if (!Files.isRegularFile(pomFile)) {
            throw new IllegalArgumentException("pom.xml 不存在: " + pomFile);
        }
        Element project = parseDocument(pomFile).getDocumentElement();
        Element parent = child(project, "parent");
        MavenModule parentModule = resolveLocalParent(pomFile, parent);
        String groupId = text(project, "groupId");
        if (groupId == null && parent != null) {
            groupId = text(parent, "groupId");
        }
        String artifactId = text(project, "artifactId");
        if (groupId == null || artifactId == null) {
            throw new IllegalArgumentException("pom.xml 缺少 groupId 或 artifactId: " + pomFile);
        }
        String packaging = text(project, "packaging");
        List<MavenDependency> dependencies = new ArrayList<>();
        if (parentModule != null) {
            dependencies.addAll(parentModule.dependencies());
        }
        dependencies.addAll(dependencies(project));
        return new MavenModule(pomFile.getParent(), groupId, artifactId, packaging == null ? "jar" : packaging,
            List.copyOf(dependencies), modules(project));
    }

    private MavenModule resolveLocalParent(Path pomFile, Element parent) {
        if (parent == null) {
            return null;
        }
        Element relativePath = child(parent, "relativePath");
        String path = relativePath == null ? DEFAULT_PARENT_PATH : relativePath.getTextContent().trim();
        if (path.isEmpty()) {
            return null;
        }
        Path parentPom = pomFile.getParent().resolve(path).normalize();
        if (Files.isDirectory(parentPom)) {
            parentPom = parentPom.resolve("pom.xml");
        }
        if (!Files.isRegularFile(parentPom)) {
            return null;
        }
        MavenModule candidate = readModule(parentPom.getParent());
        boolean sameCoordinate = candidate.artifactId().equals(text(parent, "artifactId"))
            && candidate.groupId().equals(text(parent, "groupId"));
        return sameCoordinate ? candidate : null;
    }

    private static List<MavenDependency> dependencies(Element project) {
        Element dependencies = child(project, "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        List<MavenDependency> result = new ArrayList<>();
        for (Element dependency : children(dependencies, "dependency")) {
            String scope = text(dependency, "scope");
            result.add(new MavenDependency(text(dependency, "groupId"), text(dependency, "artifactId"),
                scope == null ? "compile" : scope));
        }
        return result;
    }

    private static List<String> modules(Element project) {
        Element modules = child(project, "modules");
        if (modules == null) {
            return List.of();
        }
        return children(modules, "module").stream().map(module -> module.getTextContent().trim()).toList();
    }

    private static Document parseDocument(Path pomFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(pomFile.toFile());
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IllegalArgumentException("pom.xml 无法解析: " + pomFile, exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("pom.xml 读取失败: " + pomFile, exception);
        }
    }

    private static Element child(Element parent, String name) {
        List<Element> matches = children(parent, name);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static String text(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null) {
            return null;
        }
        String value = element.getTextContent().trim();
        return value.isEmpty() ? null : value;
    }
}
