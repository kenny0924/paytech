# 任务卡 B：Operations / Client BFF 转发与 Platform 注册块 135

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n`。**只改** `paytech/operations-console-bff/**`、`paytech/client-console-bff/**`，以及需求目录下的 `db/` 文件（主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/db/`）。卡 A 正在改 `paytech/platform-support/**`（含 api 模块）；卡 C 改 `operations-console-web/**`。不要动它们。
- 授权：普通文件编辑、编译、运行两个 BFF 模块的测试。不提交、不推送、不执行任何 SQL、不动容器。
- 权威：需求与 `technique.md`（v3）第 3.7—3.9 节，同目录 `tasks/A-platform.md`。Platform 合同以 worktree 中 `paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/localization/` 为准（卡 A 仍可能微调；以你开工时的版本为准，报告列出你依赖的类）。

## 开工前必读

`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（BFF 聚合层一节、第 4 节错误、第 10 节前端文案）、`agent-docs/APPLICATION_ARCHITECTURE.md` 第 3、4 节、`agent-docs/decisions/2026-10-22-file-upload-address-principles.md`、`agent-docs/UNIT_TEST_GUIDE.md`。逐行参照最近同类实现：data-query 的 BFF 转发（`/data-query-admin/v1`）与块 131 注册 SQL（`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-数据查询服务/` 下 `db/`、`release/`），工作流附件上传的 BFF 直调文件服务做法（块 127 / 130）。

## 范围

1. **operations-console-bff**：
   - 管理接口前缀 `/localization-admin/v1`，逐一转发 Platform 23 个管理接口（路径为 Platform 路径去掉 `/internal/localization/v1`），按现有 BFF 转发风格（PLT-I02 权限校验、操作人头、错误原样透传）。权限：读 `I18N_VIEW`，写 `I18N_EDIT`，发布与切换当前发布 `I18N_PUBLISH`。
   - 导入文件上传：按文件上传原则由 BFF 直调 Platform 内部文件接口，提供开始上传 / 完成上传两个端点，原因码 `I18N_KEY_IMPORT`、`I18N_TRANSLATION_IMPORT`（参照工作流附件的现成做法），返回 `fileId` 供导入预览使用。
   - 匿名 manifest：`GET /i18n/v1/manifest?locale=&releaseNo=`，应用编码固定 `operations-console`，忽略前端传入的应用编码；`Cache-Control: no-cache`，透传 / 生成 `ETag` 并支持 304。
2. **client-console-bff**：只加匿名 manifest，同上，应用编码固定 `client-console`。
3. **注册块 135**（`db/dml.sql`，目标库 `mor_platform`，只写不执行）：ID 规则与段划分照抄块 131 的格式（`9000000001351xx` 权限、`1352xx` 角色权限、`1353xx` 控制台资源、`1354xx` 路由、`1355xx` 请求头、`1356xx` 查询参数、`1357xx` Guard，若实际表结构不同以现有块为准）：
   - 权限 3 项（`I18N_VIEW`、`I18N_EDIT`、`I18N_PUBLISH`，风险等级选最贴切者并说明），授 Platform 管理员角色；
   - Console Resource：一级菜单"多语言"，子菜单"翻译"`/i18n/translations`、"发布记录"`/i18n/releases`、"语言与应用"`/i18n/settings`（组件键自定，告知卡 C），必要的按钮资源（导入 key、发布、切回、新建应用、新建命名空间）；
   - Gateway 路由：Operations 管理 23 条 + 上传 2 条（`HUMAN_PERMISSION`，写路由放行 `Idempotency-Key`）+ manifest 1 条（`PUBLIC`）；Client manifest 1 条（`PUBLIC`）；查询参数白名单、请求头、Guard 照同类路由；
   - 文件原因码 / 上传路由：两个导入原因码走现有私有桶写入规则（按现有表结构登记）；语言包公开桶原因码 `I18N_RELEASE_BUNDLE` **不在本卡**（等文件公开桶合并）。
   - 初始数据：Locale `en`、`zh-CN`；应用 4 个（technique 3.9 末段）。这些属于 Platform 业务初始数据，放 `db/dml.sql` 第二部分，并同步到 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`？——**不要改该文件**（卡 A 范围），在报告里列出需要追加的行由主会话合并。
   - 同时写 `db/dml-rollback.sql`（删除本块全部行）。
4. **测试**：两个 BFF 的转发测试按现有风格（外部 HTTP 依赖用测试端点返回配置 DTO，不用 Mockito 桩），覆盖权限、错误透传、manifest 固定应用编码与 304、上传原因码。

## 验证

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd /Users/kenny/Desktop/Workspace/Code-MoR-i18n/paytech
./mvnw -q -pl platform-support/paytech-platform-support-api -am install -DskipTests
./mvnw -pl operations-console-bff/paytech-operations-console-bff-test -am test -Dtest='com/paytech/operations/console/bff/**/*Locali*' -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl client-console-bff/paytech-client-console-bff-test -am test -Dtest='**/*Manifest*' -Dsurefire.failIfNoSpecifiedTests=false
```

（测试类命名与模块名以实际为准；只跑本卡新增与直接相关的测试，最后再跑两个 BFF 模块全量一次。）

## 交付报告（写到 `.../tasks/B-report.md`）

BFF 端点表（方法、外部路径、下游 Platform 接口、权限）；块 135 行数与 ID 段；给卡 C 的组件键与路由；需主会话合并到 initial-data 的行；测试实际输出；待裁决问题。**完成后直接结束。**
