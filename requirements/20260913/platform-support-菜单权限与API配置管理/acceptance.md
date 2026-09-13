# 阶段 5 验收证据

本文件保存本阶段执行证据；当前实现、环境状态与下一确认点统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-config-management-20260913)。需求与逐项标准见 [requirement.md](./requirement.md)，阶段授权见 [delivery.md](./delivery.md)。

## 执行范围与边界

执行日期：2026-09-13。本期交付量保持 18 项新增能力（Platform/BFF 各 18 个端点）、0 张新表 / 0 个新字段 / 0 个新索引、227 行禁用注册数据；包含用户批准的 Foundation 404 修复。

本机链路为实际 Web 生产构建 → 临时入口代理 → 实际 Gateway → 实际 BFF → 实际 Platform → `local-environment` MySQL/Redis。仅使用已批准的独立 `127.0.0.1:13306/mor_platform_config_validation`。三个合成账号分别覆盖管理、只读和无新增权限场景。临时启用本期 18 Route / 4 Resource，由额外测试角色显式授权；原管理员 4 条 REVOKED 授权未改变。

入口代理设置可信 Channel / Client IP，Request ID 由实际 Gateway 生成。BFF/Gateway 使用仅本机的 HTTP test Profile 与 SimpleDiscovery；Platform 的签名及 Email Vault 使用已批准 auto 适配器。新的动态 Target 指向合成 HTTP 回显端点，只证明数据库快照路由转发。没有访问真实 Nacos、其他 Channel 的真实业务认证、共享 test 或生产环境，也没有发布制品或提交代码。

## 验证结果

| 证据 | 实际结果 |
|---|---|
| Platform 最终 `clean verify` | 433 项，失败 / 错误 / 跳过均为 0；指令覆盖率 97.597%，分支覆盖率 90.086%，原 90% 门槛不变 |
| BFF 最终 `verify` | 1105 项，失败 / 错误 / 跳过均为 0 |
| Web 有效开发证据复用 | 全量 920 项、后续配置聚焦 5 项通过；TypeScript 与 Vite 生产构建通过。本阶段未修改 Web 源码 |
| Foundation / Gateway 有效证据复用 | Foundation 108 项；Gateway 动态转发及快照 13 项通过；对应边界见当前台账 |
| 本机真实公开 HTTP | 累计 50 项检查通过，逐项覆盖 A01–A18；包括 403、404、409、幂等重放、首次授权、停用恢复及数据库动态转发 |
| 浏览器 | 真实账号登录、资源保存回读、只读详情、草稿回退、权限筛选与引用、六 Channel 选项、必填错误、权限组合与响应式布局通过；未捕获 error / warn 控制台消息 |
| 清理 | 验证库恢复 24 表 / 1141 行；账号、会话、命令、审计、临时签名 Key 等归零。18 Route / 4 Resource 恢复 DISABLED，4 条管理员授权仍 REVOKED；本任务启动的进程已退出 |

机器证据：[验证汇总、报告及制品 SHA-256](./assets/stage5/verification.json)、[实际 HTTP 检查记录](./assets/stage5/integration.json)、[浏览器观察记录](./assets/stage5/browser.md)。不归档密码、Token、Cookie 或私有运行文件。

## 本阶段发现并修复

1. 旧目录数量、精确路由及日志路径断言未同步已批准功能，按实际固定合同更新，没有放宽生产权限。
2. 完整覆盖率门禁揭示配置异常、历史关联与保护边界用例不足；补充真实 HTTP / MySQL / Redis 和聚焦规则用例，保留原阈值。
3. 实际整链路发现 A18 被 PLT-I02 的普通会话允许清单拒绝。先增加 HTTP 用例，得到 `allowed=false` 的失败证据，再补齐 `operations-configuration-permission-options` 接入。业务服务继续按 usage 校验真实 READ 权限，强制改密及 Bootstrap 会话的原限制不变。修复后的最终完整验证为上表 433 项。

手工启动器曾出现本机 Profile / 入口 Header 配置不匹配及 Spring 退出顺序竞态，均在验收工具中修正，没有削弱生产边界。首次退出遗留已按记录中的合成 ID 精确清理；最后一次正常退出完成自有数据清理及初始状态核对。

## 功能验收映射

| 标准 | 证据与覆盖范围 |
|---|---|
| AC-01 | 三类账号真实 HTTP 与浏览器；只读没有新增 / 编辑 / 保存，直接写 API 返回 403；无新权限账号访问详情显示无权限 |
| AC-02 | 配置 HTTP 覆盖树生命周期、父子状态、互挂竞争、排序和非法输入；浏览器真实改名保存回读 |
| AC-03 | 11 个部署组件能力与实际 handler 固定合同；不支持的组件 / 按钮及保护资源拒绝写入 |
| AC-04 | 新 Permission 创建不会自动授权；真实角色创建、人员角色分配及新会话导航证明显式首次授权 |
| AC-05 | 真实 Resource / Role 引用与数量、浏览器三类关系页签；Route 引用及分页边界由配置 HTTP 用例覆盖 |
| AC-06 | 真实停用后导航移除、恢复后沿原授权重新可见；停用权限禁止新增绑定，历史引用保持的 HTTP 用例通过 |
| AC-07 | 六 Channel 创建 / 编辑及编译真实 HTTP 用例；实际 Platform 快照让新 Target / Route 自动转发，停用后返回 404，无重启 |
| AC-08 | 实际 Target Discovery 编辑后被新快照消费；单请求快照固定及更换发现目标复用 Gateway 回归证据 |
| AC-09 | 真实 Route 根 / Header / Query / Guard 保存、回读；子项插入失败整体回滚的 MySQL 用例通过 |
| AC-10 | 路径竞争、身份 / 权限 / Profile / Header / Guard 约束及无效快照保留旧配置用例通过 |
| AC-11 | 真实重放与 409；并发 CAS、权限绑定竞争及角色授权重查通过；浏览器取消离开保留草稿 |
| AC-12 | 新权限 → 新资源 / 新角色 → 人员显式分配 → 新会话导航可见；结合真实新 Target / Route 转发，写入进入实际审计 |
| AC-13 | 最终 Platform / BFF 全量验证及 Web 有效证据覆盖旧合同；沿用 v1。六 Channel 仅配置能力，不扩大业务认证上线范围 |
| AC-14 | 浏览器保存提示 Platform 已保存；实际快照另外验证。没有新发布步骤、启动 DDL 或业务播种 |

## 页面验收映射

| 标准 | 证据与覆盖范围 |
|---|---|
| UI-01 | 实际平台配置下的 API 路由管理、菜单与按钮、权限目录入口；菜单树缩进、状态与操作可辨认 |
| UI-02 | 资源树名称与停用标识；资源 / 权限 / Route 详情复用表单并禁用输入，无保存入口 |
| UI-03 | Route 分组、完整子项、六 Channel 下拉和逐字段必填错误；模式组合逻辑复用现行 Web / API 用例 |
| UI-04 | 资源详情 / 编辑 URL，取消回退保留草稿，确认回退返回原只读详情；权限列表筛选随回退保留 |
| UI-05 | 三类权限页面、字段错误；服务错误、冲突草稿保持复用已通过 Web 开发用例，HTTP 真实验证对应错误合同 |
| UI-06 | 真实权限引用数量及角色关系、启停恢复语义；状态弹层与影响文案复用既有 Web 用例；1280 / 1024 / 768 宽度观察，1024 / 768 无页面横向溢出 |

## 复现与日志

从根目录使用 Maven Wrapper / JDK 25；先载入本机私有环境并将 Platform 测试 DB 明确指向上述独立库，不输出凭据。

```sh
./mvnw -f platform-support-service/pom.xml clean verify
./mvnw -f operations-console-bff/pom.xml verify
```

前端有效证据来自 `operations-console-web` 下的 `npm test` 和 `npm run build`。真实链路启动、身份/数据归属和退出清理见[手工验收启动器](./assets/stage5/harness/README.md)。完整日志路径及 SHA-256 保存在验证汇总中；日志仍位于本机私有临时目录，不能当作共享 test 执行记录。

下一确认范围是阶段 6：准备 test 的具体制品、SQL、环境差异与部署清单，完成后再请求部署授权。
