# test 部署检查与用户验收

2026-09-13 已按用户“部署test”完成发布。当前权威状态见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)，发布明细与恢复入口见 [release.md](./release.md)。

## 已完成的部署检查

- [容器记录](./assets/stage7/final-containers.json)：11 个容器健康；仅 Platform/BFF/Web 镜像更新，另外 8 个容器未替换。
- [真实入口检查](./assets/stage7/public-checks.json)：15 项通过；现有管理员登录、新增 4 个菜单/按钮资源可见、凭证查询、Scope 目录和选项、中文查询、Route Scope 筛选、审计对象筛选、3 项合法 404、原规则集与决策流查询。公开入口 44 个静态文件 SHA 与发布构建一致。
- [Gateway Snapshot](./assets/stage7/gateway-snapshot.json)：97 条 Route 已加载，最近刷新成功，连续失败为 0，未使用失败回退 Snapshot。
- [Schema](./assets/stage6/schema-counts.json)、[注册事务](./assets/stage6/registration-application.json)、[启用事务](./assets/stage6/activation-application.json)均核对通过；没有 Client/凭证/Scope 业务种子。
- 首轮健康检查发现打包缓存的探针缺少 BFF 当前端口。重新编译仓库现行探针后更新 Java 镜像，业务 JAR 不变。404 检查以真实 HTTP 状态和 Problem Code 为准，不要求响应重复携带 status 字段。

本次按轻量部署检查交付；阶段 5 的完整浏览器和写入/撤销验收见 [acceptance.md](./acceptance.md)，没有在 test 重新执行，也不等同于用户已验收。浏览器检查仅确认新静态入口可加载登录页，临时页已关闭，用户原标签页未修改。

## 用户验收入口

打开 [Operations test](http://localhost:28080)，刷新页面后进入「平台配置」：

1. 「API Scope」：新建编码、名称和说明，验证编辑、查询及详情。
2. 「API 路由管理」：在适用的 API_SCOPE 路由中使用已登记的 Scope。
3. 「API 凭证」：目前 test 无 Client/凭证，列表为空是预期结果；本期 Operations 不提供创建/轮换客户凭证功能。凭证详情及紧急撤销需有合法凭证数据后再验收，不能用空列表宣称完成。

已有页面可能仍使用旧静态包，需刷新；权限导航如未更新，可退出重新登录。本地代码已按用户后续要求提交，精确记录见 [交付记录](./delivery.md)；尚未推送，业务验收状态见当前台账。
