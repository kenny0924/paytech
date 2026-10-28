# rel-dev 报告：阶段 6 构建（2026-10-27 20:05—20:35Z）

> 只在本机构建与核对；对 mor-test 只做 `docker inspect`、`docker cp` 拷出 JAR、`docker logs`、只读 HTTP（Nacos 查询、经只读 sidecar 读 `/actuator/health*`）。没有停容器、换镜像、写库、改 Gateway / Nacos，没有把镜像推送或加载到别处（mor-test 与本机共用同一 Docker，无需加载）。未提交。

## 1. 构建源与指纹

| 工作树 | 内容 |
|---|---|
| `/Users/kenny/Desktop/Workspace/Code-MoR-refclient-base` | `git worktree add --detach … 5e1f16ab`，无改动（基线） |
| `/Users/kenny/Desktop/Workspace/Code-MoR-refclient-rel` | `5e1f16ab` + `Code-MoR-refclient` 全部未提交改动（按 `git status --porcelain -z --untracked-files=all` 复制 153、删除 49） |

指纹（全部非忽略文件 sha1 清单再 sha1）：

| 时间（UTC） | `Code-MoR-refclient` | `Code-MoR-refclient-rel` | `git status --short` md5 |
|---|---|---|---|
| 20:11:32（构建前） | `a2ea2734e76893e9` | `a2ea2734e76893e9` | `9d91dfc2f84ecdba1a0708a6eebacd1b`（= 阶段 5 构建时） |
| 20:31:17（构建后） | `a2ea2734e76893e9` | `a2ea2734e76893e9` | `9d91dfc2f84ecdba1a0708a6eebacd1b` |

```bash
fp(){ (cd "$1"; git ls-files --cached --others --exclude-standard -z | xargs -0 shasum 2>/dev/null | sort -k2 | shasum | cut -c1-16); }
fp /Users/kenny/Desktop/Workspace/Code-MoR-refclient; fp /Users/kenny/Desktop/Workspace/Code-MoR-refclient-rel
```

补丁：`refclient-on-5e1f16ab.patch`（`scripts/export_patch.sh`，临时 index，不碰共享 index；201 个文件）——应用到干净 `5e1f16ab` 后与发布快照逐文件相同（0 不符），供 card-BIN / 法务修订提交后重建。

## 2. 构建命令与制品

```bash
release/scripts/build_apps.sh <工作树>      # JDK 25；./mvnw -B -o -q -Dmaven.repo.local=~/.m3-refclient/repository \
                                            #   -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ -DskipTests -pl <6 个 app> -am clean package
```

两次（基线、发布）均成功。镜像：`test-environment/java/Dockerfile` + `--build-arg JAR_FILE=<服务>.jar`，上下文含 `javac --release 25` 编译的 `health-probe/`（与现网镜像同一套）；tag `refclient-20261028a`。用 `docker create` + `docker cp` 取出镜像内 `/app/application.jar` 与构建产物比较，6 个均逐字节相同。

| 服务 | 镜像 sha256 | JAR sha256 | 可用性 |
|---|---|---|---|
| ledger-service | `sha256:1ffc86a13932cd6b2d440a6038ae140f9b5fd279714306c32725641163e7e3fd` | `4c110fd648b086e11b1e644c1ce939d8a5923bf808651e519eb0091be7f1075d` | 可用 |
| billing-service | `sha256:9edb6cea358eb38eaebd2fc798bc139ba31f40d113b5e195fa108e1d6ae477e4` | `c8c1603400b205ea25b628a599c6636866d820eb26129363fdbbac7e6db7ddce` | 可用 |
| card-service | `sha256:33ab40e68bca3971305914b03f83e72708f5914fb0d995fdeed2f9a92b98328d` | `15f94f0c418939f3bd3ad64bbd29b0d774d94ac49eaad5074a48631e15bfa08b` | **不可用**：会回退现网 card-BIN binlist-20261027a，须重建 |
| client-master-service | `sha256:576d5422eebd08b7ca00b13e6ae4fd1dabd2aa68295ffb9b1796726d93b2ca7b` | `e17e68485283b583c4bbb26c9fcd6ac2ad6ec834c6c69a66eda8bdc0ab978e6a` | 现网 = 5e1f16ab，可用；若法务修订先发布则须重建 |
| operations-console-bff | `sha256:6eb795cfd475026a0a9c70e9df1a47111251f90a9cdf3548a7d8f3d4fc4f7b5f` | `4a6e1293c614e15829ee6064c554556b013379d89d4c66dc458a8fe6efe9741f` | **不可用**：同 card |
| platform-support-service | `sha256:62a1701a0fd63aceaa85406add7b5cf89c6ac8850d358d886cd7e466442a3e13` | `35f7c42367977f1d5ed89c017c173f0f61bb45e5b5dd2a952c4127724cbec15c` | **不可用**：同 card |

基线 `5e1f16ab` 的 6 个 JAR（用于漂移核对，不发布）：Platform `51d227dc…`、ledger `49ea7c9b…`、billing `7dea5214…`、card `4abaeb78…`、client-master `27f479d4…`、ops-bff `5cf1820f…`。

## 3. 发布相对基线的范围（条目 CRC，`scripts/jarcmp.py`）

| 服务 | 变化 |
|---|---|
| platform-support | −16（3 个旧 Controller / Processor 与 `CurrencySpecQueryApi`、`CountryRegionQueryApi` 等 api 6 个、core 7 个），~1 |
| ledger | +32（`paytech-platform-support-reference-data-client`），−16（自有快照 / 客户端 / 调度 / 健康检查 / 配置 10 个与 api 旧接口 6 个），~6 |
| billing | 同 ledger 形态：+32 −16 ~5 |
| card | +32 −8 ~4（删 `PlatformCountryRegionClient(Impl)`） |
| client-master | +32 −9 ~9（删 `ReferenceDataClient(Impl)` 等） |
| operations-console-bff | −6（旧 api 类）~4（财务下拉改调 `ReferenceCurrencyQueryApi`） |

## 4. 漂移核对（`scripts/baseline_check.sh`，可重跑）

有效运行：20:32Z（`evidence/baseline-20261027T2032Z/`）与 20:38Z（`evidence/baseline-20261027T2038Z/`），均在 card-BIN 20:19Z 发布完成之后；20:12Z 的一次落在其发布中途，已作废。脚本按 `scripts/expected-diffs.tsv` 把差异分为已知 / 未知：Platform 12、card 14、ops-bff 21 条为 card-BIN 未提交改动，ledger 19、billing 32 条为公共库更新，**未知差异全部为 0**。结论见 baseline.md 第 1 节：ledger、billing 为更早的已提交版本（自身类 0 差异，公共库差异均有后续提交）；client-master = 5e1f16ab；**Platform、card、ops-bff 在 20:11—20:14Z 被 card-BIN 发布为其未提交版本**，本次的这三个候选不得发布。

## 5. 覆盖文件与 env

`compose-{candidate,rollback}.<服务>.yaml` 各 6 个；回滚文件由 `scripts/gen_rollback.sh` 按现网生成（20:38Z：Platform `186347bc`、card `380064fe`、ops-bff `25f6a869`、ledger `3819ff28`、billing `53450948`、client-master `80257737`，记录在 `rollback-targets.tsv`），部署前重新生成；`scripts/env_check.sh` 渲染比较 12 / 12 全部与运行容器逐键逐值相同。不新增 env；无 SQL、无注册、无语言包。

## 6. 验收脚本（准备，未运行）

`acceptance/refclient_mortest.py`：`capture-finance` / `compare-finance`（财务下拉逐字节）、`probe`（readiness、刷新失败 WARN、Platform 调用方日志、旧接口 200 / 404、`/reference-data/*` 条数）、`ledger`（AE001 余额读取 + 一次试算）、`billing`（阶段 5 原始私网请求，`payloads/billing-fact.json` 已按 `stage5-requests.json` 填好；合成 OE / Client，未登记币种自动选取）、`client-master`（复用参考数据需求的 mor-test 验收 F / A / K）。只读 sidecar 用 `acceptance/HttpGet.java`。

## 7. 待决 / 阻塞

1. Platform、card、ops-bff 必须在 card-BIN 提交之上重建（补丁与脚本已备好，runbook 第 0 节）；client-master 视法务修订发布先后。
2. billing 请求体已按 e2e-dev 的 `stage5-requests.json` 填好；card 在 mor-test 不做 BIN 上传（主会话决定），证据见 runbook 3.4。
3. 健康明细：不改配置无法读到 `referenceData` 条数；运行卡用组合证据替代，直接查看需单独授权临时覆盖 `MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS`。
4. ledger "查不到 → 可重投" 在 mor-test 无安全做法；真实过账不为验收专门造数据（runbook 3.2）。

## 8. b 版：在 card-BIN 提交 c4ff0e20 上重建（2026-10-28 03:2x—03:35Z）

主会话通知 card-BIN 已提交（`c4ff0e20` = 代码 `8a441c90` + 文档）。按 runbook 第 0 节执行；只读访问 mor-test，未执行 SQL、未提交 / 推送，主工作区 git index 未动（`git apply --3way` 只作用于 rel2 自己的 worktree index）。

| 步 | 结果 |
|---|---|
| P-4 冻结 | 03:2xZ 与 03:34Z 两次：`Code-MoR-refclient` 指纹 `a2ea2734e76893e9`、`git status` md5 `9d91dfc2…`，与 a 版及阶段 5 相同 |
| 补丁 | 重新导出 `refclient-on-5e1f16ab.patch`：201 个文件，sha256 前缀 `44bc3c63`（与 a 版相同）。`git apply --3way` 到 `Code-MoR-refclient-rel2`（`c4ff0e20`）：退出码 0、无未合并条目、无冲突标记 |
| 重叠文件 | 只有 1 个：ops-bff `PlatformClientConfiguration.java`（card-BIN 加 `OperationsAccountLabelQueryApi` bean；本需求删 `CurrencySpecQueryApi` bean 并改注释）——合并结果两边改动都在、互不覆盖 |
| 残留引用 | rel2 中旧接口 / 旧类的提及与已审 worktree 完全相同（只剩 `/currency-admin/v1/currency-specs` 管理路径与一处 JavaDoc） |
| 构建 | `build_apps.sh` base2、rel2 均成功（只编译打包，未跑测试） |
| 范围 | rel2 vs base2 的条目差异与 a 版（rel vs base）**逐条相同**（6 个服务），没有多带或少带；card-BIN 的类仍在 |
| 基线核对 | `evidence/baseline-c4ff0e20-20261028T0329Z/`：Platform、card、ops-bff 与现网 **0 差异**（现网 = c4ff0e20）；client-master 现网独有 0（基线多 3 个更新的公共库类）；ledger 19、billing 32 处公共库差异逐条 `git log` 核对，修改它们的提交都是 c4ff0e20 的祖先（`f707f2da`、`be688db7`、`3baef5f0`、`ab27bd76`、`4b661830`、`9f506f0f` 等，`ledger-billing-trace.tsv`），并用 javap 确认现网独有成员都是旧提交中存在、后被改 / 删的成员（如 `1d39cd94` 的 `WorkflowInstanceApi.reverifyAction` 由 `f707f2da` 删除；events.yaml 现网为 ClientReviewDecided v1、基线 v2）→ 登记进 `scripts/expected-diffs.tsv`（card-BIN 行已删除）后 **6 个服务未知差异均为 0** |
| 回滚文件 | `gen_rollback.sh`（03:3xZ）：Platform `186347bc`、card `380064fe`、ops-bff `25f6a869`、ledger `3819ff28`、billing `53450948`、client-master `80257737`（与主会话给出的 card-BIN 现网一致，`rollback-targets.tsv`） |
| env | `env_check.sh`：候选 6 + 回滚 6 全部与运行容器逐键逐值相同 |

b 版镜像（tag `refclient-20261028b`，Dockerfile 与 health-probe 同 a 版；`docker create` + `docker cp` 核对镜像内 JAR 与构建产物逐字节相同；User 10001:10001），已回填 `compose-candidate.*.yaml`：

| 服务 | 镜像 sha256 | JAR sha256 |
|---|---|---|
| platform-support-service | `sha256:1e54f3498feeaf7831c4fe41247cabda8ce1982d9e872bdc716d49c84f37256d` | `e8eedf4d9f548fd28d63241ba53baa3e446b9144d41627d3b5e1275bb1783741` |
| ledger-service | `sha256:51a57a3b721840b079798159f8d149a4362bbb76e763111a7183ab1bb08864e9` | `8dca8af87933ead2de8706999b0e86f994f97c4a29a007d84464dc95a096b4e6` |
| billing-service | `sha256:cf580cfbd05786e55c0e09b9749e1c1ba1a4fe54acef383e355bef7ac6c05650` | `b218dc10ad9d062c802a5df48aca322c806297e4720c434d3d44b37c7cdbd4e9` |
| card-service | `sha256:32d6f27a49864edc836d669d0e261dbb2a67937afe2deb7216afe71eb9b90718` | `2725f2a5e9cc3e9e47c25230e92a6d58d55d4d947c68458a70ea9c8f355fbe03` |
| client-master-service | `sha256:64e515c69cbd0e0b8c34c1ef3386f757c5d180bff8ab91ebdfcc6601d1e9ad1f` | `ec4a2b14e5bf7f67d2e137ee20ade4714a60d777a932ec2c6f1d204226afca49` |
| operations-console-bff | `sha256:4bd7574cad11747c54a608c57b51955a7860e1889d99a71f6569c40d9b528b01` | `645b767368cdaf44141cca1b111e97e0bd76b25899b59c73d6aac484e5b55f39` |

a 版（5e1f16ab）镜像作废，不得发布。异常：无。注意：rebase 后的代码只做了编译打包，未在 rel2 重跑各服务 auto 测试（合并只有 1 个重叠文件且结果已人工核对）；是否补跑由主会话决定。保留的 worktree：`Code-MoR-refclient-base2`、`Code-MoR-refclient-rel2`（及 a 版的 base / rel）。
