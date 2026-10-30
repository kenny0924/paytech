import {
  activeStatuses, isId, isInstant, isInteger, isOneOf, isOptionalText, isRecord, isText, orNull, pageDecoder, require,
} from './dataQueryCommon.ts';
import type { ActiveStatus } from './dataQueryCommon.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('data-query');

// ---------- 枚举 ----------

export const keyValueTypes = ['INTEGER', 'STRING'] as const;
export type KeyValueType = typeof keyValueTypes[number];
export const keyRoles = ['LINK', 'DIMENSION'] as const;
export type KeyRole = typeof keyRoles[number];
/** 值类型显示为 `data-query.enum.keyValueType.<值>`（整数 / 文本），角色为 `data-query.enum.keyRole.<值>`（链路 / 维度）。 */
export const keyValueTypeLabel = (value: string) => enumLabel(t, 'enum.keyValueType', value);
export const keyRoleLabel = (value: string) => enumLabel(t, 'enum.keyRole', value);

/** 表单上限（technique 8.1），最终以服务端校验为准。 */
export const keyTypeLimits = { name: 128, group: 32, pattern: 256, hint: 128, description: 512, maxSortNo: 9999 } as const;

// ---------- 合同类型 ----------

/** K1—K4 的键类型（QueryKeyTypeResponseBody）。 */
export interface QueryKeyType {
  keyTypeId: string; keyTypeCode: string; keyTypeName: string; groupName: string; valueType: KeyValueType; defaultRole: KeyRole;
  entryAllowed: boolean; valuePattern: string | null; inputHint: string | null; sortNo: number; description: string | null;
  status: ActiveStatus; version: number; referencedTemplateCount: number; updatedBy: string; updateTime: string;
}

export function decodeKeyType(value: unknown): QueryKeyType {
  require(isRecord(value) && isId(value.keyTypeId) && isText(value.keyTypeCode) && isText(value.keyTypeName) && isText(value.groupName)
    && isOneOf(keyValueTypes, value.valueType) && isOneOf(keyRoles, value.defaultRole) && typeof value.entryAllowed === 'boolean'
    && isOptionalText(value.valuePattern) && isOptionalText(value.inputHint) && isInteger(value.sortNo) && isOptionalText(value.description)
    && isOneOf(activeStatuses, value.status) && isInteger(value.version, 1) && isInteger(value.referencedTemplateCount ?? 0)
    && isText(value.updatedBy) && isInstant(value.updateTime));
  return {
    ...(value as unknown as QueryKeyType), valuePattern: orNull(value.valuePattern as string | null) || null, inputHint: orNull(value.inputHint as string | null) || null,
    description: orNull(value.description as string | null), referencedTemplateCount: (value.referencedTemplateCount ?? 0) as number,
  };
}
export const decodeKeyTypePage = pageDecoder(decodeKeyType);

// ---------- 表单 ↔ 请求体 ----------

export interface KeyTypeForm {
  keyTypeCode: string; keyTypeName: string; groupName: string; valueType: KeyValueType; defaultRole: KeyRole; entryAllowed: boolean;
  valuePattern: string; inputHint: string; sortNo: number; description: string;
}

export function keyTypeForm(item: QueryKeyType): KeyTypeForm {
  return {
    keyTypeCode: item.keyTypeCode, keyTypeName: item.keyTypeName, groupName: item.groupName, valueType: item.valueType, defaultRole: item.defaultRole,
    entryAllowed: item.entryAllowed, valuePattern: item.valuePattern ?? '', inputHint: item.inputHint ?? '', sortNo: item.sortNo, description: item.description ?? '',
  };
}

/** 新建：排在所选分组末尾（无分组时排在全部之后），取 10 的倍数，便于以后插入。 */
export function newKeyTypeForm(items: readonly QueryKeyType[], groupName = ''): KeyTypeForm {
  const peers = items.filter(item => item.groupName === groupName);
  const last = Math.max(0, ...(peers.length ? peers : items).map(item => item.sortNo));
  return {
    keyTypeCode: '', keyTypeName: '', groupName, valueType: 'INTEGER', defaultRole: 'LINK', entryAllowed: true, valuePattern: '', inputHint: '',
    sortNo: Math.min(keyTypeLimits.maxSortNo, Math.floor(last / 10) * 10 + 10), description: '',
  };
}

const optional = (value: string) => value.trim() ? value.trim() : null;
function commonBody(form: KeyTypeForm) {
  return {
    keyTypeName: form.keyTypeName.trim(), groupName: form.groupName.trim(), valueType: form.valueType, defaultRole: form.defaultRole,
    entryAllowed: form.entryAllowed, valuePattern: optional(form.valuePattern), inputHint: optional(form.inputHint), sortNo: form.sortNo,
    description: optional(form.description),
  };
}
export const createKeyTypeBody = (form: KeyTypeForm) => ({ keyTypeCode: form.keyTypeCode.trim(), ...commonBody(form) });
export const updateKeyTypeBody = (form: KeyTypeForm, expectedVersion: number) => ({ ...commonBody(form), expectedVersion });

// ---------- 分组与排序 ----------

export interface KeyTypeGroup<T extends { groupName: string; sortNo: number } = QueryKeyType> { groupName: string; rank: number; items: T[] }

const bySortNo = <T extends { sortNo: number; keyTypeCode: string }>(a: T, b: T) => a.sortNo - b.sortNo || a.keyTypeCode.localeCompare(b.keyTypeCode);

/** 按分组列出：分组顺序取组内最小 sort_no（与链路图泳道相同），组内按 sort_no、编码。 */
export function groupKeyTypes<T extends { groupName: string; sortNo: number; keyTypeCode: string }>(items: readonly T[]): KeyTypeGroup<T>[] {
  const groups = new Map<string, T[]>();
  for (const item of [...items].sort(bySortNo)) groups.set(item.groupName, [...(groups.get(item.groupName) ?? []), item]);
  return [...groups.entries()].map(([groupName, members]) => ({ groupName, rank: members[0].sortNo, items: members }))
    .sort((a, b) => a.rank - b.rank || a.groupName.localeCompare(b.groupName));
}

/**
 * 拖动排序：把 movingId 放到 targetId 的位置（同组内），返回整体新顺序，sortNo 按位置重写为 序号 × 10（与 K5 服务端一致，用于乐观显示）。
 * 跨组或找不到时返回 null。
 */
export function moveKeyType(items: readonly QueryKeyType[], movingId: string, targetId: string): QueryKeyType[] | null {
  const ordered = [...items].sort(bySortNo);
  const from = ordered.findIndex(item => item.keyTypeId === movingId); const to = ordered.findIndex(item => item.keyTypeId === targetId);
  if (from < 0 || to < 0 || from === to || ordered[from].groupName !== ordered[to].groupName) return null;
  const [moving] = ordered.splice(from, 1); ordered.splice(to, 0, moving);
  return ordered.map((item, index) => ({ ...item, sortNo: (index + 1) * 10 }));
}

/** K5 请求体：全部 ACTIVE 与 INACTIVE 键类型的新顺序。 */
export const keyTypeOrderBody = (ordered: readonly QueryKeyType[]) => ({ items: ordered.map(item => ({ keyTypeId: item.keyTypeId, expectedVersion: item.version })) });

// ---------- 入口格式（正则子集） ----------

export type PatternProblem = 'tooLong' | 'invalid' | 'unsupported';

/** Java 与 JavaScript 含义不同或只有一方支持的转义：\A \Z \z \G \Q \E \R \X \h \H \p \P。 */
const javaOnlyEscapes = 'AZzGQERXhHpP';

/**
 * 入口校验正则只允许 Java 与 JavaScript 行为一致的子集（B13，technique 8.1）：拒绝反向引用、前后断言、内联标志、
 * 所有格与原子组；另拒绝两边含义不同的写法（空字符类 []、字符类中的 [ 与 &&、Java 专有转义），比服务端略严。
 * 空串表示不校验。
 */
export function patternProblem(pattern: string): PatternProblem | null {
  if (!pattern) return null;
  if (pattern.length > keyTypeLimits.pattern) return 'tooLong';
  let inClass = false;
  for (let i = 0; i < pattern.length; i++) {
    const c = pattern[i]; const next = pattern[i + 1] ?? '';
    if (c === '\\') {
      if (!inClass && (/[1-9]/.test(next) || (next === 'k' && pattern[i + 2] === '<'))) return 'unsupported';
      if (javaOnlyEscapes.includes(next) && next !== '') return 'unsupported';
      i++; continue;
    }
    if (inClass) {
      if (c === ']') inClass = false;
      else if (c === '[' || (c === '&' && next === '&')) return 'unsupported';
      continue;
    }
    if (c === '[') {
      const start = pattern[i + 1] === '^' ? i + 2 : i + 1;
      if (pattern[start] === ']') return 'unsupported';
      inClass = true; i = start - 1; continue;
    }
    if (c === '(' && next === '?') {
      const kind = pattern[i + 2] ?? '';
      if (kind === ':') continue;
      if (kind === '<' && /[A-Za-z]/.test(pattern[i + 3] ?? '')) continue;
      return 'unsupported';
    }
    if ('*+?}'.includes(c) && next === '+') return 'unsupported';
  }
  try { new RegExp(`^(?:${pattern})$`); } catch { return 'invalid'; }
  return null;
}

/** 入口值整串匹配；正则为空时视为通过，无法编译时视为不通过。 */
export function matchesPattern(pattern: string | null | undefined, value: string): boolean {
  if (!pattern) return true;
  try { return new RegExp(`^(?:${pattern})$`).test(value); } catch { return false; }
}

export const patternProblemText = (problem: PatternProblem) => t(`keyTypes.pattern.problem.${problem}`);
