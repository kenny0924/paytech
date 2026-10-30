# 联合查询 · 阶段 6 任务卡：mor-test 发布准备（只读核对 + 本机构建）

## 目标

产出可审阅、可执行的 mor-test 发布清单。**本阶段不改 mor-test 任何东西**：不停 / 起容器、不执行写 SQL / GRANT、不写注册块、不写 XXL / MQ / Platform 数据（含运营 API 登录与写）、不发布语言包、不 `docker cp` 写入容器。只做只读核对、只读导出到本机、本机构建与离线演练、写文档。阶段 7 的环境变更由主会话执行，须用户明确授权。不提交、不推送。

## 输入

- 方案：`Code-MoR/requirements/20261029/platform-support-联合查询/technique.md` 第 9 节（DDL、注册块 144、语言包）。
- 研发代码：`/Users/kenny/Desktop/Workspace/Code-MoR-jq-m`（main 30361be7 + 联合查询，未提交；阶段 5 本机 e2e 41 / 41）。
- 现网：Platform = REQ-03；ops-bff / ops-web / tax / billing / account / acquiring = 税务 tax-20261029a（现已提交为 main fa21d5ce）；块 143、146 已激活（routeCount 633）；operations-console #24。
- 模板：REQ-05 `requirements/20261028/platform-support-workflow按钮发起与数据验证/release/`；经验 memory `mor-test-pin-image-ids`、`mor-test-release-override-env`、`mor-test-release-card-lessons`、`ops-web-shared-tree-release`、`mor-test-compose-sh-overrides`、`mor-test-deploy-lessons`、`registration-block-gateway-compile`。

## 分工

| 角色 | 内容 |
|---|---|
| jq-platform | 发布 worktree、Platform / ops-bff 基线证明与候选镜像、compose、SQL 与离线编译、只读授权 SQL、演示数据方案与登记脚本、语言包计划、运行卡、接口验收、本报告 |
| jq-web | ops-web 基线证明、候选镜像与 compose、浏览器走查计划（web-stage6.md） |

## 涉及服务

| 服务 | 本需求 | 说明 |
|---|---|---|
| platform-support-service | 换镜像 | 键类型、联合查询、模板 joint |
| operations-console-bff | 换镜像 | 同路径转发 |
| operations-console-web | 换镜像 | 联合查询、键类型页面与模板编辑器页签 |
| api-gateway | 不换 | 只做快照编译验证 |

## 产出

本目录：`baseline.md`、`runbook.md`、`stage6-report.md`、`compose-*`、`sql/`、`harness/`、`scripts/`、`jq_bundles.py` + `i18n-bundles/`、`demo/`、`acceptance/`、`precheck/`、`evidence/`。
