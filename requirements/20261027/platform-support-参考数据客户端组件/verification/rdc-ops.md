# operations-console-bff 改读参考数据币种接口（S-07 / F-08）验证记录

日期：2026-10-27　工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-refclient`（未提交）

## 1. 改动

财务页币种下拉 `GET /finance/currencies/v1` 的数据源由旧接口 `CurrencySpecQueryApi`（`GET /platform/v1/currency-specs`）改为 `ReferenceCurrencyQueryApi.listCurrencies(status, null)`（`GET /platform/v1/reference-data/currencies`）。BFF 仍是转发，不引入 starter；`CurrencySpecManagementApi` 未动。

| 文件（`paytech/operations-console-bff/` 下） | 改动 |
|---|---|
| `…-app/…/integration/impl/PlatformCurrencySpecClientImpl.java` | 删除 `CurrencySpecQueryApi` 依赖；`listCurrencies(status)` 改调 `referenceApi.listCurrencies(status, null)` |
| `…-app/…/integration/impl/CurrencySpecContractConverter.java` | `toFinance` 改为接收 `ReferenceCurrencyListResponseBody`：逐条核对币种代码、小数位 0–8、舍入方式、状态、版本 > 0，币种不得重复；指定 status 时每条都须为该状态，否则 502；只投影 `currency/minorUnit/roundingMode/status`。删除只服务旧合同的 `updateTime` 解析 |
| `…-app/…/configuration/PlatformClientConfiguration.java` | 删除 `CurrencySpecQueryApi` Bean 与 import |
| `…-app/…/integration/PlatformCurrencySpecClient.java`、`…/ext/rest/FinanceSetupController.java` | 注释改为"由 Platform 按状态筛选" |
| `…-test/…/auto/CurrencySpecBrowserAutoTest.java` | 下拉用例改 Mock 新路径；新增下拉上游无效响应用例 |
| `…-test/…/auto/OperationsHttpBodyNamingAutoTest.java` | 命名约束清单中旧 API 换为 `ReferenceCurrencyQueryApi` |

`grep CurrencySpecQueryApi\|/platform/v1/currency-specs`（main + test 源码与资源）：0 处。

## 2. 响应等价性

| 维度 | 旧 | 新 | 结论 |
|---|---|---|---|
| 字段 | `currency, minorUnit, roundingMode, status`，顺序固定 | 同（`ReferenceCurrency.currencyCode` 投影为 `currency`） | 等价，用例逐条断言字段名与顺序 |
| status 为空 | 全部状态 | 不带 `status` 参数，Platform 返回全部状态 | 等价（用例断言 Platform 收到的 query 为 null） |
| status 指定 | BFF 筛选 | Platform 筛选；BFF 核对每条状态一致 | 等价（用例断言 query 为 `status=X`，结果只含对应币种） |
| 排序 | 旧接口按币种代码升序，BFF 保持顺序 | 新接口合同同样按币种代码升序，BFF 保持顺序 | 等价 |
| 非法 status | Controller `@Pattern(DRAFT\|ACTIVE\|INACTIVE)` 在访问 Platform 前 400 | 不变 | 等价（`test_financeCurrenciesInvalidStatus`） |
| 上游无效 | 字段不合法 / 重复 → 502 | 同；新增"返回状态与筛选不符 → 502"；新接口无 `updateTime`，不再校验 | 页面侧 502 语义不变 |

## 3. 命令与结果

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
# 加载 local-environment/.env.local（未打印）
cd /Users/kenny/Desktop/Workspace/Code-MoR-refclient/paytech

./mvnw -o -q -pl operations-console-bff/paytech-operations-console-bff-test -am \
  -Dtest='CurrencySpecBrowserAutoTest,OperationsConsoleBffApplicationAutoTest,OperationsHttpBodyNamingAutoTest' \
  -Dsurefire.failIfNoSpecifiedTests=false verify
# CurrencySpecBrowserAutoTest 27/0/0、OperationsConsoleBffApplicationAutoTest 8/0/0、OperationsHttpBodyNamingAutoTest 53/0/0

./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am \
  -Dtest='com.paytech.operations.console.bff.**' -Dsurefire.failIfNoSpecifiedTests=false verify
# Tests run: 2424, Failures: 0, Errors: 0, Skipped: 0；BUILD SUCCESS（199 秒）
```

Maven 仓库为 `~/.m3-refclient`（`.mvn/maven.config`）；只运行了 operations-console-bff 测试，未跑全 reactor 测试。
