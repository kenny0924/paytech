import type { FormSchema } from "./model.ts";
import { isPersonRole } from "./schema.ts";

/** 关系类型目录：Schema 的关系类型（t_relation_type）优先；旧环境退回现有五种。 */
export function relationTypes(schema: FormSchema): { code: string; labelKey: string; ownership: boolean; ubo: boolean; role: string }[] {
  if (schema.relationTypes.length) return schema.relationTypes.map((item) => ({ code: item.code, labelKey: item.labelKey,
    ownership: item.ownershipPctRequired ?? ownershipRole(item.code), ubo: item.uboFlag ?? item.code === "UBO", role: item.ruleRole ?? item.code }));
  return ["UBO", "OWNERSHIP", "DIRECTOR", "CONTROL", "SIGNATORY"].map((code) => ({ code, labelKey: "onboardingSchema.relationType." + code,
    ownership: ownershipRole(code), ubo: code === "UBO", role: code === "OWNERSHIP" ? "OWNER" : code }));
}
export const ownershipRole = (code: string) => code === "UBO" || code === "OWNERSHIP";
/** 关系人证件槽：按材料槽的主体角色（ANY_RELATED 等通用角色或具体关系类型 / 规则角色）匹配该人员的角色。 */
export function personSlots(schema: FormSchema, roles: string[]) {
  const types = relationTypes(schema);
  // 槽位角色若是某个关系类型 / 规则角色，只对担任该角色的人员生效；其他非主体角色（如 ANY_RELATED）适用于所有关系人。
  const specific = new Set(types.flatMap((type) => [type.code, type.role]));
  const held = new Set(roles.flatMap((code) => [code, types.find((type) => type.code === code)?.role ?? code]));
  return schema.materials.filter((slot) => isPersonRole(slot.subjectRole)
    && (!specific.has(slot.subjectRole.toUpperCase()) || held.has(slot.subjectRole.toUpperCase())));
}

/**
 * 人员抽屉的 422 字段错误 → 抽屉控件
 * <li>`relation.ownershipPercentage` → 持股；`relation.uboBasis` → 受益所有人依据；其他 `relation.*` → 角色。</li>
 * <li>人员资料字段（可带 `person.` 或 `people[<id>].` 前缀）→ 对应草稿路径。</li>
 * <li>文案由调用方按 message 中的错误码本地化，不直接显示服务端原文。</li>
 */
export function drawerErrors(fields: Record<string, string>, text: (message: string) => string) {
  const relation: { roles?: string; share?: string; basis?: string } = {}, person: Record<string, string> = {};
  for (const [field, message] of Object.entries(fields)) {
    const key = field.replace(/^person\./, "").replace(/^people\[[1-9][0-9]{0,18}\]\./, "");
    if (key.startsWith("relation.") || key === "relation") {
      const name = key.slice("relation.".length);
      if (name === "ownershipPercentage") relation.share = text(message);
      else if (name === "uboBasis") relation.basis = text(message);
      else relation.roles ??= text(message);
    } else if (/^[\w:]+\.\w+$/.test(key)) person[key] = text(message);
  }
  return { relation, person, mapped: Object.keys(relation).length + Object.keys(person).length > 0 };
}
