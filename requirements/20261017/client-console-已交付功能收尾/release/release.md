# Client Console 已交付功能收尾：mor-test 发布记录（2026-10-17）

> 授权：2026-10-17 用户“可以”确认阶段 6+7（本地提交、test `mor_cct` 备份后回填、只替换 test CCT、跑验收并写入验收数据）。
> 提交：`3209429 feat(client-console): 已交付功能收尾（资料批准时间、开发者浏览器验收上 test）`（16 个文件，只含本需求）。
> 范围：F-02、F-03。F-01（真实邀请邮件）待用户提供 SES 已验证的收件地址，本次未做。不含 Platform、Client BFF、Client Web、Gateway 镜像与其他环境。

## 1. 制品

- 干净 worktree `3209429`；离线 install `paytech-vault-client`、`risk-api`、`paytech-payment-api`、`platform-support-api` 后 install CCT（JDK 25，`-DskipTests -Djacoco.skip=true`）；健康探针同一 worktree 编译。`f7b5500..3209429` 在 CCT 目录只有本次提交。
- JAR SHA-256 前缀 `e66ccc98bdcfd8eb`；制品 `test-environment/artifacts/client-wrapup-20261017/`；镜像 `mor-test/client-contract-service:client-wrapup-20261017`，ID 固定在 [compose-candidate-20261017.yaml](./compose-candidate-20261017.yaml)。

| 服务 | 发布前（回退用） | 发布后 |
|---|---|---|
| client-contract-service | `sha256:01ec90654b20…`（`f7b5500`） | `sha256:8268e4cf18dc…` |

## 2. 执行（UTC 2026-10-17）

| 时间 | 步骤 | 结果 |
|---|---|---|
| 10:45 | 通知在线会话（code-mor-e0、code-mor-1b；两者均不使用 test） | — |
| 10:46 | 只读预检：test CCT 为 `01ec90654b20…`；已批准且 `approved_time` 为空的 Client 21 个；已填写 0；资料共 29 行 | 通过 |
| 10:47 | 备份 `mor_cct.t_party_profile`（29 行，逐行 INSERT 与源表一致）、待回填 profile_id 清单、CCT 容器 inspect → `~/Code-MoR-backups/20261017-client-wrapup-test/20261017T104735Z/`（0700/0600，SHA256SUMS） | 完成 |
| 10:47 | 执行 [db/dml.sql](../db/dml.sql) 两次 | 21 行回填，剩余 0；第二次无变化 |
| 10:47:49 | 替换 CCT（`compose.sh -f release/compose-candidate-20261017.yaml up -d --no-deps --no-build --pull never --wait client-contract-service`） | 18 秒 healthy，日志无 ERROR。说明：通知中写“约 5 分钟后”，实际在通知后约 2—3 分钟替换；两个在线会话均确认不使用 test |
| 10:48—11:00 | 验收（第 3 节） | 全部通过 |

## 3. 验收（经正式入口 `https://localhost:28443` / `http://localhost:28080`）

| 项 | 结果 |
|---|---|
| `E2E_TARGET=test developer_browser.mjs`（F-02，账号由 `team_browser_accounts.py` 构造） | **10/10 PASS**，另有 3 项按设计 SKIP（测试事件、投递列表与详情、等待重试不显示重发；test 不向外网推送，本机已覆盖）；端点 `https://example.com/code-mor-acceptance` 新建后已停用；HTTP≥400 与控制台错误 0 |
| `E2E_TARGET=test workspace_flow.py` | **53/53**；新批准 Client 的 `approved_time`、申请结论 `update_time`、首页 `approvedAt` 均为 `2026-10-17T10:49:12.041735Z`（AC-04） |
| `E2E_TARGET=test team_flow.py` | **123/123**（被邀请成员读取工作区摘要回归） |
| 日志 | CCT、Client BFF、Platform 自 10:47 起 ERROR 0 |

## 4. 回退

用第 1 节“发布前”镜像 ID 写覆盖文件后 `compose.sh -f <覆盖> up -d --no-deps --no-build --pull never --wait client-contract-service`；旧 CCT 不读 `approved_time`，回填数据可保留。必须撤销回填时：`UPDATE t_party_profile SET approved_time=NULL WHERE profile_id IN (<备份目录 backfill-profile-ids.txt>)`。

## 5. test 遗留数据

本次新增的 `team-owner-*`、`browser-invitee-*`、`workspace-*`、`team-*@example.test` 验收账号及其申请、邀请、成员、凭证；1 个已停用的 `example.com` Webhook 端点（开发者浏览器验收）。不清理。
