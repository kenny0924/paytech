package com.paytech.platform.support.test.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 外部 API 测试数据文件：src/test/resources/externalapi 下的原子 API 定义（definition）、流程（flow）、 公共脚本源码（script）与 Vault 账号配置（vault）。
 *
 * <p>
 * 每次读取都返回新对象，用例在副本上改动只与本场景相关的属性；内容均为合成数据，不代表真实渠道协议。
 */
public final class ExternalApiFixtures {

    private static final String ROOT = "externalapi/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ExternalApiFixtures() {}

    /** 原子 API definition_json，例如 create-order。 */
    public static ObjectNode definition(String name) {
        return json("definition/" + name + ".json");
    }

    /** 流程 flow_definition_json，例如 pay。 */
    public static ObjectNode flow(String name) {
        return json("flow/" + name + ".json");
    }

    /** 公共脚本源码，去掉文件末尾换行，例如 sign。 */
    public static String script(String name) {
        return text("script/" + name + ".av").stripTrailing();
    }

    /** Vault 中 CHANNEL_ACCOUNT_CONFIG_V1 的合成账号配置。 */
    public static ObjectNode channelAccount() {
        return json("vault/channel-account.json");
    }

    /** 读取并解析 JSON 对象。 */
    static ObjectNode json(String path) {
        return (ObjectNode)JSON.readTree(text(path));
    }

    /** 按 UTF-8 读取类路径文件。 */
    static String text(String path) {
        try (InputStream input = ExternalApiFixtures.class.getClassLoader().getResourceAsStream(ROOT + path)) {
            if (input == null) {
                throw new IllegalStateException("测试数据文件不存在：" + ROOT + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
