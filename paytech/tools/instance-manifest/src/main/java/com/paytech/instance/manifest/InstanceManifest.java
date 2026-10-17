package com.paytech.instance.manifest;

import java.util.List;

/**
 * 校验通过的实例清单中生成部署描述所需的部分。
 *
 * @param instanceId 实例 ID
 * @param platformVersion 平台版本
 * @param services 按清单顺序排列的服务运行参数
 * @author Claude
 */
public record InstanceManifest(String instanceId, String platformVersion, List<ServiceRuntime> services) {
}
