# client-fe 验证记录：client-console-bff / client-console-web（阶段 4，R8）

> 工作区：`/Users/kenny/Desktop/Workspace/Code-MoR-refdata`（基于 `aba76a1a`，未提交）。范围：`paytech/client-console-bff`、`client-console-web`。依据：requirement R8、B7、AC-07 / AC-11 / AC-14；technique F-05、F-06、F-12、F-13，S-04、S-05、S-09，8.4（D-04），第 9、10 节。

## 1. 改动文件

### client-console-bff
| 文件 | 说明 |
|---|---|
| `integration/PlatformReferenceDataClient.java`（新） + `impl/PlatformReferenceDataClientImpl.java`（新） | 调用 `ReferenceCountryQueryApi` / `ReferenceCurrencyQueryApi` / `CountrySubdivisionQueryApi`，固定 `status=ACTIVE`；回执缺列表、条目缺编码或名称、不是 ACTIVE、行政区不属于请求的国家 → `UPSTREAM_INVALID_RESPONSE`；不重试 |
| `configuration/PlatformClientConfiguration.java` | 三个 QueryApi Bean（`client.onboarding-enabled=true` 时创建），与其他 Platform 客户端同一套发现、超时、严格 Problem；另启用严格 JSON，响应上限 `262144` 字节（新增 `create(..., int maximumBytes)` 重载，其他客户端行为不变） |
| `domain/enums/ReferenceCatalog.java`（新） | 保留目录码 `REF_CURRENCY`、`REF_COUNTRY_SUBDIVISION` |
| `service/model/response/ClientFormSchemaResponseBody.java`（新） | D-04 页面模型：顶层字段与 client-master 相同；`catalogs` 的条目改为 BFF 自有的 `CatalogItem{code,labelKey,name,logoPublicUrl,countryCode}`；新增 `countries[]{code,name,logoPublicUrl,callingCode}`；分区、地址格式、标识类型、关系、材料、声明直接沿用 client-master API 的嵌套类型 |
| `service/processor/GetClientFormSchemaProcessor.java`（新） + `request/GetClientFormSchemaRequest.java`（新） | 组装：client-master Schema → ACTIVE 国家（总是）→ ACTIVE 币种（Schema 引用 `REF_CURRENCY` 时）→ ACTIVE 行政区（引用 `REF_COUNTRY_SUBDIVISION` 时）；client-master 自有目录原样转换 |
| `integration/CctOnboardingClient.java` + `impl/CctOnboardingClientImpl.java` | 新增 `formSchema(...)`（原 Controller 内的形状检查移到这里） |
| `ext/rest/ClientOnboardingController.java` | `GET /client-onboarding/v1/form-schema` 路径与参数校验不变，改为调用 Processor，返回 `ClientFormSchemaResponseBody` |
| 测试 `auto/ClientFormSchemaHttpAutoTest.java`（新） | 见第 3 节 |
| 测试 `auto/ClientOnboardingHttpAutoTest.java` | 协议模拟器补上 `/reference-data/countries` 回执（form-schema 现在总会读国家） |

### client-console-web
| 文件 | 说明 |
|---|---|
| `src/onboarding/refdata.ts`（新） | B7 名称规则与选项构造：国家 / 币种用 `Intl.DisplayNames`（`fallback:"none"`）→ 后端 `name` → 编码；行政区用 `refdata.subdivision.<CODE>` → `name` → 编码；搜索（本地化名、英文名、编码，忽略大小写与变音符号，编码前缀优先）；图标地址只接受 http(s) 或站内绝对路径 |
| `src/onboarding/RefSelect.tsx`（新） | 可搜索下拉：触发按钮（`role=combobox`）+ 列表（`listbox`/`option`，`aria-activedescendant`），每项 国旗/图标 + 本地化名称 + 等宽编码 + 选中勾；↑↓ / Home / End / Enter / Esc；当前值不在选项中时按编码显示；≤640px 改为底部面板（搜索固定在顶部）；`OptionsUnavailable`：字段内“选项暂时无法加载 · 重试” |
| `src/onboarding/widgets.tsx` | 删除内置 249 个国家编码；COUNTRY 控件、证件签发国、地址国家都用 `schema.countries`；`REF_` 目录的 SELECT 与地址组件用 `RefSelect`；行政区按地址国家筛选，该国没有行政区时改为文本输入；`countryName` 改为从 `refdata.ts` 再导出（其他页面不用改） |
| `src/onboarding/schema.ts` | `addressPathsToClear`：地址国家一变就清空已选行政区（即使地址格式不变），原有“格式切换清空不共有组件”规则不变 |
| `src/onboarding/model.ts` | `CatalogItem` 加可选 `name/logoPublicUrl/countryCode`；`FormSchema.countries`；`readSchema` 解码并过滤非法国家与图标地址 |
| `src/onboarding/ReviewTask.tsx` | 核对页：国家带英文名回退；`REF_CURRENCY` 显示“本地化名称 (CODE)”；行政区按 refdata → name → 编码 |
| `src/onboarding/context.tsx`、`ApplicationRoot.tsx` | 上下文新增 `reloadSchema()`，供“重试”只重新读取 Schema |
| `src/i18n/runtime.ts`、`react.tsx` | 新增 `find()` / `useOptionalT()`：取不到时返回 null 且不告警，用于数据所有的 `refdata` 命名空间 |
| `src/onboarding/onboarding.css` | 下拉、面板、底部面板、图标占位、不可用提示样式（沿用 v2 token；错误态红框） |
| `src/i18n/en/onboarding-schema.json`、`i18n/client-console.zh-CN.seed.json` | 删除 `catalog.INDUSTRY.*`、`catalog.CURRENCY.*`、`catalog.STATE_OR_PROVINCE.*`（英文 48 个、中文 48 个） |
| `src/i18n/en/onboarding.json` + 中文种子 | 新增 7 个键 `onboarding.refdata.{select_country,search_placeholder,search_hint,currency_hint,no_match,unavailable,retry}` |
| 测试 | `refdata.test.ts`（新）、`countrypack.test.ts`、`schema.test.ts`、`runtime.test.ts`、`i18n-coverage.test.ts` |

## 2. 设计取舍

- **页面模型（D-04）**：BFF 自有的是顶层类型和目录条目、国家条目；其余嵌套结构直接复用 client-master API 的类型，避免复制 15 个类后出现两份合同不一致。目录条目字段名沿用 client-master 的 `code`（不是 `itemCode`），Web 的 `readSchema` 已经兼容两种写法。
- **行政区的国家范围**：只有辖区本地地址格式引用 `REF_COUNTRY_SUBDIVISION`，且辖区代码本身是 ACTIVE 国家代码（AE、SA、EG 都满足；`t_jurisdiction.country_code` 等于辖区代码）时，只读取该国（`countryCode=<辖区>`）。如果通用地址格式或普通字段也引用它，或者辖区代码不是国家代码（比如以后的自由区辖区），就读取全部 ACTIVE 行政区，不传 `countryCode`。前端始终按地址国家筛选。这样不需要再调用一次 `jurisdictions`。
- **失败处理**：任何 Platform 读取失败，整页都返回统一的 Problem：503 `DEPENDENCY_UNAVAILABLE`、504 `DEPENDENCY_TIMEOUT` 或 502 `UPSTREAM_INVALID_RESPONSE`，不会返回缺少选项的 Schema。所以 Web 的“选项暂时无法加载 · 重试”只用于兜底：国家列表为空，或者 `REF_` 目录没有下发（比如 BFF 版本较旧）。这时不使用任何内置列表。
- **`refdata` 命名空间**：由 Platform 发布，英文基准就是参考数据的 `name`，所以没有放进 `builtin`、没有 `src/i18n/en/refdata.json`，也不进入导出脚本，避免导入时覆盖 Platform 的键。缺失翻译时由 `find()` 返回 null，界面显示后端 `name`，不会显示原始键（AC-14）。i18n 守卫增加了一条：`builtin` 和中文种子都不能含 `refdata`，旧的三组目录键必须已删除。
- **币种显示**：列表每行显示本地化名称，编码放在右侧等宽列；选中后的按钮显示“本地化名称 (CODE)”，与原型 C 一致。没有图标的币种不显示占位；国家没有国旗时显示中性灰色占位。
- **移动端（390px）**：现有组件很容易做到，已经实现。通过 `matchMedia("(max-width:640px)")` 切换为底部面板，有遮罩、拖动条、标题和关闭按钮，搜索框固定在顶部，列表在下方滚动，打开时锁定页面滚动，与原型 E 一致。
- **其他 SELECT**（组织形式、业务模式等 client-master 自有目录）仍使用原生 `select`，不在本次范围内。

## 3. 执行的命令与结果

| 命令 | 结果 |
|---|---|
| `./mvnw -o -q -pl client-console-bff/paytech-client-console-bff-app,client-console-bff/paytech-client-console-bff-test -am test -Dtest='com.paytech.client.console.bff.**'`（JDK 25，`~/.m3-refdata`） | exit 0；BFF 共 100 个测试，全部通过（10 个类；`ClientFormSchemaHttpAutoTest` 8 个，`ClientOnboardingHttpAutoTest` 32 个）。reactor 编译时使用的是工作区里 cm-dev、pf-dev 当前的 API 源码 |
| `npm ci`（只在工作区目录执行） | 安装 80 个包 |
| `npm test` | 128 个，全部通过（原有 118 个） |
| `npm run build`（`tsc --noEmit && vite build`） | 通过；有一条“chunk 超过 500 kB”的提示，改动前就已存在 |
| `npm run i18n:export-source` | 9 个命名空间、994 个键，英文与中文的键集合一致（生成的 `i18n-out/` 已删除） |
| 浏览器冒烟（临时的 Vite 页面用夹具 Schema 渲染真实控件，结束后已删除） | 1280 宽、zh-CN：国家下拉显示国旗和本地化名称，输入“阿”能匹配阿联酋、阿曼、沙特（英文名匹配），Enter 可以选中；币种显示“阿联酋迪拉姆 (AED)”和图标；酋长国显示 `refdata` 中文翻译，缺翻译的 Sharjah 回退英文名；地址国家改成 GB 后已选酋长国被清空，并变为可选文本框。390 宽（iframe）：底部面板与原型 E 一致。选项缺失时每个字段都显示不可用提示。控制台没有报错 |

`ClientFormSchemaHttpAutoTest` 的场景：
- `test_normalFlow`：补全国家、币种、AE 行政区，自有目录不变，调用顺序与 Query 符合预期。
- `test_edgeCase_subdivisionsBeyondLocalAddress`：通用地址格式也引用行政区时读取全部。
- `test_edgeCase_jurisdictionNotCountry`：辖区代码不是 ACTIVE 国家时读取全部。
- `test_noReferenceCatalogs`：Schema 不引用参考数据时只读国家。
- `test_platformUnavailable`：国家、币种、行政区分别返回 503，整页都返回 503 `DEPENDENCY_UNAVAILABLE`。
- `test_platformInvalidResponse`：回执中有 INACTIVE 条目或行政区国家不符时返回 502 `UPSTREAM_INVALID_RESPONSE`。
- `test_ownerUnavailable`：client-master 不可用时不调用 Platform。
- `test_invalidParameter`。

外部 Mocker 按路径返回配置好的 DTO；没有配置的调用会在 `@AfterEach` 中报失败；测试不用 Mockito。

## 4. 遗留问题

1. **端到端没有和真实 Platform / client-master 联调**：BFF 测试使用测试 HTTP 接口，浏览器冒烟使用夹具。真实的 en / zh-CN、1440 / 390 冒烟（AC-11）要等阶段 6 部署后在 mor-test 上做。
2. **`refdata` 中文翻译依赖 Platform 发布**：client-console 应用的 `refdata` 命名空间要创建、导入并发布（块 139，S-09）。发布之前行政区显示英文 `name`，这是预期行为。
3. **行业（INDUSTRY）**：当前的 client-console Schema 不采集行业，所以没有做两级行业卡片；只删除了旧的 i18n 键。
4. **重试的范围**：“重试”只重新读取 Schema。BFF 正常时整页失败会先发生，所以这个入口只在 BFF 返回不完整时出现。
5. 依赖 cm-dev 已提交到工作区的 DML（`REF_CURRENCY`、`REF_COUNTRY_SUBDIVISION`，`t_catalog_item` 已没有参考数据）；`countrypack.test.ts` 增加了对应的守卫断言。
