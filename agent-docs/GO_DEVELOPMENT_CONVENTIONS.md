# Go 开发规范

最后更新：2026-10-14

状态：Confirmed。适用于 Vault Server 及今后新增的 Go 服务。技术选型与版本只在 [TECHNOLOGY_STACK.md §7.1](./TECHNOLOGY_STACK.md#71-go-服务技术栈) 维护。

- **目录与分层**采用 [Kratos 工程布局](https://go-kratos.dev/docs/intro/layout)（`server → service → biz ← data`，DDD 简化版），只借用目录约定，不引入 Kratos 框架。
- **编码风格**以 [Uber Go Style Guide](https://github.com/uber-go/guide/blob/master/style.md) 为基线，并遵守官方 [Effective Go](https://go.dev/doc/effective_go) 与 [Go Code Review Comments](https://go.dev/wiki/CodeReviewComments)；本文只记录项目补充与差异。
- [Java 开发规范](./DEVELOPMENT_CONVENTIONS.md) 的事务、错误和测试原则同样适用；分层与命名按本文。

## 1. 目录与分层

```text
<service>/
├── cmd/<server>/main.go       # 进程入口：读配置 → bootstrap 组装 → 监听 → 优雅停机
├── api/
│   ├── openapi/               # 对外合同
│   └── <service>/v1/          # 请求/响应体（≈ *-api 模块），binding 校验标签
├── internal/
│   ├── server/                # HTTP 服务器：路由登记、中间件、公共头、Trace/指标、Problem 错误出口
│   ├── service/               # 接口实现：解析与校验请求、转换为 biz 输入、调用用例、写响应
│   ├── biz/                   # 业务核心：用例、领域能力、领域对象与错误码、仓储/外部端口接口
│   ├── data/                  # 数据访问：仓储实现、事务、错误归类；data/sqlc 为查询与生成代码
│   ├── conf/                  # 配置读取与校验
│   ├── bootstrap/             # 组合根：按 data → biz → service → server 组装，cmd 与自动测试共用
│   └── pkg/                   # 技术组件：密钥实现、注册中心、ID、观测、策略加载等，可被多层使用但不含业务流程
├── test/                      # 独立自动测试（≈ Java <service>-test 模块），见第 8 节
├── docs/database/sql/         # 手工执行的 Schema，sqlc 读取它生成类型
└── sqlc.yaml / .golangci.yml
```

依赖方向：`server → service → biz ← data`，`biz` 不依赖任何其他业务层、HTTP、数据库驱动或框架；`data` 与 `pkg/*` 实现 `biz` 定义的端口。读一个接口按 `server`（路由）→ `service`（入参出参）→ `biz`（业务规则）→ `data`（落库）顺序即可。

| Kratos 层 | Java 对应 | Vault 示例 |
|---|---|---|
| `server` | WebMvcConfigurer、Filter、`@ControllerAdvice` | `http.go` 路由、`business_route.go` 统一包装、`problem.go` |
| `service` | `@RestController` | `RecordService.Protect/Reveal` |
| `biz` 用例 | Processor | `ProtectUsecase`、`RevealUsecase` |
| `biz` 领域能力/对象 | Domain Service、domain、Repository 接口 | `AccessAuditService`、`RecordCipherService`、`Record`、`Store/TxStore` |
| `data` | RepositoryImpl + Mapper | `Store.InTransaction`、`txRepository`、`data/sqlc` |

## 2. 各层职责

| 层 | 职责 | 不做 |
|---|---|---|
| `server` | 登记路由、中间件；公共头校验、Trace、指标；把 `service` 返回的错误统一映射为 Problem | 解析业务请求体、业务判定 |
| `service` | 严格解析请求体、按标签校验、转换为 biz 输入、调用用例、写成功响应 | 查库、开事务、业务规则 |
| `biz` | 用例主方法 `Process(ctx, attempt, input)` 按“校验 → 策略 → 事务外外部调用 → 本地事务 → 事务后步骤”平铺，卫语句提前返回；跨用例能力放领域服务 | 访问驱动、HTTP 或框架类型 |
| `data` | 委托 sqlc 查询、类型转换、事务、错误归类 | 业务规则、授权、审计判定 |

## 3. 命名与接口

- 包名为单个小写词（`server`、`service`、`biz`、`data`）；文件按领域对象或用例命名（`protect.go`、`record.go`），不按类型后缀命名；导出标识符大驼峰；接收者一到两个字母。
- 用例 `XxxUsecase`（≈ Processor），领域服务 `XxxService`，接口实现 `XxxService`（`service` 包），请求/响应体 `XxxRequestBody` / `XxxResponseBody`。
- **与 Java 的差异：** 接口由使用方定义（`service` 定义它需要的用例接口，`biz` 定义仓储与密钥端口）；实现类型不加 `Impl` 后缀。
- 枚举用 `type X string` + 常量；对外和持久化只用 Code 字符串，解析未知值即失败。
- 同一层的代码放同一个包，不为“分层对称”拆出只有一两个文件的子包。

## 4. 数据访问与事务

- SQL 只写在 `internal/data/sqlc/queries/*.sql`，`-- name: Xxx :one|:many|:exec` 命名与业务含义一致；加锁读取在方法名体现（`ForUpdate`、`ForShare`）。修改后执行 `sqlc generate`，`sqlc diff` 必须干净。
- 无法由 sqlc 表达的只读查询（如启动时确认表已建好的 information_schema 查询）可保留手写 SQL，集中在 `data` 包并注明原因；启动只检查表存在，不逐列核对结构。
- 事务由仓储实现提供 `InTransaction(ctx, func(tx) error)`：回调返回错误即回滚，panic 也回滚；提交或回滚结果无法确认时返回专用哨兵错误，调用方不得重试或重复审计。
- 事务内只操作数据库，不调用密钥服务、注册中心等任何依赖；需要的加密、HMAC 在事务前完成（Vault 由 `TestKeyServiceIsNeverCalledInsideTransaction` 守护）。

## 5. 错误与日志

- 领域错误携带稳定大写 Code，`Error()` 只返回本地安全说明；任意错误经 `biz.Sanitize` 收敛后才能离开领域层。
- 驱动、SDK 原始错误不得进入响应或日志；在仓储/适配层归类。
- 包装错误用 `fmt.Errorf("...: %w", err)`，判断用 `errors.Is/As`，不用 `==` 比较错误。
- 被忽略的返回值必须显式写 `_ =` 并说明原因（如清理阶段尽力而为）。
- 日志只记录 Request ID、调用方、动作、数据类型、结果码与耗时。

## 6. 注释

- 导出标识符写 Go doc 注释（以名称开头），说明用中文；非显而易见的内部函数同样要写。
- 安全或并发上的取舍写在代码旁，说明“为什么”，不复述代码。

## 7. 代码形态

- 函数目标不超过 30 行，lint 上限 40 行；嵌套不超过 3 层；认知复杂度不超过 20。
- 优先卫语句与 `switch`；长流程拆为按阶段命名的小函数。
- 敏感字节由持有者负责 `biz.Wipe`/`clear`，失败路径同样清零。

## 8. 测试

### 8.1 独立测试目录（对应 Java `<service>-test`）

业务入口测试放在服务根的 `test/`，与生产代码分离；生产包不得导入 `test/`。

```text
test/
├── auto/                     # 默认执行（go test ./...）
│   ├── contract/             # OpenAPI 合同与 Wire 金样
│   ├── biz/                  # 用例与领域规则（≈ Java auto/domain）：经 biz 公开 API 调用
│   ├── conf/  pkg/<组件>/    # 配置与技术组件的公开 API 测试
│   └── ext/http/             # HTTP 入口测试，一个入口一个文件；main_test.go 中 TestMain 调 base.Run
├── manual/                   # 显式运行的真实环境联调（有需要时再建）
└── support/                  # 测试辅助（对应 Java test 包）
    ├── base/                 # Run：按 bootstrap.Assemble 启动一次服务；Setup：每例全局准备与清理；完整测试配置清单
    ├── builder/              # 请求 Builder：template + 具名覆盖，负例可表达缺失/null/原始 JSON
    ├── mocker/local/         # 本地数据库前置数据（直接插入，不调用被测命令）
    ├── checker/              # 响应、数据库、日志断言（只读）
    ├── dal/                  # 测试专用 SQL（TestMapper）：Checker 读取与清理
    └── fixture/              # 本次运行的隔离标识与策略
```

- **生命周期：** `TestMain` 相当于测试应用上下文，只启动一次；每个用例开头调用 `base.Setup(t)`（全局 BeforeEach：检查环境可复用与空基线），它用 `t.Cleanup` 登记全局清理（AfterEach），断言失败也会执行。文件内共用准备写成 `setUpXxx(t)`（类级 BeforeEach），在 `base.Setup` 之后调用。
- **装配：** 测试经 `bootstrap.Assemble` 使用正式装配，不另建平行装配；测试配置在 `support/base` 中列出全部配置键（对应 `application-mock.yml`）。
- **隔离：** 共享本机库时，每次运行生成独占的调用方、数据类型、请求 ID 前缀，清理只删这些数据；Snowflake 坐标按测试包登记，互不重叠。
- **用例：** 一个用例一个场景；同结构的多组输入用表驱动子测试。文件头注释写明被测入口（路径与 Processor 方法）。断言响应、数据库事实与日志，不以“没有报错”代替。

### 8.2 测试放置规则

`_test.go` 只在 `go test` 时编译，不进入生产二进制；Go 的包即目录，放到 `test/` 下的测试只能访问导出标识符。据此：

- **只经公开 API 的测试一律放 `test/auto/`**（`package xxx_test` 或独立包），包括入口、用例、配置和组件公开行为测试。
- **必须访问未导出实现的白盒测试留在被测包旁**（`package xxx`），仅限无法从公开 API 构造的不变量：事务提交结果未知/回滚失败、驱动错误归类、严格 JSON 边界、时钟回拨、注册超时、启动/停机清理等；可用确定性替身或故障注入，不得替代入口测试。
- 不为把测试搬进 `test/` 而导出内部函数或增加仅供测试的访问方法。

### 8.3 通用要求

- 不使用 gomock 等行为 Stub 替换生产链路；外部边界（KMS、Nacos SDK、时钟、随机源）可用确定性替身。
- 依赖缺失时以准备说明失败，不跳过。
- 门禁：`gofmt`、`sqlc diff`、`golangci-lint run`（0 issues）、`go vet`、`go test`、`go test -race`。
