# 任务卡 S2-D：第二段 mor-test 发布包（阶段 6 准备）

> 用户 2026-10-22 确认第二段阶段 4—5，端到端验证放到 mor-test 验收中做。本卡只做**准备**，与第一段的 [任务卡 C](./C-release.md) 规则相同：
> - 不停 / 起 / 替换 mor-test 任何容器；
> - 不执行写 SQL；
> - 不调用写接口；
> - 不提交，不 install，不改 `:development` tag。
>
> 环境变更由主会话执行（阶段 7 另需用户授权）。

## 1. 源码

- 集成工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-fs2-int`，即 main `1d39cd94` + 第二段，验证结果见 delivery.md。
- main 现在是 `3a4574c7`，比集成基线多一个提交（Cybersource，只涉及 channel-connector 和文档）。请确认它与本次要发的服务无关。
- 主工作区已合入第二段，但还混有他人的未提交改动，**不要从主工作区构建**。

## 2. 服务与基线

| 容器 | 运行镜像 | 已知来源 |
|---|---|---|
| platform-support-service | `b9301714…` | 工作流发布，基线 `1d39cd94`，见 `requirements/20261021/platform-support-工作流/release/` |
| operations-console-bff | `f1fbcea1…` | 同上 |
| operations-console-web | `c7646648…` | 同上。**第二段应该不改 ops-web**，请确认集成目录相对 1d39cd94 没有 ops-web 改动；有的话报告 |
| client-master-service | `6f1dce5e…`（2026-10-18 构建） | 待查 |
| client-console-bff | `51f5263b…`（2026-10-18） | 待查 |
| client-console-web | `b060cd3d…`（2026-10-18） | 待查 |

**基线证明：** 用 1d39cd94 不加第二段补丁构建每个要换的服务，与运行容器逐项比较（方法同第一段 release.md 第 1 节）。

**重点：** CCT、client-console-bff、client-console-web 在 mor-test 上是 10-18 的旧版本。1d39cd94 相对它们很可能还有别人已提交、但未发布的改动。请：
- 列出这些差异分别来自哪些提交 / 需求；
- 判断它们是否需要 DDL、数据或配置，以及在 mor-test 上能否随本次一并上线；
- 只报告，不自行取舍；由主会话和用户决定是一并发布，还是先做别的。

## 3. 兼容性与顺序（写进 release.md）

至少回答：
- **CCT DDL：**
  - 第 1 步（加列、回填、放宽、新唯一键）对**旧 CCT** 是否安全；
  - 第 2 步（删 `file_id`、旧唯一键，`file_address` 改 NOT NULL）必须在新 CCT 稳定之后执行。
- **Platform 删 Owner：** 删列 / 删表的 DDL 对新、旧 Platform 的影响，放在最后执行，执行前先备份。
- **新旧版本混跑：** 发布期间各服务新旧版本混跑时，哪些组合会出问题。例如：
  - 新 Platform 已删 F01—F04，旧 client-console-bff 还在调；
  - 新 client-console-bff 调新内部接口，而 Platform 还没开 DUAL。

  据此给出最短的不可用窗口和替换顺序。用户确认过 Q16"分两次替换 Platform、任一步可回滚"；如果因为旧入口已删，做不到真正的两次替换，就如实写出偏差，并说明在执行删除 DDL 之前，任一步仍可回滚。
- **Platform 环境变量：**
  - 新增 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`、`PAYTECH_INSTANCE_ID`（取值规则查清楚，各实例唯一）；
  - 删掉已被忽略的 `FILES_ONBOARDING_ENABLED`；
  - **必须保留**工作流的 5 个 `PLATFORM_SUPPORT_RABBITMQ_*`，以及数据查询覆盖文件中的 `DATAQUERY_HTTP_CALLERS_0`；
  - 先 `printenv` 读运行容器，再 grep 历次 `requirements/*/release/compose-candidate*.yaml`，逐键带上。
- **其他服务：** client-console-bff、ops-bff、CCT 是否需要新增环境变量。调用方头写在代码里，应该不需要；请确认。
- **DUAL 的影响：** Platform 现有内部入口在 DUAL 下的行为（集成测试已覆盖两个代表），以及 mor-test 上其他服务调 Platform 时是否带了 `X-Caller-Service`、会不会被误判。

## 4. 发布物（写到本需求 `release-s2/`）

照第一段的 `release/` 结构：
- `release.md`；
- `compose-candidate.yaml` / `compose-rollback.yaml`：钉 sha256；
- `sql/00-precheck.sql`：执行一次（只读），结果写入 release.md；
- `sql/01-cct-file-address-step1.sql`、`sql/02-cct-file-address-step2.sql`；
- `sql/03-platform-file-owner-removal.sql`；
- `sql/99-postcheck.sql`；
- `scripts/backup.sh`、`scripts/apply_sql.sh`；
- `acceptance/`。

## 5. 验收脚本（mor-test 端到端，补阶段 5）

- **客户端到端：** 用 mor-test 已有的测试客户账号（查 `test-environment/private/access.md` 与以往入驻验收脚本），不另建真实主体：
  - 浏览器同路径上传：开始 → 直传 → 完成 → 轮询扫描；
  - 关联材料（C12，提交地址），CCT 存 `file_address` 且冻结哈希；
  - 提交申请（C14）。提交会在 compliance 生成审核任务，请评估这对共享环境的影响和清理方式；如果无法清理，**只做到关联为止**，并报告主会话。
- **授权矩阵：**
  - 本人可查、可下载；
  - 他人文件返回 404；
  - 地址格式错误返回 422；
  - 扫描未完成返回 409。
- **运营审核下载：** 用已有审核任务的旧文件（55 条中的一条）下载，校验字节与 SHA-256；同时确认 Platform 写了操作人审计。
- **旧数据：** 申请详情中的旧材料显示地址，且能下载。
- **内部接口：**
  - 无身份返回 401；
  - 带 `X-Caller-Service` 可用；
  - `disposition=INLINE` 对 pdf 返回 INLINE，对其他类型降为 ATTACHMENT；
  - 下载响应带原文件名。
- **清理脚本：** 只删本次运行生成的数据。
- **浏览器冒烟：** 参照第一段的 `stage7_browser.py`：
  - 客户控制台的入驻材料页：上传、预览列表、下载；
  - 运营控制台：审核详情下载、存储管理、工作流附件。

## 6. 交付

写完 release.md 后回复主会话：
- 基线证明结果；
- CCT / 客户侧的未发布差异清单；
- 候选镜像 sha256；
- 执行顺序；
- 需要主会话或用户决定的问题。
