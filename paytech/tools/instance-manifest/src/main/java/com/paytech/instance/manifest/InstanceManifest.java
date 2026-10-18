package com.paytech.instance.manifest;

import java.util.List;
import java.util.Map;

/**
 * 校验通过的实例清单中生成部署描述与执行迁移所需的部分。
 *
 * @param instanceId 实例 ID
 * @param platformVersion 平台版本
 * @param services 按清单顺序排列的服务运行参数
 * @param schemaVersions {@code versions.schema}：服务名到目标 Schema 版本，按清单顺序排列
 * @author Claude
 */
public record InstanceManifest(String instanceId, String platformVersion, List<ServiceRuntime> services,
    Map<String, Integer> schemaVersions) {

    /**
     * 按服务名查找运行参数。
     *
     * @param name 服务名
     * @return 运行参数；清单未部署该服务时为 null
     */
    public ServiceRuntime findService(String name) {
        return services.stream().filter(service -> service.name().equals(name)).findFirst().orElse(null);
    }
}
