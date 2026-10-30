# payout S1 · 阶段 6 发布准备报告（2026-10-30）

> 范围：只做发布准备。mor-test 只读（inspect / printenv / sha256sum / cp 取出 / 只读 SELECT / mysqldump 导出到本机）；未停 / 起 mor-test 应用容器、未写 SQL / XXL / Nacos / MQ。例外：主机卡死重启后，经用户同意把停着的 mor-test / mor-local 基础设施容器原样 `docker start`（不改镜像与配置）。镜像只在本机构建；未提交、未推送。**阶段 7 须用户明确授权。**
> 材料：[W8-report.md](./W8-report.md)（源码、测试、镜像、compose）、[W9-report.md](./W9-report.md)（SQL、演练、语言包、验收）、[baseline.md](./baseline.md)、[runbook.md](./runbook.md)、`sql/`、`compose-*.yaml`、`harness/`、`scripts/`、`acceptance/`、`cleanup/`、`i18n-bundles/`、`precheck/`、`evidence/`。

## 1. 结论

| 项 | 结果 |
|---|---|
| 基线 | main `9eab368c`；7 个现网服务镜像与记录一致，account / Platform / ops-bff / Gateway JAR 与 main 构建 0 差异，ops-web 277 个文件逐字节相同，client-bff 现网独有 0 |
| 候选源码 | `Code-MoR-po-rel` = `9eab368c` + 247 个文件，NO DRIFT；12 处冲突合并经主会话复跑 `git merge-file` 复核；测试中补正两处测试代码缺陷（ops-bff `@Bean` 漏合、payout 通知 Mock 路径），提交以 po-rel 为准 |
| 测试 | reactor test-compile 126 模块；payout 33/0、account 72/0、Platform 受影响 6 类 41/0、client-bff 108/0、ops-bff 2543/0、arch-rules 70/0；ops-web tsc、1686/1686、build、i18n 导出通过 |
| 镜像 | 6 个，tag `payout-s1-20261030a`，compose 已钉 sha256（主会话核对 6 个 ID 与 compose 一致）：payout `75a6337c`、account `8dfd1060`、Platform `0ea9cc2d`、client-bff `24d3aa05`、ops-bff `626a74a7`、ops-web `acb33e94` |
| compose | 12 个覆盖文件；回滚文件与现网 env 逐键逐值相同；候选只多 Platform `SPRING_APPLICATION_JSON` 中 payout-service 四个通知模板 |
| payout 落位 | IP `172.30.73.44`、Region 0 / Worker 16（主会话核对 .44 空闲、16 未被任何运行中服务使用）、18117 / 19117；不需要 Nacos 配置文件与内部认证登记；XXL 分组 + `compensationEventScan` |
| 数据侧 | 预检全部空闲（块 147、Vault 880—885、库 / 账号）；Vault 版本 21，DML 按 MAX+1 不需改；Vault 10 s 刷新，无需重启 |
| 离线演练 | 现网与候选 Platform JAR 各一轮 45 步全部符合预期；激活 652 → 671 条路由、快照 1 080 333 B（距 4 MiB 余 3 113 971 B）；回滚后 Vault / payout 与导出 0 差异，Platform 仅管理员角色 version 一行（预期） |
| 语言包 | 基于 #28：新增 223 键（nav 7、payout 216），新建命名空间 payout，干跑通过；client-console 不需要 |
| 验收脚本 | `payout_acceptance.py` + `payout_browser.mortest.mjs`，语法与只读干跑通过；`plan` 唯一失败项为 D-4 缺 Client 凭据 |

## 2. 影响窗口

account 约 35 s、Platform 约 60 s（登录与所有经 Platform 的请求不可用）、两个 BFF 各约 30 s、ops-web 约 5 s；payout 为新服务，Gateway 热加载无中断。

## 3. 待用户裁决

| # | 事项 | 建议 |
|---|---|---|
| U-1 | client-bff 是否随发 | 发（否则块 147 的 Client 路由指向不存在的接口，Client 侧 API 验收做不了） |
| U-2 | AC-09 通知邮件验证方式（mor-test 走真实 SES 沙箱，Mailpit 收不到） | 用 SES 邮件模拟器地址作为租户管理员成员收件验证 |
| U-3 | Client 所有者凭据（A = DEMO-CL-001、B） | 用户提供 `~/Code-MoR-backups/payout-s1-stage7/clients.json`（0600）；不提供则只验运营侧 |
| U-4 | 是否在 mor-test 登记计费事实造余额 | 不登记（会产生不可删除的分录） |
| U-5 | base compose 补丁 `compose-base-payout.patch` | 随阶段 8 提交合入；阶段 7 不依赖 |
| U-6 | 本机残留库 `reh_payout_d`（87 张 Platform 表，07:49 建，本需求阶段 4 演练拷贝未删） | DROP |

## 4. 遗留（不阻塞阶段 7）

- `paytech/payout/docs/database/sql/payout-schema.sql` 有 9 张表且 `t_compensation_event` 为旧 Finance 形状，与代码 / `sql/12` 不一致——阶段 8 提交前修正。
- 本机 e2e 共享栈宿主机进程在重启后未恢复（属明细账运维会话，未动）。
- 本机阶段 5 合成数据清理草稿 `cleanup/local-stage5-cleanup-draft.sql`（未执行）。
