# 阶段 7 mor-test 验收记录（任务卡 G）

> 执行：2026-10-19 21:41–21:49Z（本地 2026-10-20 凌晨），任务卡 G agent。环境 mor-test（Docker context `desktop-linux`），运营入口 `http://localhost:28080`。
> 结论：**通过**。业务链路、直连命中、Web 与全页面只读回归全部符合预期；唯一一次失败是验收脚本漏传查询参数，补测通过，不是缺陷。

## 1. 被验版本

| 组件 | 镜像 ID | 内容 |
|---|---|---|
| commercial-service | `sha256:88dbf74155a9ceb5c82f01376b1bb3ddec7a9fe7ba9077bf42b50b7da04cd71d` | main `cf39a1ee`，exec JAR `fa656f403f8b3968…` |
| operations-console-bff | `sha256:2bd2afee26092ff41ad69b19af40c04c97a053921c1eb5b61cde13fcdc1706aa` | 同上，exec JAR `2578b29475bef142…` |
| operations-console-web | `sha256:edf140b9e25b1a682e9b60fd8954fa36b4906334d26fd924a66ba2e5a5882bc2` | 同上，入口 `assets/index-CZvT-J3j.js` |

数据库：`mor_commercial` 20 张表（目录 7 / 11 / 21）；`mor_platform` 块 95 已启用（24 路由、8 资源、5 授权，PLATFORM_ADMIN 版本 32）。

## 2. 账号

| 账号 | 来源 | 用途 |
|---|---|---|
| A | `test-environment/private/access.md` 的运营管理员（PLATFORM_ADMIN） | 画像、合同、方案、版本、价格、submit、schedule、activate |
| B | 脚本经 `POST /iam/v1/roles` 与 `POST /iam/v1/accounts` 新建：合成角色 `QA_COMMERCIAL_CHECKER_89BD2A`（`238054065030905856`，只含 COMMERCIAL_CONTRACT_READ / PRICING_READ / PRICING_APPROVE），合成人员 `commercial-checker-89bd2a@example.test`（`238054066079481856`），首次登录后改口令 | approve（批准人 ≠ 提交人） |

收尾：B 已停用，合成角色权限已清空（与 `test-environment/scripts/roles_smoke.py` 一致，留下停用人员与空角色，不删除）。没有用 SQL 给任何账号授权。

## 3. 接口验收（[stage7_acceptance.py](./stage7_acceptance.py)，`E2E_TARGET=test`）

运行记录 `local-environment/e2e/runtime/commercial-stage7-20261019T214150Z.json`（RUN `89BD2A`）。首轮 **46/47**。

| # | 步骤 | 状态码 / 结果 |
|---|---|---|
| 1 | A 登录 | 200 |
| 2 | A 导航含「商务」菜单、3 个子菜单、4 个按钮 | 通过 |
| 3 | IAM 授权选项含 3 个商务权限 | 通过 |
| 4–8 | 新建合成角色、人员 B；B 首登、改口令、再登录 | 200 ×5 |
| 9 | A GET `/commercial-admin/v1/pricing-profiles` | 200 |
| 10–15 | 创建并激活画像 `G7_89BD2A_CARD_VISA_CREDIT_DOM`（VISA / CREDIT / DOMESTIC）与 `G7_89BD2A_CARD_DEFAULT`（无条件） | 200 ×4，两者 ACTIVE |
| 16 | 创建 CLIENT 合同 `G7-CLIENT-89BD2A`（MOR、SETTLE_THROUGH_PLATFORM、PLATFORM，OE `AE001`） | 200 |
| 17 | submit-for-signature | 200 |
| 18–20 | 两个参与方 acceptance | 200 ×2，合同 SIGNED |
| 21–22 | activate | 200，合同 ACTIVE |
| 23 | PUT products `[ACQUIRING]` | 200 |
| 24 | POST capability `CARD_VISA`（CURRENCY AED、USD，保证金 0.05 / 180 天） | 200 |
| 25–26 | GET 合同详情 | 200，ACTIVE、产品与开通项一致 |
| 27 | 创建方案 `G7-CLIENT-PRICING-89BD2A`（CLIENT_PRICING） | 200 |
| 28 | 创建版本（BLENDED、EXCLUSIVE） | 200 |
| 29 | PUT prices：AED / USD × 具体（priority 1）/ 兜底（priority 2），各含 CAPTURE_PCT、CAPTURE_FIXED、REFUND_FIXED | 200 |
| 30 | A submit | 200 |
| 31 | A approve 自己提交的版本 | **400**（预期：批准人不能是提交人） |
| 32–33 | B approve | 200，APPROVED |
| 34 | A schedule（生效 = 当前 + 30 秒，`2026-10-19T21:42:24Z`） | 200 |
| 35–36 | 到点后 A activate | 200，ACTIVE |
| 37–38 | A GET `commercial-terms/versions/{id}` 聚合 | 200，版本 ACTIVE、合同、4 条价格 |
| 39 | A GET `versions/{id}/prices`（未带参数） | **400 `INVALID_REQUEST`**：脚本漏传必填的 `entityNo` / `termType`；补测见下 |
| 40–41 | 收尾：停用 B、清空合成角色 | 200 ×2 |
| 42 | commercial 直连 `POST /commercial-prices/v1/resolutions`（USD，VISA / CREDIT / DOMESTIC，一次性探针容器接入 `mor-test-backend`） | 200，命中 `G7_89BD2A_CARD_VISA_CREDIT_DOM` |
| 43 | commercial `GET :19108/actuator/health` | 200，UP |
| 44 | Web `index.html` 引用 `assets/index-CZvT-J3j.js` | 通过 |
| 45–47 | Web GET `/commercial/agreements`、`/commercial/pricing-profiles`、`/commercial/terms` | 200 ×3 |

补测（21:43Z，同一版本）：`GET …/versions/238054073369169920/prices?entityNo=AE001&termType=CLIENT_PRICING&currency=USD` → **200**，2 条 USD 价格（具体、兜底）。脚本已修正为带参数调用并核对条数。

## 4. 页面只读回归（[stage7_regression.py](./stage7_regression.py)）

`pageCapabilities.ts` 全部运营页面逐个打开，每页停 7 秒：**37/37 通过**。没有 ≥ 400 响应、失败请求或控制台错误，覆盖系统管理、平台配置、风控、外部 API、数据保护、通知、Webhook、财务五页与商务三页。截图在 [assets/stage7/](../assets/stage7/)：商务三页与财务三页，定价画像页显示两个合成画像为「启用」。

## 5. 运行观察

- 日志：自 21:34Z 起 commercial、ops-bff、ops-web、Gateway、Platform 的 ERROR 均为 0。没有管理员口令或 Bearer Token 明文。
- 事件：`t_compensation_event` 为 0 行，说明合同状态与版本激活两类发布任务已执行。`commercial.events` 目前没有消费方绑定，与 `unroutable-policy: ACK_AND_WARN` 一致。
- XXL：任务 16 `commercialTermVersionActivation` 有三次运行，都是 trigger 200 / handle 200。分别是手动 21:38:30，定时 21:40 和 21:45。

## 6. 清理

[acceptance-cleanup-test.sql](./acceptance-cleanup-test.sql)（`commercial_app`，21:49:29Z）先 SELECT 核对，结果全部等于期望：2 / 3 / 1 / 2 / 1 / 1 / 1 / 2 / 1 / 1 / 4 / 12 / 7 / 26。随后在同一事务内删除：BCR 版本 26、记录 7、组件 12、价格 4、版本 1、方案 1、开通约束 2、开通 1、产品 1、文件 1、参与方 2、画像条件 3、画像 2。清理后 `mor_commercial` 只剩目录 7 / 11 / 21 行，其余 17 张表全部 0 行。Platform 审计记录、停用的合成人员和空合成角色保留。
