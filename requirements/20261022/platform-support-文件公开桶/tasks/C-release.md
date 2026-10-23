# 任务卡 C：mor-test 发布包（阶段 6 准备）

> 用户 2026-10-23 确认阶段 4—5，进入阶段 6。本卡**只做准备**：不停 / 起 / 替换 mor-test 任何容器；不执行写 SQL；不调用 mor-test 写接口；不提交、不 install、不改 `:development` tag；不改主工作区除本需求目录以外的文件。环境变更在阶段 7 由主会话执行（另需用户授权）。

## 1. 必读

- 本需求目录（`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-文件公开桶/`）：`requirement.md`、`technique.md`（尤其 4.4 兼容与发布顺序）、`tasks/common.md`、`reports/A-platform-report.md`、`reports/B-operations-report.md`、`delivery.md`（基线记录）。
- 前置需求的发布包作模板：`requirements/20261022/platform-support-文件多存储并存/release/`、`release-s2/`（release.md、compose-candidate / rollback、sql、scripts、acceptance）。
- `test-environment/README.md`、`test-environment/compose.sh`；经验：候选覆盖文件必须逐键带上历次覆盖文件中的环境变量（先在运行容器 `printenv`，再 grep `requirements/*/release*/compose-candidate*.yaml`）；部署只认固定 `sha256:`；`compose.sh` 只传覆盖文件；不要 `docker cp` 进运行容器（读出可以）；访问 127.0.0.1 管理端口用 `--network container:<target>` 一次性旁路容器。

## 2. 源码

- 集成目录：`/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-int` = main `22ede435` + A（209 个路径）+ B（72 个路径），验证见 delivery.md 阶段 5。**不要从主工作区构建**（混有他人未提交改动）。
- 先确认 main 当前 HEAD 相对 `22ede435` 的新提交是否涉及本次要发的服务；涉及就报告，不自行合并。

## 3. 服务、消费方与基线

- **要换的服务**：platform-support-service、operations-console-bff、operations-console-web，以及 `paytech-platform-support-api` 的所有生产消费方（因为内部 HTTP 客户端严格反序列化，旧消费方读不了新 Platform 的新字段）。在 `22ede435` 上 grep 生产代码确认消费方清单，至少包括 client-console-bff、client-master-service（CCT），以及卡 BIN 引入的 card 服务（`de4f7ba4`）；逐个确认。
- **当前运行镜像**：`docker inspect <容器> --format '{{.Image}}'` 读出；已知 ops-web `8bdbdd44`、platform `96da579b`、ops-bff `9c382db3`（code-mor-6b 卡 BIN + 3DS 发布，覆盖文件 `requirements/20261022/payment-3DS全链路/release/compose-candidate.yaml`）。
- **基线证明**（方法同前置需求 release.md 第 1 节：JAR 内 `BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目比较，第三方 JAR 整包 SHA-256；Web 比 `dist/` 与 nginx 配置逐字节）：用 `22ede435`（不加本需求）构建每个要换的服务，与运行容器比较。
  - **ops-web**：现网 = `42879401` + 卡 BIN 改动 + 主工作区 16 个未提交文件（按钮布局 6 个 + 数据查询 UX 10 个）。用 `22ede435` + 这 16 个文件（从主工作区原样复制）构建，应与现网 dist 一致；本需求的 ops-web 候选 = 集成目录 + 数据查询 10 个文件（按钮布局 6 个集成目录已有，核对与主工作区一致）。数据查询文件清单向 code-mor-55 的发布记录核对（`requirements/20261022/platform-support-数据查询服务/release-ux/`）。
  - 其余服务若与 `22ede435` 不一致，列出差异来自哪些提交 / 会话，只报告不取舍。

## 4. 兼容性与顺序（写进 release.md）

至少回答：
- **DDL 对旧代码**：新列默认值、三个变宽唯一键对当前运行的旧 Platform 是否安全（本机已验证：按旧代码构建的会话在新 Schema 上跑测试正常）。mor-test 现有数据（含 code-mor-6b 新增的写入路由 `operations-console-bff` → `CARD_BIN_CSV`、用户手工登记的 `S3_TEST` 实例、桶与 `client-console-bff` 路由）在回填与新唯一键下是否成立——用只读 SQL 核对。
- **替换顺序**：先发全部消费方（新 api，`Integer` 可空，可容忍旧 Platform），再发 Platform，最后 ops-web；块 130 两条路由路径修改与块 134 激活放在哪一步（旧 ops-bff 与新路径、新 ops-bff 与旧路径的组合各会怎样）。给出最短不可用窗口。
- **环境变量**：每个要换的服务逐键带上现有覆盖文件的变量；本需求是否需要新增变量（匿名检查超时取实例设置，应不需要）——确认。
- **AWS / MinIO 前提**（写成需要用户配合的清单）：`S3_TEST` 的 IAM 策略需补 `s3:ListBucketVersions`（已有）、`s3:GetObjectVersion`（已有）、`s3:PutObject` 用于复制、`s3:GetObject` 源版本读取——按 A 报告核对复制所需权限；公开桶验收需要一个开启匿名读取的测试桶（AWS 或 mor-test MinIO，给出两种做法与所需操作，建桶由用户或主会话在阶段 7 授权后执行）。CORS 需允许不带 `If-None-Match` 的 PUT（核对现有 S3_TEST CORS 是否已满足）。

## 5. 发布物（写到本需求 `release/`）

- `release.md`（结构同前置需求：源码与镜像、基线证明、兼容性、只读核对结果、发布物、执行步骤、回滚、验收、执行记录占位、风险与待决定事项）；
- `compose-candidate.yaml` / `compose-rollback.yaml`（钉 sha256，候选镜像 tag `pubbucket-20261023a`，在集成目录构建）；
- `sql/00-precheck.sql`（只读，阶段 6 执行一次，结果写入 release.md）、`sql/01-platform-ddl.sql`、`sql/02-platform-backfill.sql`（取自 `db/ddl.sql`、`db/dml.sql`）、`sql/10-block130-route-path.sql`、`sql/11-registration-block134.sql`（取自 `db/platform-support/`）、`test-activate.sql` / `test-deactivate.sql`、`sql/99-postcheck.sql`；
- `scripts/backup.sh`、`scripts/apply_sql.sh`（参照前置需求）；
- `acceptance/`：
  - 存储管理：私有桶检查（已有 `mor-platform-files`、`mor-test-platform-files-k7q2` 重查应仍 OK）、公开桶登记与检查（失败与成功各一）、写入规则矩阵（含公开路由与不回落）、检查策略新建 / 修订 / 激活 / 废弃 / 停用拒绝；
  - 内部文件接口（经旁路探针容器，带 `X-Caller-Service`）：公开上传 → 检查通过后 `publicUrl` 可匿名读取、私有上传无 `publicUrl`、同地址替换（私有检查前读旧版本；公开替换后读到新内容）、并发替换 409；
  - 文件记录：列表筛选、详情版本 / 检查结果 / 上传记录、下载写审计；
  - 回归：客户入驻上传与审核下载（client-console-bff 路由仍写 S3_TEST）、工作流附件、卡 BIN CSV 上传；
  - 浏览器冒烟（参照前置需求 `stage7_browser.py`，运营控制台 http://localhost:28080，管理员凭据取 `test-environment/private/access.md`，不打印）：存储管理四个页签、文件记录列表与详情、审计页；
  - `cleanup`：只删本次运行生成的数据；不删除存储对象以外的任何既有配置。

## 6. 交付

完成后回复主会话：基线证明结果、消费方清单、候选镜像 sha256、执行顺序与不可用窗口、只读核对结果、需要用户在 AWS / MinIO 上配合的操作、需要主会话或用户决定的问题。
