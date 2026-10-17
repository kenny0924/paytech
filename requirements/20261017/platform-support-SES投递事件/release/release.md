# Platform SES 投递事件：mor-test 发布清单

阶段 6 评审稿（2026-10-17）。只读核对 mor-test，未改任何 test 资源。确认后进入阶段 7 执行。

**本次：mor-test 的 Platform、Operations BFF、Operations Web 换成包含本需求的镜像（三者同批，因 BFF 严格解析 Platform 响应）；`mor_platform` 两张表加 9 列；Platform 的 Compose 加 `NOTIFICATION_SES_EVENTS_ENABLED: "true"`；你在 AWS 新建 SNS、SQS 与 Configuration Set；`SES_PRIMARY` 填上队列 URL；用 SES 模拟收件地址验收送达、退信、投诉回写。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 数据库 `mor_platform` | [ddl.sql](../db/platform-support-service/ddl.sql)：`t_notification_provider` 加 4 列、`t_notification_attempt` 加 5 列，均可空；`resolution_source` 只改注释。在线执行，旧 Platform 不读新列 |
| 配置（Compose） | Platform 加 `NOTIFICATION_SES_EVENTS_ENABLED: "true"`（`NOTIFICATION_ENABLED`、`NOTIFICATION_SES_ENABLED` 已是 true；三者缺一拉取不运行，见阶段 5 发现）。拉取间隔等用默认（60 秒、长轮询 10 秒、每轮最多 5 批） |
| Vault | 不改。拉取复用已有规则 `REVEAL / NOTIFICATION_PROVIDER_CREDENTIAL_V1 / NOTIFICATION_DELIVERY`；验收提交通知用的 `PROTECT/REVEAL / EMAIL_ADDRESS_V1 / PLATFORM_NOTIFICATION_EMAIL_V1` 已存在 |
| 登记 DML | 无（不新增接口与按钮） |
| 不动 | Gateway、Vault 程序、Risk、CCT、Commerce、Nginx；不推送 Git 与镜像，不碰生产 |

## 2. 只读核对结果（2026-10-17 11:4x +04）

| 项 | 结果 |
|---|---|
| 运行镜像 | Platform `0cdfe0e9…`（06:53Z 起，Webhook 错误码发布）、BFF `aec81706…`、Web `04f9f7d1…`。Risk 会话正在 mor-test 发布 Risk / BFF / Web，Gateway 幂等会话也已获授权替换 Commerce / Platform / Gateway / BFF / Web；执行时以届时的运行镜像为回退基线 |
| `mor_platform` | `t_notification_provider` 13 列、`t_notification_attempt` 14 列，新列都不存在；`SES_PRIMARY`：`AMAZON_SES`、ACTIVE、`ap-southeast-1`、`noreply@payorigo.com`、无 Configuration Set、已录入密钥、版本 4；`t_notification_attempt` 0 行（mor-test 至今没有普通通知，认证邮件与测试发送不落尝试表） |
| `mor_vault` | 上述三条 Platform 规则均为 ACTIVE |
| Platform 配置 | `NOTIFICATION_ENABLED=true`、`NOTIFICATION_SES_ENABLED=true`；`notification.http.callers` 为空（没有任何服务能提交普通通知） |

## 3. 你在 AWS 上的操作（区域 ap-southeast-1，和 `SES_PRIMARY` 一致）

名称是建议值，可以改，最后把队列 URL 发给我。

1. **确认抑制列表**：SES → Configuration → Suppression list → Account-level settings，确认已开启，原因勾选 **Bounces** 和 **Complaints**（Q-02 前提：硬退信、投诉地址由 SES 自动抑制）。
2. **建死信队列**：SQS → Create queue → Standard，名称 `mor-test-ses-events-dlq`，Message retention period 改为 14 天，其余默认 → Create。
3. **建事件队列**：SQS → Create queue → Standard，名称 `mor-test-ses-events`：
   - Visibility timeout 60 秒；
   - Encryption 保持默认的 SSE-SQS（不要选 KMS，否则还要给 SNS 配 KMS 权限）；
   - Dead-letter queue：Enabled，选 `mor-test-ses-events-dlq`，Maximum receives 填 5；
   - Create。
4. **建 SNS 主题**：SNS → Topics → Create topic → Standard，名称 `mor-test-ses-events` → Create。
5. **队列订阅主题**：回到 SQS 的 `mor-test-ses-events` → 右上角 **Subscribe to Amazon SNS topic** → 选 `mor-test-ses-events` → Save（这一步会自动给队列加上允许该主题投递的访问策略）。然后到 SNS → Subscriptions，选中这条订阅 → Edit → 打开 **Enable raw message delivery** → Save。
6. **建 Configuration Set**：SES → Configuration sets → Create set，名称 `mor-test-events` → Create。进入它 → Event destinations → Add destination：
   - Event types 只勾 **Deliveries**、**Hard bounces**（控制台叫 Bounces）、**Complaints**、**Rejects**；
   - Destination 选 **Amazon SNS**，主题选 `mor-test-ses-events`；
   - 保存。
7. **设为域名默认**：SES → Identities → `payorigo.com` → Configuration set（或 Default configuration set）→ Edit → 勾选使用默认 Configuration Set，选 `mor-test-events` → Save。之后从这个域名发出的所有邮件（含认证邮件、测试发送）都会产生事件；认证邮件和测试发送的事件对不上发送尝试，Platform 只计数后删除（F-06）。
8. **给 IAM 用户加权限**：IAM → Users → Platform 发信用的那个用户 → Add permissions → Create inline policy → JSON，填入（把 `<账号ID>` 换成 12 位账号 ID）：

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": ["sqs:ReceiveMessage", "sqs:DeleteMessage"],
       "Resource": "arn:aws:sqs:ap-southeast-1:<账号ID>:mor-test-ses-events"
     }]
   }
   ```

   命名如 `mor-test-ses-events-consume` → Create。原有发信权限不动。
9. **发我队列 URL**：SQS 队列详情页的 URL，形如 `https://sqs.ap-southeast-1.amazonaws.com/<账号ID>/mor-test-ses-events`。不需要发任何密钥。

## 4. 需要你决定

**D1 构建基线。** mor-test 上 BFF、Web（可能还有 Platform）正被 Risk、Gateway 幂等两个会话发布，它们的改动尚未提交。我替换这三个服务时必须包含它们已上线的改动，否则会把它们抹掉。
- 选项 A（建议）：等 Risk、Gateway 幂等会话完成发布并本地提交后，**先本地提交本需求**（阶段 8 的提交提前，只含本需求的行，不推送），再从该提交建干净 worktree 构建。镜像对应一个提交号，并且自然包含前面已提交的改动。
- 选项 B：不等提交，在干净 worktree 复制它们的未提交文件和本需求文件后构建。快，但镜像对应不到提交，复制遗漏会抹掉别人的改动。

**D2 真实链路验收方式。** AC-11 要求送达、退信、投诉都写回发送记录。mor-test 至今没有普通通知（认证邮件和测试发送不落尝试表，事件对不上），要产生可回写的尝试，只能经内部接口提交通知，而它的调用方白名单是空的。
- 选项 A（建议）：验收期间临时给 Platform 加一个调用方 `e2esesevents`，只允许模板 `E2E_SES_EVT_TEST`（Platform 多重启 2 次，每次约 25 秒）；经运营后台新建该模板和路由（主账号 `SES_PRIMARY`），从 test 内网向 3 个 SES 模拟地址提交一条通知：`success@simulator.amazonses.com`、`bounce@simulator.amazonses.com`、`complaint@simulator.amazonses.com`。核对 3 个投递分别变为：已送达；失败（硬退信）；已送达并记下投诉（SES 对投诉模拟地址先投递成功、再回投诉）。页面截图。结束后删掉临时调用方、模板、路由和这条记录。模拟地址不影响发信信誉，沙箱下也能发。
- 选项 B：只用「测试发送」发到模拟地址。能验证 SES → SNS → SQS → 拉取 → 删除整条链路（账号列表「最近事件」会更新），但事件对不上尝试，**回写发送记录这一项在 test 上验不到**，只有自动测试和本机合成数据作证据。

## 5. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 2 节查询；记录届时的三个运行镜像作为回退基线；确认 Risk、Gateway 幂等会话的发布窗口已结束。
2. **备份**：`mor_platform` 的 `t_notification_provider`、`t_notification_attempt`（结构与数据）、`compose.applications.yaml` 副本、三个容器 inspect；存 `~/Code-MoR-backups/20261017-ses-events-test/<UTC>/`（0700/0600，SHA256SUMS）。
3. **构建**（按 D1）：Platform、BFF 与依赖 API 同 reactor 离线打包，Web `npm test` + `npm run build`；镜像 tag `mor-test/<服务>:ses-events-20261017`，记录镜像 ID 与 jar SHA-256。
4. **DDL**：在 `mor_platform` 执行 ddl.sql，核对两表各新增 4 / 5 列。
5. **改 Compose 并替换 Platform → BFF → Web**，紧接着依次执行（`up -d --no-deps --no-build --pull never --wait`）。Platform 换好到 BFF 换好之间约 30 秒，旧 BFF 解析不了新字段，「发送账号」列表与发送记录详情会报错；其他页面不受影响。
6. **配置队列**：拿到你的队列 URL 后，经运营后台编辑 `SES_PRIMARY` 填入（版本 4 → 5，记审计）；1 分钟内列表应显示「接收正常」，若显示「拉取失败：SQS_ACCESS_DENIED」等则按错误码回查 AWS 第 5、8 步。
7. **验收**（按 D2）。另做：页面回归「发送账号」「发送记录」与管理员可见页面无 ≥400；Platform 日志无 ERROR、无队列消息原文与收件地址。
8. **清理**（D2 A）：删除临时调用方配置并重启 Platform；删除 `E2E_SES_EVT_TEST` 模板、路由与这条通知记录；审计与 Vault 中的模拟地址密文保留。
9. **记录**：写入本文第 7 节，更新交付记录与台账；Compose 修改与发布记录的提交另行授权。

## 6. 恢复

| 情况 | 处理 |
|---|---|
| Platform 不健康 | 恢复 Compose 副本，用回退基线镜像回退 Platform，**BFF、Web 同时回退**（新 BFF 也解析不了旧 Platform 的响应）；新列对旧 Platform 无影响 |
| BFF / Web 不健康 | 三者一起回退到回退基线 |
| 拉取异常（错误码、日志刷屏等） | 把 Compose 的 `NOTIFICATION_SES_EVENTS_ENABLED` 改回 false 只重启 Platform，或在页面清空 `SES_PRIMARY` 的队列 URL（不用重启）。发信不受影响；队列消息留在 SQS（保留 4 天，超过 5 次接收进死信队列） |
| AWS 侧撤销 | 在 `payorigo.com` 取消默认 Configuration Set 即停止产生事件；SNS、SQS 可保留或删除 |
| 数据 | 新列保留（旧版本不读）；回退 DDL 会丢失已回写的事件结果，只有确认不再需要时再单独授权 |

## 7. 执行记录

（阶段 7 填写）
