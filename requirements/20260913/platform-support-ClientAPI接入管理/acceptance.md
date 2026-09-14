# 阶段 5 综合验证与本地验收

本阶段完成真实调用链及页面验收，修复凭证查询输入框被挤没、Scope 中文名称搜索报错两处问题。未新增接口或数据库变更。当前跨仓库状态及下一阶段以 [台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913) 为准。

## 1. 证据与边界

- [完整验证记录](./assets/stage5/verification.json)：运行命令、通过数量、源码/构建与日志 SHA-256。
- [代表性入口检查](./assets/stage5/integration.json) 28 项 + [Scope 路由绑定](./assets/stage5/route-integration.json) 8 项；全部经过真实 Gateway/BFF/Platform。
- 修复后 [搜索复验](./assets/stage5/search-recheck.json) 11 项，以及 [路由绑定复跑](./assets/stage5/route-integration-rerun.json) 8 项。含中文名称、中文无结果、ASCII 编码与大小写边界。
- [浏览器记录](./assets/stage5/browser.md)：实际查看运行截图，检查新增、保存、只读、关联、权限、冲突、回退、撤销及响应式布局。
- [撤销认证证据](./assets/stage5/browser-support.json)、[日志检查](./assets/stage5/log-secrecy.json)和[清理核对](./assets/stage5/cleanup.json)。

环境仅为 `127.0.0.1:13306/mor_platform_config_validation`，复用已有 local-environment MySQL/Redis。入口代理承担本机同源和可信头覆盖，业务处理使用当前真实服务；服务发现为本机 SimpleDiscovery，签名及 Email Vault 为已有开发适配器。没有开通真实 Client API 业务请求或连接 Risk Owner，不把本次验收表述为共享 test 已发布。可复现启动器在 [harness](./assets/stage5/harness/README.md)。

## 2. 验收映射

| 验收 | 本阶段事实 |
|---|---|
| AC-01 / UI-01 | 中文 Client 查询、详情、前缀/三种状态正确；筛选/重置及长名称省略通过 |
| AC-02 / AC-03 / UI-02 | 查询响应与运行日志无完整合成凭证/PHC；详情只读，无客户日常创建/轮换/授权编辑入口 |
| AC-04 / UI-03 | Scope 新增/编辑/清空说明成功，编码不可改；Route 动态目录搜索选择并保存成功 |
| AC-05 / UI-04 | Scope/Route/凭证引用、停用/已撤销状态、分页及详情跳转通过；不将引用标记为可调用 |
| AC-06 / AC-07 | 只读用户无操作按钮且 API 返回 403；取消撤销不变更，确认后新 PLT-I03 认证由 200 变 401。其他凭证仍可认证等边界复用本期真实 HTTP 回归 |
| AC-08 / UI-05 | 同键重放、旧版本 409；真实浏览器草稿保留、禁止覆盖、离开保护；查询分页 replace、详情跳转/返回正确 |
| AC-09 | Scope 对象审计可看到本轮实际变更和原因；独立授权规则不变 |
| AC-10 | 真实旧登录/菜单/Route 维护链通过，Gateway 加载 63 条实际 Operations 路由；风控及其他既有入口兼容复用阶段 4 BFF/Web 证据，本轮没有真实 Risk 链验收 |
| UI-06 | 已确认 Pencil 方案作为对照，运行页面在 1440/1024/390 宽度下实际核对；没有用设计截图替代浏览器证据 |

## 3. 修复与回归

**凭证查询布局：** 浏览器发现两个输入宽度为 0。修正模式下拉与输入的 Flex 尺寸，并为查询输入增加可访问名称。重新构建后中文查询成功，窄屏单列和表格局部横向滚动正常。

**Scope 中文搜索：** 真实入口报 503，Platform 的 SQL 实际报 MySQL 1270：ASCII 编码列与中文搜索关键词字符集不兼容。现有真实 HTTP 用例增加中文文字/通配符输入，修复前 2 项中 1 项失败（期望 200，实际 500）；修正 LIKE 编码表达式为兼容的 utf8mb4 二进制比较后，本期全部 34 项 HTTP 回归通过。保留编码大小写规则、查询转义及 Schema。完整链和浏览器的中文名称查询、选择保存复验通过。

最终 Platform 聚焦 `verify` 返回 0，原 90% 指令/分支门禁通过（使用仓库累计 JaCoCo 数据，非一次全新 clean 全套运行）。Web 923/923 检查和生产构建通过，需求站 136/136 及构建通过，本需求 139 个本地文档链接有效。BFF 生产代码本阶段未再改动，沿用阶段 4 的 1,138 项开发回归；其真实转发/聚合已由本阶段完整链验证。

初次批量创建分页夹具触发真实 Gateway 429；验收脚本按 Retry-After 放慢并复用原请求，没有放宽或关闭限流。该准备过程不计为产品缺陷。

## 4. 收尾与下一阶段

自有浏览器、Web、Gateway、BFF、Platform 进程均已退出。数据库恢复 25 表 / 1,268 行基线，临时 Scope、凭证、账号、会话、命令、审计及签名密钥均为 0；临时启用的 27 条 Route 和 8 个资源恢复 DISABLED。本期 127 条未启用注册仍保留，数据库结构沿用阶段 4 已批准结果。

本阶段无未关闭缺陷。下一步仅准备阶段 6 的 test 部署清单、SQL 执行/启用步骤、制品和恢复方案；实际部署需用户确认阶段 6 的具体内容。未发布远程制品、修改 test 或 Git 提交/推送。
