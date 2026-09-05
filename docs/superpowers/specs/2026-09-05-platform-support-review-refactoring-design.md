# Platform Support 审查问题修复与重构

Decision status: Confirmed by the user in this task on 2026-09-05.
Implementation, verification and deployment: See [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md).

> 同日后继：[Controller 尽力审计决策](../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)
> 仅取代本文对既有 Operations HTTP 审计事务/Processor 失败审计编排保持不变的约束；
> 其余业务事务、锁、Service/Owner 边界及本次修复结论继续有效。新审计设计不等于已实现。

## 范围与选择

落实本任务已审查并获用户批准的问题：测试状态污染、变量模板重叠、管理员误保护、登录失败原因丢失、Service 接口形态、Directory 职责集中、人员分页逐行查询、脆弱源码字符串测试及 Lombok 遗漏。

采用现有 `api / web / test` 内的能力拆分。相比只给巨型类加接口，拆分能让授权、人员、角色和审计查询各有明确职责；当前没有必要增加 Maven 模块、部署单元或数据库。HTTP Method、Path、JSON、公共错误和锁/事务语义保持既有契约，明确纠正上述错误行为。

## 行为修复

- 同 HTTP Method、同 Segment 数的任意两个含变量模板，只要存在匹配交集就拒绝全份 Snapshot；完全固定 Path 仍具有获批的优先权。Platform 与 Gateway 同步修正。
- 仅当目标账号本身贡献有效管理员计数、且此次变更将移除其资格时，执行最后管理员保护。删除、停用和角色分配共用资格判定，保留现有数据库集合保护和 CAS。
- Login 外部仍返回通用失败；内部审计记录明确的未知账号、停用、锁定、凭据不可用、密码错误、成员/Tenant 不可用原因，不记录输入的账号标识或密码。

## 代码结构

- 所有现有业务 Service 使用同名接口和 `impl/*Impl`。保持必要的领域上下文类型稳定，调用方只依赖接口。
- 拆分 Directory 的授权/导航、角色管理、人员管理和审计查询能力。Processor 保留完整事务、资源锁、失败审计编排；领域服务承载规则。
- 人员页通过列表摘要投影和批量角色查询获取展示事实，不逐行读取 Password 校验材料。
- 同一 Owner JAR 内没有业务规则的 DataObject 镜像可去除；真实聚合、脱敏投影和 HTTP 边界必须保留。
- 纯字段赋值构造器使用 `@AllArgsConstructor`，带参数校验、密钥解析、事务模板初始化的构造器保留。Repository 实现按 `*RepositoryImpl` 命名。

## 测试与运行边界

- 测试只操作本次运行拥有的数据；每次运行独立 marker/ID。默认测试不执行历史残留清理。
- Snapshot/Health 故障注入使用精确拥有的临时目录行；需要全局一致性观察的本地数据库测试使用跨进程独占运行保护，失败时清晰报出竞争，不删除其他运行状态。
- 保留生产 Mapper XML 和 BoundSql 门禁；锁顺序/冲突/回滚用真实 HTTP/MySQL/Redis 行为测试验证，删除私有方法名称和异常全文字符串断言。
- 不启动容器、不迁移或 seed Schema，不连接 `mor-test`。默认通过环境注入凭据；本任务后续获用户明确许可，仅为 local 测试读取 `.env.local` 并在内存注入，不输出或写入凭据。默认自动测试使用既有 Loopback MySQL/Redis。
- 行为缺陷先增加失败回归；机械接口/命名/构造器调整使用编译和现有行为测试验证，不机械增加 getter/构造器测试。

## 验收

修复回归、架构门禁、Platform `clean verify`、Gateway 对应路由/编译器门禁、需求站点测试通过；核对测试前后目录及本次 fixture/Redis 残留。最后执行独立代码评审。实现和验证结果写入组件 README 与唯一状态台账，不宣称部署。
