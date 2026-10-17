package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.Action;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.Capabilities;

import tools.jackson.databind.ObjectMapper;

/** 仅描述当前部署代码的能力；数据库仍拥有菜单和按钮实例。 */
@Component
public final class ConsoleResourceCapabilities {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_RESOURCES = 2000;
    private final Capabilities capabilities;

    /** 随制品加载一次并对重复身份 fail-fast，不读取 Web 文件系统。 */
    public ConsoleResourceCapabilities(ObjectMapper json) {
        try (var input = new ClassPathResource("operations/console-resource-capabilities.json").getInputStream()) {
            List<ConfigurationCatalog.Component> components = List.copyOf(Arrays.asList(
                json.readValue(input, ConfigurationCatalog.Component[].class)));
            if (components.stream().map(ConfigurationCatalog.Component::componentKey).distinct().count() != components.size()
                || components.stream().flatMap(c -> c.actions().stream()).map(Action::resourceCode).distinct().count()
                    != components.stream().mapToLong(c -> c.actions().size()).sum()) {
                throw new IllegalStateException("页面能力或动作编码重复");
            }
            capabilities = new Capabilities(components, List.of("MENU", "BUTTON"), MAX_DEPTH, MAX_RESOURCES);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("无法加载当前页面能力清单", exception);
        }
    }

    /** 返回只读部署能力，不补入任意数据库组件。 */
    public Capabilities current() { return capabilities; }

    /** 未知组件返回空，调用方区分历史读取与新增绑定。 */
    public ConfigurationCatalog.Component component(String key) {
        return capabilities.components().stream().filter(c -> c.componentKey().equals(key)).findFirst().orElse(null);
    }
}
