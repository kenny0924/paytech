# Operations Console 多语言迁移指南

适用于 `operations-console-web` 既有页面的迁移（需求 `requirements/20261023/operations-控制台多语言迁移/`，方案 v2）。基础设施已由 F0 完成；本文是模块卡的照做手册。样板：`src/pages/CurrencySpecs.tsx` 与 `src/configuration/currencySpecModel.ts`（及其测试）。

## 1. 基础设施一览

| 位置 | 用途 |
|---|---|
| `src/i18n/index.ts` | 运行时实例 `i18n`、`translator(ns)`、`tOr`、`enumLabel`、`formatDateTime` / `formatDate` / `formatShortDateTime` / `formatNumber` |
| `src/i18n/react.tsx` | `useT(ns)`、`useLocale()`、`I18nGate`（根部已加载全部命名空间，页面不再需要自己的 Gate） |
| `src/i18n/namespaces.ts` | `OPS_NAMESPACES`：23 个命名空间 |
| `src/i18n/en/<ns>.json` | 英文基准（唯一基准，构建时内置） |
| `i18n/zh-CN/<ns>.json` | 简体中文种子：扁平 `{"<完整 key>": "原中文"}` |
| `src/i18n/testing.ts` | 测试辅助 `testT(ns)`：内置英文、不发网络、key 缺失直接抛错 |
| `src/i18nSourceGuard.test.ts` | 禁中文扫描：全 `src` 严格执行（注释与测试除外），白名单只有 `navigation/pageCapabilities.ts` |
| `src/i18nCatalog.test.ts` | en/zh-CN key 一致、ICU 合法且参数一致、代码引用的 key 存在、`nav.resource.*` 覆盖 |
| `scripts/i18n-export-source.mjs` | 合并输出 Platform 导入文件到 `i18n-out/` 并做一致性校验 |

## 2. 命名空间归属（按目录 / 文件前缀）

每张卡只改自己命名空间的 `en/<ns>.json` 与 `zh-CN/<ns>.json`。`common`、`auth`、`nav`、`enum` 由 F0 维护，**模块卡不要改这四个文件**（并行改同一 JSON 必然冲突）；模块需要"保存""取消"之类的词时，可以直接引用已有 `common.action.*`，没有的就在自己命名空间建 key（重复无妨）。

| 命名空间 | 目录 / 文件 | 卡 |
|---|---|---|
| `common` `auth` `nav` | `main.tsx` `App.tsx` `auth/` `navigation/` `feedback/` `components/` `hooks/` `api/` | F0（已完成） |
| `access` | `pages/Accounts*` `pages/account*` `pages/Roles*` `pages/role*` `pages/Audit*` `pages/audit*` `pages/Permissions.tsx` `pages/TenantAccess.tsx` `tenantAccess/` | M6 |
| `gateway` | `pages/GatewayConfiguration*` `pages/gatewayConfiguration*` `pages/ConsoleResources.tsx` `pages/ApiScopes.tsx` `pages/ApiCredentials.tsx` `clientApi/` `configuration/`（`currencySpecModel.ts` 除外） | M6 |
| `client-review` | `pages/ClientOnboardingReview.tsx` `clientReview/` | M6 |
| `currency` | `pages/CurrencySpecs.tsx` `configuration/currencySpecModel.ts` | F0（样板，已完成） |
| `card` | `pages/CardBinLibrary.tsx` `card/` | M6 |
| `finance` | `pages/Finance*` `finance/` | M1 |
| `recon` | `pages/Recon*` `reconciliation/` | M1 |
| `risk` | `pages/Risk*` `risk/` | M2 |
| `workflow` | `pages/Workflow*` `workflow/` | M3 |
| `channel` | `pages/Channel*` `pages/ExternalStandardApis.tsx` `channel/` `externalApi/` | M4 |
| `commercial` | `pages/Commercial*` `commercial/` | M5 |
| `data-query` | `pages/DataSources.tsx` `pages/QueryTemplates.tsx` `dataQuery/` | M7 |
| `file` | `pages/FileStorage.tsx` `pages/FileRecords.tsx` `fileStorage/` `fileRecords/` | M7 |
| `notification` | `pages/Notification*` `notification/` | M7 |
| `webhook` | `pages/WebhookManagement.tsx` `webhook/` | M7 |
| `vault` | `pages/Vault*` `vault/` | M7 |
| `qualification` | `pages/Qualifications.tsx` `qualification/` | 商业关系快照（20261023） |
| `master-data` | `pages/OperatingEntities.tsx` `pages/LocalPartners.tsx` `masterData/` | 商业关系快照（20261023） |
| `i18n-admin` `enum` | `localization/`（已有，不动） | — |

`gateway.json` 中已有 F0 写入的 `gateway.statusModal.*`（`components/GatewayStatusModal.tsx` 用），M6 在同一文件追加即可。

## 3. key 规则

- 形如 `<ns>.<页面或区域>.<名称>`，段名小驼峰，例如 `currency.specs.column.updatedAt`、`recon.exceptions.action.resolve`。Platform 规则：每段 `[A-Za-z0-9_-]+`，总长 3–191。
- 同字不同义用不同 key，不为去重合并；同一页面同一含义只建一个 key。
- 常用区域名：`<page>.title` / `.column.<field>` / `.action.<verb>` / `.field.<name>` / `.placeholder.<name>` / `.required.<name>` / `.empty` / `.loadFailed` / `.confirm.<x>`；弹窗用 `<page>.<dialog>.<x>`。
- 模块私有枚举：`<ns>.enum.<Type>.<VALUE>`，`VALUE` 原样（大写代码），配合 `enumLabel`。
- 页面 h1：若原中文与菜单中文名完全相同（如"币种"），用 `useT('nav')` 的 `resource.<资源编码>`，这样英文 h1 与面包屑一致、`useTrailTitleHeading` 仍会隐藏重复标题；否则用本模块 key。

## 4. 写法

### 4.1 组件内

```tsx
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';

const t = useT('currency'), tCommon = useT('common');
<Button>{tCommon('action.refresh')}</Button>
<Form.Item label={t('editor.code')} rules={[{ required: true, message: t('editor.codeRequired') }]} />
```

`useT` 的参数必须是字符串字面量（`useT('finance')`），一致性测试靠它识别 key 归属；`t('…')` 的 key 尽量写字面量（三元两侧都写字面量也可识别）。

### 4.2 非组件代码（模型、校验、API 辅助）

```ts
import { translator } from '../i18n/index.ts';
const t = translator('currency');               // 模块级只绑定命名空间
export function statusNotice(spec) { return t('statusChange.disableNotice'); }  // 调用时才取文案
```

不要在模块加载时取文案（`const LABEL = t('x')`）。语言切换会整页刷新，所以"调用时取值"总是当前语言。

### 4.3 模块级常量的三种改法（前后对照）

**A. 标签映射表 → 枚举 key + `enumLabel`**

```ts
// 前
export const currencyStatusLabels = { DRAFT: '草稿', ACTIVE: '启用', INACTIVE: '停用' };
<Tag>{currencyStatusLabels[row.status]}</Tag>
// 后：en/currency.json 有 currency.enum.status.DRAFT/ACTIVE/INACTIVE
<Tag>{enumLabel(t, 'enum.status', row.status)}</Tag>   // 未知值显示原代码
```

**B. 含文案的选项 / 表格列 → 放进组件（必要时 `useMemo`）**

```tsx
// 前（模块顶层）
const roundingOptions = roundingModes.map(value => ({ value, label: roundingModeLabels[value] }));
// 后（组件内）
const roundingOptions = useMemo(() => roundingModes.map(value =>
  ({ value, label: enumLabel(t, 'enum.roundingMode', value) })), [t]);
```

只放代码的常量（顺序、颜色、路径）保持在模块顶层；文案在渲染时由 key 翻译，例如 `TimeRangePicker` 的 `shortcuts` 改为只存预设值，标签取 `common.timeRange.preset.<值>`。

**C. 返回文案的模型函数 → 调用时用 `translator`，或返回 key + 参数**

```ts
// 前
return { verb: '停用', notice: '已有账簿与计费事实照常处理…' };
// 后（样板 currencyStatusChange）
return { verb: t('statusChange.disable'), notice: t('statusChange.disableNotice') };
```

函数被很多页面复用、或需要在测试里断言"选了哪条文案"时，也可返回 `{ key, params }` 由组件翻译；同一模块内保持一种写法。

### 4.4 模板字符串 → ICU

不拼接翻译片段，整句一个 key，变量用命名参数：

```json
// en
"currency.specs.count": "{count, plural, one {# currency} other {# currencies}}"
// zh-CN（中文没有复数，保持原句）
"currency.specs.count": "共 {count} 个币种"
```

```tsx
t('specs.count', { count: items.length })
```

- en 与 zh-CN 的参数名必须一致（测试会查）；中文里的 `{count}` 不需要 plural。
- 文案里需要字面的 `'`、`{`、`}` 时按 ICU 转义（`'{'`）；普通英文撇号 `don't` 不需要转义。
- 原中文里把变量放在句中任意位置都可以（`{subject}已删除`），英文按英文语序重写（`Deleted {subject}.`）。
- 嵌入另一个已翻译的词时，先翻译再作参数传入（`roundingMode: enumLabel(t, 'enum.roundingMode', v)`）。
- 句中要嵌入 React 节点（等宽编码、链接、输入框）时用 `src/i18n/richText.ts`：`richText(t, 'trial.pageInput', { input: <InputNumber … /> })`，等宽编码用 `code('#{}')`；节点作命名参数传入，普通参数放第四个参数。不要把句子拆成多个 key 再拼接。

### 4.5 枚举

`enumLabel(t, prefix, value)`：`t(prefix + '.' + value)` 存在则返回翻译，否则返回原值；空值返回 `''`。跨模块共用的枚举才放 `enum` 命名空间（需要时找 F0/F9 统一加，不要并行改 `enum.json`）。

### 4.6 `tOr` 的使用场景

key 可能合法缺失、且有比 key 更好的回退时用：`tOr(t, key, fallback)`。典型是菜单名 `resourceLabel(tNav, resource)`（`navigation/menu.ts`）= `tOr(tNav, 'resource.' + resourceCode, displayName)`。角色权限树的资源节点用同一函数（BFF 节点已带 `code`）。其他场景应当先补 key，不用 `tOr` 掩盖缺失。

### 4.7 日期与数字

`new Date(v).toLocaleString('zh-CN', { hour12: false })` 改为 `formatDateTime(v)`（zh-CN 输出不变，en 随语言）；`Intl.*Format('zh-CN')` 改为 `formatDate` / `formatNumber`（`i18n.locale()`）。antd 组件语言由根部 `ConfigProvider` 决定，**不要再给组件单独传 `locale={zhCN}`**，也不要 `dayjs(...).locale('zh-cn')`（dayjs 全局语言已在 `main.tsx` 设置）。

## 5. 不翻译的内容

- 运营录入或服务端返回的业务数据（产品名、模板名、角色名、发布说明…）、各类编码、ID、金额、币种代码、MID、BIN。
- 服务端错误详情（Problem `detail`、BFF 校验信息）照原样显示。
- `navigation/pageCapabilities.ts`：Platform 注册数据镜像，保留中文 `displayName`，是扫描唯一白名单。
- `workflow/formModel.ts` 的拼音转换逻辑：它处理用户输入的中文字段名，被转换的中文是数据不是文案；但同一文件里的校验提示、默认选项名（`'选项 1'`、`（副本）`）等界面文案仍要迁移。扫描没有为它开白名单，转换逻辑若确需中文字面量，用 `\u` 转义并注明原因。
- "菜单与按钮""权限目录"配置页显示与编辑 Platform 的 `display_name` 原值，只翻译页面框架。

## 6. 测试改写

- 测试以内置英文运行，断言英文基准或通过 key 断言：`const t = testT('currency'); assert.equal(text, t('statusChange.disableNotice'))`，或直接写英文字面量。`testT` 遇到缺失 key 会抛错，防止"回显 key 也通过"。
- 作为业务数据的中文（用户输入的名称、服务端返回的 message）可以保留。
- `ApiError` 的兜底消息现在是英文（`common.apiError.*`），断言中文兜底消息的旧测试要改。
- 源码字符串扫描类测试（如断言源码包含某句中文）改为断言 key（例：`/header\.permissionRefreshFailed/`）。

## 7. 种子与英文写法

- `zh-CN` 种子 = 迁移前代码里的原中文，**逐字**照抄，包括全角标点、空格、`＋` 等符号；拼接句合并成一个 key 时，种子写成合并后的完整原句并用 `{参数}` 占位。
- 原来就是英文显示的文案（如 `Hide Navigation`），zh-CN 种子照抄英文原文。
- JSON 按 key 排序、两空格缩进、UTF-8；en 与 zh-CN 必须同一组 key。
- 英文按 [术语表](../../requirements/20261023/operations-控制台多语言迁移/glossary.md) 第 1—3 节用词，第 4 节写法：菜单 / 标题 / 按钮 / 表头 Title Case（Save Draft、Created At），提示与校验句首大写并带句号（"Enter a reason."），时间列 `… At`，数量句用 ICU 复数，不翻译编码与枚举原值；规范术语 Client、Operating Entity、Local Partner、Channel 等不得替换。

## 8. 自检（交付前全部通过）

```bash
npm test                               # 全部通过（含全量禁中文扫描）
npx tsc --noEmit
npx vite build
node scripts/i18n-export-source.mjs    # 一致性校验 + 各命名空间 key 数
```

迁移已全部完成（F9 起守卫对全 `src` 严格执行，不再有 `MIGRATED_DIRS`）。新增或修改界面文案：先在 `src/i18n/en/<ns>.json` 加英文基准 key，同时在 `i18n/zh-CN/<ns>.json` 加中文种子，再在多语言管理中发布。
