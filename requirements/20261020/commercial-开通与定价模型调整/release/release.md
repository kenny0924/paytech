# commercial 开通与定价模型调整（一期）· mor-test 发布记录

> 用户 2026-10-20 授权阶段 1–7 连续执行（含 mor-test SQL、Platform 注册、镜像替换、测试数据）。

## 1. 发布范围与制品

| 组件 | 动作 | 制品 / 结果 |
|---|---|---|
| 源码 | 集成 worktree `.claude/worktrees/cp-int` `6570ef73` | main `4ab205e0` + A `84377cb2` + B `56ba38fe` + C `f48fe526` + 表归属修正 |
| commercial-service | 替换 | `sha256:4a0c4531794644e78c65b4c859c0d7f1f87bc219054dfbf9e1735997172f13e4`（JAR 前缀 `79deb86c7dc9f9cd`） |
| operations-console-bff | 替换 | `sha256:9708ccdb8fa733754bd5142e2fc072ac33599b627b6c257ac75ca7308cf2360e`（JAR `7d88741e6a886734`） |
| platform-support-service | 替换（只为 capabilities JSON） | `sha256:95348566542d280aa0cb56039860b909c0e13de3f45d56ddeb1b2a82bbe0a8cd`（JAR `37ca10630c770f78`） |
| operations-console-web | 替换 | `sha256:abab7475643d1960b74c1cd0e42d458154c222967f1eba9224ddaa36f4befa7e`（入口 `assets/index-DFtXS91w.js`） |
| mor_commercial | root 执行 [ddl.sql](../db/commercial/ddl.sql) → [migration.sql](../db/commercial/migration.sql) | 22 张表；CARD_* 合并为 CARD |
| mor_platform | 块 124 注册 + [test-activate.sql](../db/platform-support-service/test-activate.sql) | 3 / 3 / 5 / 17 / 94 / 3 / 102 行；激活 17 / 5 / 3 / 1 |
| compose | [compose-candidate.yaml](./compose-candidate.yaml)（tag `capability-20261021`，固定 sha256） | commercial env 沿用上一版候选 |

镜像以 `docker build` 显式 tag 构建（Java：`test-environment/java/Dockerfile`，health-probe 与运行容器逐字节一致；Web：`operations-console-web/Dockerfile`），未改 `:development` tag 与 `test-environment/artifacts`。

## 2. 发布前核对

- 不回退：集成构建 JAR 与 mor-test 运行 JAR 逐类比较——Platform 仅 `console-resource-capabilities.json` 不同；ops-bff、commercial 仅商务类与 commercial-api / core 不同。四服务渲染后的环境变量与运行容器一致（Web 仅 nginx 基础镜像变量）。
- 迁移演练：mor-test `mor_commercial` 备份导入本地临时库执行 ddl + migration，输出见备份目录 `rehearsal-migration.out`；临时库已删除。
- mor_platform 块 124 执行前 0 行；PLATFORM_ADMIN 版本 36；mor-test 容器全部 healthy。

## 3. 执行记录（UTC，2026-10-20）

| 时间 | 步骤 | 结果 |
|---|---|---|
| 20:57 | 备份 | `/Users/kenny/Code-MoR-backups/20261020T205752Z-commercial-capability-pricing`（0700 / 0600，SHA256SUMS）：mor_commercial 整库、mor_platform 注册 8 表、镜像 ID、候选 compose、演练输出 |
| 21:00:02 | 停 commercial-service | exited |
| 21:00 | DDL | rc 0，22 张表（`sql-1-ddl.out`） |
| 21:00 | 迁移试跑（COMMIT→ROLLBACK） | 输出与演练逐行一致，回滚后数据不变（`sql-2.1-migration-trial.out`） |
| 21:00:34 | 迁移提交 | 输出与演练一致；开通项 APPLE_PAY 1 / CARD 3 / GOOGLE_PAY 1 / PAYOUT_LOCAL 1；价格 50 条不变（`sql-2.2-migration-commit.out`） |
| 21:00:54 | 块 124 注册（试跑回滚后提交） | 3 / 3 / 5 / 17 / 94 / 3 / 102（`sql-3.*`） |
| 21:01:00–21:02:01 | 替换 platform → commercial → ops-bff → ops-web | 全部 healthy、重启 0、启动日志 ERROR 0、运行镜像 = 候选 |
| 21:02:20 | 激活（试跑回滚后提交） | 路由 17、资源 5、授权 3、PLATFORM_ADMIN 版本 36 → 37（`sql-4.*`） |
| 21:03 | 冒烟（真实入口） | 维度清单 14、ACQUIRING 目录 CARD / APPLE_PAY / GOOGLE_PAY / TOKEN_STORAGE、TOKEN_STORED 挂 TOKEN_STORAGE、5 个新资源出现在导航 |

**人工确认项（迁移核对 5）：** DEMO-CL-001（`238331035912310784`）三条卡类开通项合并：原 CARD_AMEX 只限 USD，合并后 CARD 币种为 AED、USD 并集，Amex AED 由 CARD 兜底价覆盖——演示数据，接受。

| 21:30:17 | 缺陷修复重部署 commercial（阶段 7 验收发现：基于模板生成 / 补齐会保留只带功能费的价格行，相关性未锚定支付方式开通项；A 线 `9ed4c39c`，集成 `c49008e3`，commercial 195/195） | `sha256:83a05343fa93b2837cba57c7dcf735ea606f330097039f38e0a97fc39f9317da`（tag `capability-20261021b`，JAR `d32ccfd75c27168d`），healthy、重启 0、ERROR 0；候选 compose 已更新 |
| 21:53:50 | 缺陷 D2 修复重部署 commercial（模板版本读回结算字段为空，按 clientId 判断 Client 条款；A 线 `a9828758`，集成 `c6eea71a`，commercial 196/196） | `sha256:c6c9b8f663aeb69c09cf2e2d494429bff2dbc38c8aaf632d88cbcbaaa5d0221b`（tag `capability-20261021c`，JAR `0e3bc94ab646a2b1`），healthy、重启 0、ERROR 0；候选 compose 已更新 |
| 2026-10-21 03:2x | 对账会话 code-mor-93 发布对账切片 1，替换共享的 platform / ops-bff / ops-web（tag recon-slice1-20261021，源 = c6eea71a + 对账改动；对方核对本需求产物不回退：capabilities JSON 块 124 键相同、ops-web 以 c6eea71a 构建与 abab7475 逐字节一致） | platform `sha256:79e33d43…`、ops-bff `sha256:9520a545…`、ops-web `sha256:edfb6628…`；候选 compose 已改指这三个镜像，防止重跑本候选回退对账 |
| 2026-10-21 03:25:35 | 验收反馈 F1–F3：只替换 ops-web（源 = 对账 recon-slice1 源码叠加清单中 19 个 ops-web 文件 + cp-web `41f129c8` 的 9 个 commercial 文件，基线 c6eea71a；叠加前构建与现网 edfb6628 dist 157 文件逐字节一致；等对账会话页面走查结束后执行） | ops-web `sha256:333e7f2be206155567146134ce357159aa59bfeaa07b2d1b364a0666e252249b`（tag capability-20261021d，入口 `index-DPjes7gL.js`），healthy、重启 0；回滚 `sha256:edfb6628…`；候选 compose 已更新 |
| 2026-10-21 03:58:51 | 验收反馈第二轮 F4–F6：只替换 ops-web（源 = 03:25 版源码，重建与现网 333e7f2b dist 157 文件逐字节一致 + cp-web `32d256a7`、`c9229fb2` 的 commercial 文件；对账会话确认未在走查） | ops-web `sha256:139c157e50fa117524080e47a950fe322879255423e4d06af76dd51106a1b99a`（tag capability-20261021e，入口 `index-DmG4YUUF.js`），healthy、重启 0；回滚 `sha256:333e7f2b…`；候选 compose 已更新 |

## 4. 回退

1. 停用：[test-deactivate.sql](../db/platform-support-service/test-deactivate.sql)。
2. 镜像：commercial `sha256:59bb000d…`（commercial-20261020b）、ops-bff `sha256:bec85826…`（channel-20261020）、ops-web `sha256:bbb88dd8…`（opsux-20261020o）、platform `sha256:e5a06814…`（channel-20261020）。
3. 数据：迁移不可逆，回退 = 备份目录 `mor_commercial.sql` 整库恢复（之后新写入会丢失）；块 124 行可删除。

## 5. 验收

见 [verification/stage7-acceptance.md](../verification/stage7-acceptance.md)：12 / 13 通过（AC-09 环境阻塞）；D1、D2 修复后复验 10 / 10、8 / 8。最终运行镜像以第 3 节最后一行（capability-20261021c）为准。
