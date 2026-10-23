import { useState } from 'react';
import { Dropdown } from 'antd';
import { CheckOutlined, DownOutlined, GlobalOutlined } from '@ant-design/icons';
import { useLocale, useT } from './react';
import { switchLanguage } from './switchLanguage';

/**
 * Interface language menu (prototype 10 / 12): globe + current language's native name; the menu lists the
 * published locales by native name and code with a check on the current one. Hidden while fewer than two
 * locales are published. Choosing a language reloads the page on the same route (see switchLanguage).
 */
export function LanguageSwitcher({ className }: { className?: string }) {
  const t = useT('auth');
  const { locale, locales } = useLocale();
  const [switching, setSwitching] = useState(false);
  if (locales.length < 2) return null;
  const current = locales.find(item => item.code === locale) ?? locales[0];
  function choose(code: string) {
    if (code === locale || switching) return;
    setSwitching(true);
    void switchLanguage(code);
  }
  return <Dropdown trigger={['click']} placement="bottomRight" disabled={switching}
    menu={{
      selectable: true, selectedKeys: [locale], onClick: ({ key }) => choose(key),
      items: locales.map(item => ({
        key: item.code,
        label: <span className="language-option" lang={item.code}>
          <span><span className="language-option-name">{item.nativeName}</span><span className="language-option-code">{item.code}</span></span>
          {item.code === locale && <CheckOutlined className="language-option-check" />}
        </span>,
      })),
    }}
    popupRender={menu => <div className="language-menu">
      <div className="language-menu-title">{t('language.title')}</div>
      {menu}
      <div className="language-menu-hint">{t('language.hint')}</div>
    </div>}>
    <button type="button" className={['language-switcher', className].filter(Boolean).join(' ')}
      aria-label={t('language.title')} aria-haspopup="menu">
      <GlobalOutlined aria-hidden="true" /><span lang={current.code}>{current.nativeName}</span><DownOutlined aria-hidden="true" className="language-switcher-arrow" />
    </button>
  </Dropdown>;
}
