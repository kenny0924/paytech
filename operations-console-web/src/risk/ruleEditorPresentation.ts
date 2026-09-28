import type { EditorCondition } from './ruleBuilder.ts';

export function appendCondition(condition: EditorCondition, child: EditorCondition, kind: 'ALL' | 'ANY' = 'ALL'): EditorCondition {
  return condition.kind === 'ALL' || condition.kind === 'ANY'
    ? { ...condition, children: [...condition.children, child] }
    : { id: crypto.randomUUID(), kind, children: [condition, child] };
}

export function removeConditionChild(condition: EditorCondition, index: number): EditorCondition {
  if (condition.kind === 'CONDITION' || condition.kind === 'NOT' || index < 0 || index >= condition.children.length) return condition;
  const children = condition.children.filter((_, position) => position !== index);
  return children.length === 1 ? children[0]! : children.length ? { ...condition, children } : condition;
}

export function conditionCount(condition: EditorCondition): number {
  return condition.kind === 'CONDITION' ? 1 : condition.children.reduce((sum, child) => sum + conditionCount(child), 0);
}

export function conditionDepth(condition: EditorCondition): number {
  return condition.kind === 'CONDITION' ? 1 : 1 + Math.max(...condition.children.map(conditionDepth));
}

export const durationUnits = [
  { value: 'MILLISECOND', label: '毫秒', factor: 1n },
  { value: 'SECOND', label: '秒', factor: 1000n },
  { value: 'MINUTE', label: '分钟', factor: 60000n },
  { value: 'HOUR', label: '小时', factor: 3600000n },
  { value: 'DAY', label: '天', factor: 86400000n },
] as const;
export type DurationUnit = typeof durationUnits[number]['value'];

export function durationToMillis(value: string, unit: DurationUnit): string {
  if (!/^[0-9]+$/.test(value) || value.length > 32) return value;
  return (BigInt(value) * durationUnits.find(item => item.value === unit)!.factor).toString();
}

export function displayDuration(value: string): { value: string; unit: DurationUnit } {
  if (!/^[1-9][0-9]*$/.test(value) || value.length > 32) return { value, unit: 'MILLISECOND' };
  const amount = BigInt(value);
  const unit = [...durationUnits].reverse().find(item => amount % item.factor === 0n)!;
  return { value: (amount / unit.factor).toString(), unit: unit.value };
}
