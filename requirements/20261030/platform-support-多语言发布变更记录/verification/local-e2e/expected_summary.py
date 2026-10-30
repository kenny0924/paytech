"""独立基准：按现行算法（ReleaseContent.current 对比线上发布语言包、ReleaseChangeSummaryFact 口径）算预检摘要。只读。"""
import importlib.util, sys, json, argparse
spec = importlib.util.spec_from_file_location('bf', sys.argv[1]); bf = importlib.util.module_from_spec(spec); spec.loader.exec_module(bf)
a = argparse.Namespace(container=sys.argv[4] if len(sys.argv) > 4 else 'mor-local-mysql-1', db=sys.argv[2], user='root', storage_url=sys.argv[3], insecure=True)
locales = [r[0] for r in bf.mysql_rows(a, "SELECT locale_code FROM t_i18n_locale WHERE status='ENABLED' AND locale_code<>'en'")]
bundles, cache, out = bf.load_bundles(a), {}, {}
for app_id, code, current_release_id, release_no in bf.mysql_rows(a, """SELECT a.application_id, a.application_code, a.current_release_id, r.release_no
    FROM t_i18n_application a LEFT JOIN t_i18n_release r ON r.release_id=a.current_release_id"""):
    live = bf.release_content(a, bundles.get(int(current_release_id), []), cache) if current_release_id else {}
    keys = {k: (c, t) for k, c, t in bf.mysql_rows(a, "SELECT key_id, key_code, source_text FROM t_i18n_key WHERE application_id=%s AND status='ACTIVE'" % app_id)}
    current = {'en': {c: t for c, t in keys.values()}}
    for key_id, loc, text in bf.mysql_rows(a, "SELECT t.key_id, t.locale_code, t.text FROM t_i18n_translation t JOIN t_i18n_key k ON k.key_id=t.key_id WHERE k.application_id=%s AND k.status='ACTIVE'" % app_id):
        if loc in locales and key_id in keys: current.setdefault(loc, {})[keys[key_id][0]] = text
    d = bf.diff(live, current)
    en = lambda t: sorted(k for l, k, ct, _ in d if l == 'en' and ct == t)
    tr = {}
    for l, k, ct, _ in d:
        if l != 'en': tr.setdefault(l, []).append(k)
    out[code] = {'baseReleaseNo': int(release_no) if release_no else None, 'addedKeyCount': len(en('ADDED')), 'addedKeys': en('ADDED')[:20],
                 'deprecatedKeyCount': len(en('REMOVED')), 'deprecatedKeys': en('REMOVED')[:20], 'sourceChangedKeyCount': len(en('CHANGED')),
                 'sourceChangedKeys': en('CHANGED')[:20], 'translationChanges': [{'locale': l, 'changedCount': len(v), 'sampleKeys': sorted(v)[:20]} for l, v in sorted(tr.items())]}
print(json.dumps(out, ensure_ascii=False, indent=1))
