import {useLocale, useT} from './i18n/react.tsx';

/**
 * 语言切换：顶部栏与登录页使用，选择记在本浏览器
 * <li>只列 manifest 中有已发布语言包的语言；manifest 不可用、没有发布或只有一种语言时整体隐藏。</li>
 */
export function LocaleSwitcher({className}: {className?: string}) {
  const t = useT('common'), {locale, locales, setLocale} = useLocale();
  if (locales.length < 2) return null;
  return <select className={'locale-switcher' + (className ? ' ' + className : '')} aria-label={t('language.label')}
    value={locale} onChange={event => { void setLocale(event.target.value); }}>
    {locales.map(item => <option key={item.code} value={item.code} lang={item.code}>{item.nativeName}</option>)}
  </select>;
}
