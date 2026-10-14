# Go 服务技术栈与代码规范（评审稿）

- 日期：2026-10-14
- 状态：**已确认并实施（2026-10-14）**；实施差异见第 6 节
- 适用：Vault Server（`vault-service`）及今后新增的 Go 服务
- 背景：用户认为 Vault 现有代码可读性不足（接口在一个处理函数里按 method/path `switch case` 分发，SQL 为散落的字符串 + 手工 Scan），后续还要增加维护类接口，希望使用框架、按 Java 侧的规范组织代码
- 权威关系：确认后写入 [TECHNOLOGY_STACK.md](../../../agent-docs/TECHNOLOGY_STACK.md)（目前只登记 “Vault V1 Server：Go 1.27.1”），并新增 Go 开发规范文档；Vault 代码迁移另行立项，按阶段交付

## 1. 选型结论（建议）

| 领域 | 选型 | 理由 | 不选 |
|---|---|---|---|
| 语言 | Go 1.27.x（沿用） | 已在用 | — |
| HTTP 框架 | **Gin** | 社区最大、文档和示例最多；路由分组、中间件、参数绑定 + `go-playground/validator` 校验开箱即用，读起来接近 Spring MVC 的 `@PostMapping` + `@Valid` | chi（够用但绑定校验要自拼）；Echo（同类，生态略小）；Fiber（基于 fasthttp，不兼容标准 net/http 中间件）；Kratos/go-zero（Proto 优先、代码生成重，与现有 OpenAPI 合同 + Java `@HttpExchange` Client 的方式冲突） |
| 数据访问 | **sqlc** + `go-sql-driver/mysql` | SQL 写在独立 `.sql` 文件，生成类型安全的 Go 方法，和项目 MyBatis XML “SQL 与代码分离”的约定一致；无 ORM 隐式行为，便于安全审计 | GORM/ent（ORM 隐式查询与关联，审计困难，和 Java 侧不用 JPA 的取向不一致）；sqlx（仍需手写 SQL 字符串） |
| 事务 | 标准库 `database/sql` 事务 + 自有 `TxTemplate` 帮助函数 | 对应 Java `TransactionTemplate`：显式边界、事务内只操作数据库 | 隐式/声明式事务 |
| 配置 | 环境变量 → 结构体（`caarlos0/env`）+ `validator` 校验 | 对应 `@ConfigurationProperties` + 校验；启动时缺失即失败 | Viper（功能多、来源隐式） |
| 依赖组装 | `main.go` 中手工构造注入 | Go 惯用、显式、无反射；组件数量可控 | google/wire（组件多时再引入） |
| 日志 | 标准库 `log/slog`（JSON） | 结构化、零依赖；沿用“禁止 Token/明文/完整 Payload 进日志”的规则 | zap/logrus（无必要） |
| 观测 | Prometheus client + OpenTelemetry（沿用） | 已在用 | — |
| 注册发现 | `nacos-sdk-go/v2`（沿用） | 已实现注册，Java 端经 Nacos + LoadBalancer 发现 | — |
| 错误响应 | RFC 9457-based Code-MoR Problem（沿用合同） | 与全系统 Problem 规范一致，Java Client 解码不变 | — |
| API 合同 | OpenAPI（沿用）+ Java `paytech-vault-client` 的 `@HttpExchange` | 合同不因框架迁移改变 | — |
| 测试 | 标准库 `testing` + `testify`（assert/require）+ `httptest` | 对应 JUnit + AssertJ；入口测试经真实 HTTP 路由 | gomock 等行为 Stub（与项目测试规范冲突） |
| 质量工具 | `gofmt`/`goimports` + `golangci-lint`（固定规则集） | 对应格式化与静态检查 | — |

依赖版本在实施阶段以 `go list -m` 核对的稳定版本锁定到 `go.mod`，本稿不预写版本号。

## 2. 目录结构（对照 Java 分层）

```text
vault-service/
├── cmd/vault-server/main.go        # 启动：读配置 → 组装依赖 → 注册路由 → 启动 HTTP、注册 Nacos、优雅停机
├── api/openapi/                    # 对外合同（沿用）
├── internal/
│   ├── api/                        # HTTP DTO：*RequestBody / *ResponseBody（字段校验 tag），对应 *-api 模块
│   ├── ext/rest/                   # Gin 路由与 Handler（对应 Controller）：只做绑定、校验、转换、委托
│   │   ├── router.go               # 路由分组：/vault/v1/records、/vault/v1/admin、/health、/metrics
│   │   ├── record_handler.go
│   │   └── middleware/             # Request ID、调用方识别、访问审计、Problem 错误映射
│   ├── service/processor/          # 用例编排（对应 Processor）：校验 → 查询 → 本地事务 → 事务外步骤 → 响应
│   ├── domain/                     # 领域类型、Context、业务枚举
│   ├── domainservice/              # 领域服务（对应 Domain Service）：策略判定、加解密编排
│   ├── repository/                 # Repository 接口与实现（一表一个），只委托 sqlc 生成的查询
│   ├── dal/                        # sqlc：queries/*.sql（对应 Mapper XML）+ 生成代码（不手改）
│   ├── integration/                # 外部依赖适配：kms、nacos
│   ├── config/                     # 配置结构体与校验（对应 *Properties）
│   └── core/                       # 错误码、事务帮助函数、观测等公共机制
├── docs/database/sql/              # 手工执行的 Schema（沿用，sqlc 读取它生成类型）
└── paytech-vault-client/           # Java 客户端（沿用）
```

## 3. 代码规范（参考 Java 规范，按 Go 惯用法落地）

| Java 规则 | Go 落地 |
|---|---|
| Controller 只做协议适配 | Handler 只调用 `ShouldBindJSON` + 校验、转换为内部 Request、调用 Processor、写响应；不查库、不开事务 |
| 一个写接口一个 Processor | 每个用例一个 `XxxProcessor` 结构体，方法 `Process(ctx, req) (resp, error)`；主方法按阶段平铺，卫语句提前返回 |
| Service/Repository 用 interface + Impl | **接口由使用方定义**（Go 惯例），实现用小写具体类型，例如 `type RecordRepository interface{...}` + `type mysqlRecordRepository struct{...}`；不使用 `Impl` 后缀 |
| SQL 在 Mapper XML | SQL 只写在 `internal/dal/queries/*.sql`；业务代码不拼 SQL 字符串 |
| 事务用 TransactionTemplate，事务内不调外部服务 | `core.TxTemplate.Execute(ctx, func(tx) error)`；KMS、Nacos 等外部调用必须在事务外 |
| 公共异常 + 错误码 | 业务错误用领域错误类型携带稳定 Code（大写英文语义），由中间件统一映射为 Problem；不在 Handler 里逐个写状态码 |
| 枚举实现 CodeEnum | `type TokenMode string` + 常量；对外/持久化只用 Code 字符串，解析未知值即失败 |
| 方法不超过 30 行、嵌套不超过 3 层 | 同样适用；错误逐层 `return fmt.Errorf("...: %w", err)` 保留原因 |
| 中文 JavaDoc | 导出标识符写 Go doc 注释（以名称开头的句子），说明用中文 |
| 每个手写方法有注释 | 导出函数和非显而易见的内部函数必须有注释 |
| 日志不含敏感值 | slog 只记 Request ID、调用方、动作、数据类型、结果码与耗时；禁止记录 Token、明文、Payload |
| 测试从入口发起，不用行为 Stub | `httptest` 经 Gin 路由调用真实 Processor → Repository → 本机 MySQL；KMS、Nacos 用边界替身；测试数据每例清理 |
| 配置集中在 Properties | 配置只在 `internal/config` 定义与校验，业务代码不直接读环境变量 |

命名：包名小写单词（`processor`、`repository`），文件名蛇形（`record_handler.go`），导出类型大驼峰，接收者名一到两个字母。

## 4. 对 Vault 的影响与迁移建议

- **合同不变**：HTTP 路径、请求/响应 JSON、Problem、Header（`X-Caller-Service`、`Idempotency-Key`、`X-Request-Id`）、Schema 与 Java Client 均不变；只改内部组织。
- **迁移方式**：在现有测试（17 个测试文件、113 个测试函数）保护下逐层替换：① 引入 Gin 路由与中间件替换 `switch` 分发；② 引入 sqlc 替换手写 SQL；③ 按第 2 节调整目录；④ 加 `golangci-lint`。每步保持全部测试通过。
- **新增维护接口**：在 `/vault/v1/admin` 路由组下按同一结构增加，访问控制与审计在中间件统一处理（具体接口另行设计）。
- **前提**：本机需安装 Go 工具链（当前未安装）；Vault 代码不属于本会话负责的系统，迁移需你确认由哪个会话执行。

## 5. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| G1 | HTTP 框架 | Gin |
| G2 | 数据访问 | sqlc + database/sql，不用 ORM |
| G3 | 依赖组装 | `main.go` 手工注入，组件多了再评估 wire |
| G4 | 接口与实现命名 | 采用 Go 惯例（使用方定义接口、实现不加 Impl），其余分层与 Java 对齐 |
| G5 | 落地方式 | 确认后更新 TECHNOLOGY_STACK.md 并新增 `agent-docs/GO_DEVELOPMENT_CONVENTIONS.md`；Vault 迁移单独立项，分 4 步交付 |

## 6. 实施结果（2026-10-14）

- 已落地：Gin 接入层、`internal/api` DTO + binding 校验、Processor 拆分（Protect/Reveal）、领域服务（审计、加解密）、按表仓储端口 + sqlc 查询、golangci-lint 规则集；`scripts/verify.sh` 增加 `sqlc diff` 与 lint 门禁。规范写入 [GO_DEVELOPMENT_CONVENTIONS.md](../../../agent-docs/GO_DEVELOPMENT_CONVENTIONS.md)，选型写入 [TECHNOLOGY_STACK.md §7.1](../../../agent-docs/TECHNOLOGY_STACK.md#71-go-服务技术栈)。
- 与评审稿差异：
  - 配置未改用 `caarlos0/env`：现有加载器把 DSN、根密钥、Nacos 密码放在私有字段并在格式化时脱敏，`caarlos0/env` 只能填充导出字段，会削弱该保护；保留显式读取 + 启动校验。
  - 请求体不用 `ShouldBindJSON`：Vault 合同要求拒绝重复键、大小写别名与孤立代理，先严格解析再用 Gin 校验器按 `binding` 标签校验。
  - `internal/core` 未建：错误、格式规则、清零放在 `domain`，事务在仓储实现内。
- Nacos：沿用官方 `nacos-sdk-go/v2`（只做注册）。Kratos、go-zero 的 Nacos 组件都是对同一官方 SDK 的包装，且依赖各自框架的注册抽象；Gin 本身没有注册中心集成。配置中心未接入：Vault 运行参数多为密钥或启动期参数，Allowlist 放进 Nacos 会把授权边界交给 Nacos 写权限。
- 已知遗留：`ProtectProcessor` 在事务内调用 LOCAL AES 加密（进程内计算，沿用原实现），已标 `TODO by CLAUDE`，接入远程 KMS 前须移出事务。
