import { VariableSelect } from './VariableControls';
import { DictionarySelect } from './DictionarySelect';
import { useState } from 'react';
import { Button, Dropdown, Form, Input, Select } from 'antd';
import type { EditorAdditionalAction } from './ruleBuilder';
import type { AuthoringCatalog } from './riskAuthoringCatalog';
import { displayDuration, durationToMillis, durationUnits } from './ruleEditorPresentation';
import type { DurationUnit } from './ruleEditorPresentation';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';

function DurationInput({ value, disabled, label, onChange }: { value: string; disabled: boolean; label: string; onChange: (value: string) => void }) {
  const t = useT('risk');
  const [unit, setUnit] = useState<DurationUnit>(() => displayDuration(value).unit);
  const factor = durationUnits.find(item => item.value === unit)!.factor;
  const amount = /^[0-9]+$/.test(value) && value.length <= 32 && BigInt(value) % factor === 0n ? (BigInt(value) / factor).toString() : value;
  return <div className="risk-duration-input"><Input aria-label={label} inputMode="numeric" autoComplete="off" disabled={disabled} value={amount} placeholder={t('additionalAction.positiveInteger')}
    onChange={event => onChange(durationToMillis(event.target.value, unit))} />
    <Select aria-label={t('additionalAction.unitLabel', { label })} value={unit} disabled={disabled} options={durationUnits.map(item => ({ value: item.value, label: enumLabel(t, 'enum.durationUnit', item.value) }))}
      onChange={next => { setUnit(next); onChange(durationToMillis(amount, next)); }} /></div>;
}

export function AdditionalActionEditor({ actions, catalog, disabled, onChange }: {
  actions: readonly EditorAdditionalAction[]; catalog: AuthoringCatalog; disabled: boolean;
  onChange: (actions: EditorAdditionalAction[]) => void;
}) {
  const t = useT('risk');
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
    return action?.actionCategory === 'RESTRICTION' ? t('additionalAction.cooldownSubject') : action?.actionCategory === 'TAG' ? t('additionalAction.addRiskTag') : action?.actionName ?? code;
  };
  // 目录没有附加动作表示该风控点运行期不支持：无已有动作时整块不显示，有已有动作（旧版本）时只允许删除
  const unsupported = !catalog.additionalActions.length;
  if (unsupported && !actions.length) return null;
  if (unsupported) return <section aria-label={t('additionalAction.title')} className="risk-additional-actions">
    <div className="risk-additional-heading"><h4>{t('additionalAction.title')} <span>{actions.length}</span></h4></div>
    <p className="risk-help">{t('additionalAction.unsupported')}</p>
    {actions.map((action, index) => <div key={action.id} className="risk-action-panel"><div className="risk-action-heading"><span><small>{String(index + 1).padStart(2, '0')}</small>{actionName(action.actionCode)}</span>
      <Button type="text" aria-label={t('additionalAction.remove', { index: index + 1 })} disabled={disabled} onClick={() => onChange(actions.filter((_, position) => position !== index))}>×</Button></div></div>)}
  </section>;
  return <section aria-label={t('additionalAction.title')} className="risk-additional-actions">
    <div className="risk-additional-heading"><h4>{t('additionalAction.title')} <span>{actions.length || ''}</span></h4>
      <Dropdown trigger={['click']} menu={{ items: catalog.additionalActions.map(action => ({ key: action.actionCode, label: actionName(action.actionCode) })), onClick: ({ key }) => add(key) }}>
        <Button type="link" aria-label={t('additionalAction.addLabel')} disabled={disabled || actions.length >= 16 || !catalog.targetBindings.length}>{t('additionalAction.add')}</Button></Dropdown></div>
    <p className="risk-help">{actions.length ? t('additionalAction.configOnly') : t('additionalAction.hint')}</p>
    {actions.map((action, index) => <Form key={action.id} layout="vertical" requiredMark={false} className="risk-action-panel">
      <div className="risk-action-heading"><span><small>{String(index + 1).padStart(2, '0')}</small>{actionName(action.actionCode)}</span>
        <Button type="text" aria-label={t('additionalAction.remove', { index: index + 1 })} disabled={disabled} onClick={() => onChange(actions.filter((_, position) => position !== index))}>×</Button></div>
      <div className="risk-action-fields"><Form.Item label={t('additionalAction.targetRequired')}>
        <Select aria-label={t('additionalAction.targetLabel', { index: index + 1 })} value={action.targetBindingCode || undefined} placeholder={t('additionalAction.targetPlaceholder')} disabled={disabled}
          options={catalog.targetBindings.map(target => ({ value: target.bindingCode, label: target.displayName }))} onChange={targetBindingCode => update(index, { targetBindingCode,subjectVariableCode:null })} />
      </Form.Item>
      {catalog.operandFormat&&<Form.Item label={t('additionalAction.subjectVariableRequired')}><VariableSelect value={action.subjectVariableCode??null} disabled={disabled} filters={{pointCode:catalog.variableQueryContext?.pointCode,usage:'ACTION_PARAMETER',sourceType:'INPUT'}} onChange={v=>update(index,{subjectVariableCode:v.variableCode})}/></Form.Item>}
      {action.parameters.map((parameter, parameterIndex) => {
        const definition = catalog.actionParameters.find(item => item.actionCode === action.actionCode && item.actionVersionNo === action.actionVersionNo && item.parameterCode === parameter.parameterCode);
        const duration = parameter.valueType === 'DURATION';
        const changeValues = (values: string[]) => update(index, { parameters: action.parameters.map((item, position) => position === parameterIndex ? { ...item, values } : item) });
        return <Form.Item key={parameter.parameterCode} label={duration ? t('additionalAction.cooldownRequired') : t('additionalAction.riskTagRequired')}
          extra={duration ? t('additionalAction.cooldownRange', { min: definition?.minimumValue ?? '1', max: definition?.maximumValue ?? '—' }) : t('additionalAction.tagHint', { max: definition?.maxValues ?? 32 })}>
          {duration ? <DurationInput label={t('additionalAction.cooldownLabel', { index: index + 1 })} disabled={disabled} value={parameter.values[0] ?? ''} onChange={value => changeValues([value])} />
            : <DictionarySelect kind="subject-tags" label={t('additionalAction.tagLabel', { index: index + 1 })} multiple values={parameter.values} disabled={disabled}
              maxCount={definition?.maxValues ?? 32} onChange={values => changeValues(values.length ? values : [''])} />}
        </Form.Item>;
      })}</div>
    </Form>)}
  </section>;
}
