import { VariableSelect } from './VariableControls';
import { DictionarySelect } from './DictionarySelect';
import { useState } from 'react';
import { Button, Dropdown, Form, Input, Select } from 'antd';
import type { EditorAdditionalAction } from './ruleBuilder';
import type { AuthoringCatalog } from './riskAuthoringCatalog';
import { displayDuration, durationToMillis, durationUnits } from './ruleEditorPresentation';
import type { DurationUnit } from './ruleEditorPresentation';

function DurationInput({ value, disabled, label, onChange }: { value: string; disabled: boolean; label: string; onChange: (value: string) => void }) {
  const [unit, setUnit] = useState<DurationUnit>(() => displayDuration(value).unit);
  const factor = durationUnits.find(item => item.value === unit)!.factor;
  const amount = /^[0-9]+$/.test(value) && value.length <= 32 && BigInt(value) % factor === 0n ? (BigInt(value) / factor).toString() : value;
  return <div className="risk-duration-input"><Input aria-label={label} inputMode="numeric" autoComplete="off" disabled={disabled} value={amount} placeholder="正整数"
    onChange={event => onChange(durationToMillis(event.target.value, unit))} />
    <Select aria-label={`${label}单位`} value={unit} disabled={disabled} options={durationUnits.map(item => ({ value: item.value, label: item.label }))}
      onChange={next => { setUnit(next); onChange(durationToMillis(amount, next)); }} /></div>;
}

export function AdditionalActionEditor({ actions, catalog, disabled, onChange }: {
  actions: readonly EditorAdditionalAction[]; catalog: AuthoringCatalog; disabled: boolean;
  onChange: (actions: EditorAdditionalAction[]) => void;
}) {
  function update(index: number, change: Partial<EditorAdditionalAction>) {
    if (disabled) return;
    onChange(actions.map((action, position) => position === index ? { ...action, ...change } : action));
  }
  function add(actionCode: string) {
    if (disabled || actions.length >= 16) return;
    const descriptor = catalog.additionalActions.find(action => action.actionCode === actionCode)!;
    onChange([...actions, { id: crypto.randomUUID(), actionCode, actionVersionNo: descriptor.versionNo, targetBindingCode: '',
      parameters: catalog.actionParameters.filter(parameter => parameter.actionCode === actionCode && parameter.actionVersionNo === descriptor.versionNo && !parameter.targetParameterFlag)
        .map(parameter => ({ parameterCode: parameter.parameterCode, valueType: parameter.valueType, values: [''] })),
    }]);
  }
  const actionName = (code: string) => {
    const action = catalog.additionalActions.find(item => item.actionCode === code);
    return action?.actionCategory === 'RESTRICTION' ? '冷却主体' : action?.actionCategory === 'TAG' ? '添加风险标签' : action?.actionName ?? code;
  };
  // 目录没有附加动作表示该风控点运行期不支持：无已有动作时整块不显示，有已有动作（旧版本）时只允许删除
  const unsupported = !catalog.additionalActions.length;
  if (unsupported && !actions.length) return null;
  if (unsupported) return <section aria-label="附加动作" className="risk-additional-actions">
    <div className="risk-additional-heading"><h4>附加动作 <span>{actions.length}</span></h4></div>
    <p className="risk-help">该风控点运行期不支持附加动作，请移除后再保存。</p>
    {actions.map((action, index) => <div key={action.id} className="risk-action-panel"><div className="risk-action-heading"><span><small>{String(index + 1).padStart(2, '0')}</small>{actionName(action.actionCode)}</span>
      <Button type="text" aria-label={`移除附加动作 ${index + 1}`} disabled={disabled} onClick={() => onChange(actions.filter((_, position) => position !== index))}>×</Button></div></div>)}
  </section>;
  return <section aria-label="附加动作" className="risk-additional-actions">
    <div className="risk-additional-heading"><h4>附加动作 <span>{actions.length || ''}</span></h4>
      <Dropdown trigger={['click']} menu={{ items: catalog.additionalActions.map(action => ({ key: action.actionCode, label: actionName(action.actionCode) })), onClick: ({ key }) => add(key) }}>
        <Button type="link" aria-label="添加附加动作" disabled={disabled || actions.length >= 16 || !catalog.targetBindings.length}>＋ 添加动作</Button></Dropdown></div>
    <p className="risk-help">{actions.length ? '本期仅保存配置，冷却和标签尚不执行；包含附加动作的版本暂不可冻结。' : '可配置冷却、风险标签等附加动作。'}</p>
    {actions.map((action, index) => <Form key={action.id} layout="vertical" requiredMark={false} className="risk-action-panel">
      <div className="risk-action-heading"><span><small>{String(index + 1).padStart(2, '0')}</small>{actionName(action.actionCode)}</span>
        <Button type="text" aria-label={`移除附加动作 ${index + 1}`} disabled={disabled} onClick={() => onChange(actions.filter((_, position) => position !== index))}>×</Button></div>
      <div className="risk-action-fields"><Form.Item label="作用对象 *">
        <Select aria-label={`附加动作 ${index + 1} 作用主体`} value={action.targetBindingCode || undefined} placeholder="请选择主体字段" disabled={disabled}
          options={catalog.targetBindings.map(target => ({ value: target.bindingCode, label: target.displayName }))} onChange={targetBindingCode => update(index, { targetBindingCode,subjectVariableCode:null })} />
      </Form.Item>
      {catalog.operandFormat&&<Form.Item label="主体变量 *"><VariableSelect value={action.subjectVariableCode??null} disabled={disabled} filters={{pointCode:catalog.variableQueryContext?.pointCode,usage:'ACTION_PARAMETER',sourceType:'INPUT'}} onChange={v=>update(index,{subjectVariableCode:v.variableCode})}/></Form.Item>}
      {action.parameters.map((parameter, parameterIndex) => {
        const definition = catalog.actionParameters.find(item => item.actionCode === action.actionCode && item.actionVersionNo === action.actionVersionNo && item.parameterCode === parameter.parameterCode);
        const duration = parameter.valueType === 'DURATION';
        const changeValues = (values: string[]) => update(index, { parameters: action.parameters.map((item, position) => position === parameterIndex ? { ...item, values } : item) });
        return <Form.Item key={parameter.parameterCode} label={duration ? '冷却时长 *' : '风险标签 *'}
          extra={duration ? `范围 ${definition?.minimumValue ?? '1'}–${definition?.maximumValue ?? '—'} 毫秒。` : `从启用目录选择，最多 ${definition?.maxValues ?? 32} 个；作用含义由运营判断。`}>
          {duration ? <DurationInput label={`附加动作 ${index + 1} 冷却时长`} disabled={disabled} value={parameter.values[0] ?? ''} onChange={value => changeValues([value])} />
            : <DictionarySelect kind="subject-tags" label={`附加动作 ${index + 1} 标签代码`} multiple values={parameter.values} disabled={disabled}
              maxCount={definition?.maxValues ?? 32} onChange={values => changeValues(values.length ? values : [''])} />}
        </Form.Item>;
      })}</div>
    </Form>)}
  </section>;
}
