# 全球合规入驻切片 1 · 基础设施 harness

| 文件 | 用途 |
|---|---|
| `screening_mock_server.py` | 合成筛查服务（标准库）。`GET /health`；`POST /v1/screening/search`：fullName 含 `TESTHIT` → PEP 命中，含 `TESTSANCTION` → SANCTIONS 命中，否则空；`TESTFAIL` → 渠道 FAIL（`hits:[]` + `errorCode`），`TESTERROR` → HTTP 500（结果未知），`TESTSLOW` → 延迟 `--slow-ms`。流水不记姓名原值 |
| `start_screening_mock.sh` | 本机启动 / 停止（默认 127.0.0.1:18191，运行目录 `<主仓库>/local-environment/e2e/runtime/screening-mock/`） |
| `Dockerfile.screening-mock` | 独立镜像（备选）；mor-test 首选 `test-environment/compose.screening-mock.yaml`（固定摘要 python 镜像只读挂载，无需构建） |
| `screening_channel_setup.py` | 经 channel-connector 管理接口登记 `SCREENING_SEARCH`、`S_SCREENING_MOCK`、原子 API、实现、`compliance-service` 授权；可重复执行，差异按版本改写；`--smoke` 冒烟 6 例 |
| `block_compile_check.sh` + `BlockSnapshotCheck.java` | Platform 注册块发布前门禁：断网 MySQL 导入备份，逐步执行块 SQL 并用候选 Platform JAR 的生产编译器编译 Gateway 快照 |

## 本机

```bash
H=requirements/20261027/client-master-全球合规入驻与资格切片1/assets/harness
$H/start_screening_mock.sh start            # 127.0.0.1:18191
python3 -I $H/screening_channel_setup.py --base-url http://127.0.0.1:18110 --mock-url http://127.0.0.1:18191 \
  --actor-id 236033080844881920 --smoke --out <结果.json>
```

本机 channel-connector 已带 `ALLOWEDSCHEMES=https,http`、`TRUSTEDNETWORKS=127.0.0.1/32`；actor 为本机 e2e 运营管理员（与既有标准 API 的 created_by 相同）。

## mor-test（主会话执行，均为环境变更）

1. `./test-environment/compose.sh -f "$PWD/test-environment/compose.screening-mock.yaml" --profile acceptance up -d --no-deps --pull never --wait screening-mock`
2. channel-connector 按 `release/compose-candidate.channel-connector.draft.yaml` 加两项放行（只放行 172.30.73.43/32）后重建。
3. 在 backend 网络内跑登记脚本（不经 Gateway；mor-test channel-connector internal-auth 为默认 OFF）：

```bash
docker run --rm --network mor-test-backend --user 10001:10001 -v "$PWD/$H:/h:ro" \
  python:3.13-alpine@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a \
  python -I /h/screening_channel_setup.py --base-url http://channel-connector-service:18110 \
  --mock-url http://172.30.73.43:8080 --actor-id 221546852372267008 --smoke
```

`221546852372267008` 是 mor-test 既有 9 个标准 API 的 created_by（运营管理员），执行前可改为当次操作的运营账号 ID。结果文件写到可写目录（加 `-v <目录>:/out` 与 `--out /out/…`）。

## 调用合同要点（给 compliance，按 cmp-dev 2026-10-27 合同对齐）

- 通用入口 `POST /channel-connector/v1/service-executions`，`X-Caller-Service: compliance-service`，体 `{"channelCode":"S_SCREENING_MOCK","standardApiCode":"SCREENING_SEARCH","input":{…}}`。
- input：`subjectType`（PERSON / ORGANIZATION）、`fullName`、`birthYear`（**字符串 "YYYY"**，可省略；传数字 → 422 `input.birthYear 必须是字符串`）、`countries`（可空数组或省略）、`screeningTypes`（数组）、`clientRef`。未声明字段被拒绝。
- SUCCESS outputs：`runRef`（可选）、`hits[]`：`externalRef`、`entityType`、`listTypes[]`、`score`、`matchTypes[]`（必填），`riskLevel`、`matchName`（可选）。
- FAIL outputs：`{"hits":[],"errorCode":…}`；ERROR：`{}`（结果未知，不自动重试）。
- **`score` 以 JSON 字符串返回**（如 `"92.0"`，0–100）。Mock 发送带小数点的数值；整数分值（如 `92`）经 DECIMAL 校验的行为未验证，接真实服务前需确认。
- Mock：fullName 含 `TESTHIT` → PEP 命中（MEDIUM），含 `TESTSANCTION` → SANCTIONS 命中（HIGH）；externalRef 对同一 clientRef + fullName 稳定；`runRef` 由 clientRef 推导。
