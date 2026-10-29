# REQ-05 阶段 6 任务卡：mor-test 发布准备（只读核对 + 本机构建）

## 目标

产出可审阅、可执行的 mor-test 发布清单。**本阶段不改 mor-test 任何东西**：不停 / 起容器、不执行写 SQL、不写 XXL / MQ / Platform 数据（含运营 API 写）、不发布语言包、不 `docker cp` 写入容器。只做只读核对、只读导出到本机、本机构建与离线演练。阶段 7 的环境变更由主会话执行，须用户明确授权（"你帮我执行吧"）。不提交、不推送。

## 输入

- 方案：[technique.md](../technique.md) 第 7—13 节（接口、DDL、XXL `workflowDataQueryRecheck`、注册块 142、文案键、实施切分）。
- 阶段 4 / 5：工作树 `delivery.md` 阶段 4 实施记录（D-1—D-9）、[verification/stage5-report.md](../verification/stage5-report.md)。
- 研发代码：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-wfdq`（基于 `8d24df8a`，本需求改动未提交；ops-web 由 wfdq-web 在同一 worktree 修改）。
- 模板：`requirements/20261028/commercial-开通项前置门禁/release/`（REQ-01，已于 2026-10-28 20:09—20:33Z 上 mor-test，代码 `ba0e15b5` ⊂ `8d24df8a`）。
- 经验：memory `mor-test-pin-image-ids`、`mor-test-compose-sh-overrides`、`mor-test-release-override-env`、`mor-test-release-card-lessons`、`mor-test-deploy-lessons`、`registration-block-gateway-compile`、`ops-web-shared-tree-release`、`capability-gate-req01-delivered`。

## 涉及服务

| 服务 | 本需求 | 说明 |
|---|---|---|
| platform-support-service | 换镜像 | workflow 发起 / DATA_QUERY / 立即检查 / 模板校验、data-query 公开执行入口、XXL Handler |
| operations-console-bff | 换镜像 | 3 条转发（发起按编码、立即检查、模板选择 / 校验） |
| operations-console-web | 换镜像 | 工作流页面（wfdq-web） |
| card-service | 只核对 | 现网须已是 refclient 时期（不调用已删除的 `/platform/v1/country-regions`） |
| api-gateway | 不换 | 只做快照编译验证 |

## 产出（全部在本目录 `release/`）

1. `baseline.md`：上述服务的运行镜像 ID、来源、env 键；现网 JAR / dist 与候选基线 `8d24df8a` 的逐项比对；mor_platform 工作流表与注册块现状；XXL Platform 执行器；data-query 数据源；语言包当前号。
2. 候选源码 `/Users/kenny/Desktop/Workspace/Code-MoR-wfdq-rel`（`8d24df8a` 干净 worktree）→ 先构建基线并与现网逐字节比对 → 再叠加本需求文件（按 `git status --short` 清单）→ clean 构建，私有仓库 `~/.m3-wfdq`。
3. 镜像 tag `wfdq-20261029a`，记录 sha256；镜像内产物与构建产物逐字节核对。
4. compose 候选 / 回滚覆盖文件（钉 sha256，env 继承现网与历史覆盖，渲染结果与运行容器逐键逐值比较）。
5. SQL：重复预检、前滚 DDL、回滚、块 142（休眠）/ 激活 / 回退 / 验证；mor_platform 只读导出 → 断网 MySQL → 候选 Platform 真实编译器（基线 / 休眠 / 激活 / 回退）。
6. XXL `workflowDataQueryRecheck` 登记参数（Admin API 形状），Platform 执行器分组核对。
7. 语言包：operations-console 当前号、新增 / 变更 key、计划脚本（只读 dry-run），阶段 7 执行。
8. data-query：`MOR_COMMERCIAL_REPLICA` / 只读账号 / 块 131 后台路由现状；3 条 `GOLIVE_*` 模板、动作模板与 `ACC_REQ05_GOLIVE_COMMERCIAL` 定义的 API 脚本（阶段 7 执行）。
9. `runbook.md`（顺序：DDL → Platform → ops-bff → ops-web → 注册块 → XXL → 语言包 → data-query 登记 → 验收；每步验证与回滚）、`acceptance/`（API 子集 + 浏览器走查计划）、`stage6-report.md`。

## 规则

- mor-test 只读：`docker inspect` / `ps`、`printenv`（只记键）、只读 SQL（`SET SESSION TRANSACTION READ ONLY`）、`mysqldump --single-transaction` 导出到本机、`docker cp` 从容器**取出**、XXL / RabbitMQ 只读查询。镜像只在本机 Docker 构建（mor-test 与本机共用同一 Docker 主机，构建即可用，不推送任何仓库）。
- 不改主工作区与研发 worktree 代码；不改 `test-environment/compose*.yaml`；不提交、不推送。
- 阶段 7 才通知并行会话 code-mor-2d；本阶段不通知。
- 遇到无法自行判定的事项写入报告"待裁决"，继续其他部分；阻塞即报告主会话。
