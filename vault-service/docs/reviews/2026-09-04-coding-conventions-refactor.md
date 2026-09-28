# Vault 编码准则重构记录

日期：2026-09-04。基线：`c759551`。本次为行为保持重构，不增加功能或改变已批准的实现边界。

## 依据与范围

依据根仓 `agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、
`UNIT_TEST_GUIDE.md` 和 `TECHNOLOGY_STACK.md`，保留已批准 Go Server + Java Client 的专项例外：
Go 使用原有端口/适配器结构，Java 仅发布 Client，不新增 Java Server、空层或公共基础模块。

- Go 配置加载分离基础读取和校验；地址校验改为卫语句，读取顺序、默认值和安全错误不变。
- Policy 编译分离 Data Type 注册与 Rule/Purpose 展开；精确匹配、重复拒绝和严格 JSON 解析不变。
- Java 自动配置分离调用者解析、传输 Builder 与拦截器装配；目标模式、超时和拦截器顺序不变。
- 三个普通类及两个枚举的纯赋值构造器使用 Lombok；含参数校验的 Record 构造器继续显式保留。
  Lombok 为 optional 依赖，版本由现有 BOM 管理；JDK 25 显式处理器只配置在 Client 模块。
- 公共接口、不可变 DTO 和配置补齐中文契约说明，明确精确原值、全局 STABLE、命令幂等和禁止日志边界。

公开签名、Code、DTO 不变性、固定脱敏字符串及 Wire 契约不变。三表模型/DDL、密码学、事务恢复、
访问审计顺序、Nacos 生命周期和遥测实现均未修改；不恢复底层敏感异常 Cause。

## 验证结果

- Java Client 重构前后离线 `clean verify`：23 项测试通过；`javap` 核对构造器签名保持不变。
- Go 配置/策略：原有基线通过；新增边界测试在旧实现下通过；重构后 test、race、vet 均通过。
- 完整 `scripts/verify.sh`：MySQL Schema、OpenAPI/Golden、gofmt、Go vet/test/race/build、
  Maven clean verify、敏感 Canary 和 diff 检查全部通过；Java 合计 32 项（23 Client + 9 跨语言验收）。
- 根仓需求站构建及 123 项测试通过；没有修改根仓文档或其他任务的未提交内容。
- 独立只读复核未发现本次差异引入的回归。编译存在 Lombok 使用 JDK 弃用 Unsafe API 的警告，未屏蔽。

仅使用已准备的本地 MySQL 和测试自有数据，不执行 DDL、不安装或发布制品、不创建分支或提交。
真实 Nacos 手工 Smoke 与 PDManer 应用内评审仍未执行；云 KMS、生产部署和业务服务接入继续延期。
