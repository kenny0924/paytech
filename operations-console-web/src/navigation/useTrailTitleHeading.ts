import { useEffect } from 'react';

const hiddenClass = 'is-trail-title';

/**
 * 页面标题与顶部路径的当前菜单同名时隐藏页面 h1，避免重复占位
 * <li>只比较内容区第一个 .page-heading 的 h1；详情、编辑等标题文本不同的页面保持原样。</li>
 * <li>页面按需加载、标题随状态变化，用 MutationObserver 持续同步。</li>
 */
export function useTrailTitleHeading(title: string | undefined) {
  useEffect(() => {
    const content = document.querySelector('.console-content');
    if (!content) return;
    const sync = () => {
      const heading = content.querySelector('.page-heading h1');
      content.querySelectorAll(`.${hiddenClass}`).forEach(element => { if (element !== heading) element.classList.remove(hiddenClass); });
      if (heading) heading.classList.toggle(hiddenClass, !!title && heading.textContent?.trim() === title);
    };
    sync();
    const observer = new MutationObserver(sync);
    observer.observe(content, { childList: true, subtree: true, characterData: true });
    return () => observer.disconnect();
  }, [title]);
}
