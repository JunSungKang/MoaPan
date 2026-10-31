import {request} from '../shared/api.js';
import {el, report} from '../shared/common.js';
import {t} from '../shared/i18n.js';
import {translationEditor} from '../translation/administration.js';

/** One visible editor. Visited editors and their drafts live only in this document. */
export function initializeArticleReview(root, refresh) {
  const list = root.querySelector('#article-list');
  const status = root.querySelector('#article-review-status');
  const toolbar = el('div', undefined, 'article-review-toolbar');
  toolbar.id = 'article-review-toolbar'; toolbar.hidden = true;
  const previous = el('button', '이전 기사'), next = el('button', '다음 기사');
  previous.type = next.type = 'button'; previous.id = 'review-previous'; next.id = 'review-next';
  const position = el('span', '', 'article-review-position');
  position.id = 'article-review-position'; position.setAttribute('aria-live', 'polite');
  const pickerLabel = el('label', '검토할 기사'), picker = el('select');
  picker.id = 'review-article-picker'; picker.dataset.originalContent = '';
  pickerLabel.htmlFor = picker.id; pickerLabel.append(picker);
  toolbar.append(previous, position, next, pickerLabel); list.before(toolbar);

  let rows = [], current = 0, loading = false, saves = 0, epoch = 0;
  const editors = new Map(), dirty = new Set(), revisions = new WeakMap(), submitted = new WeakMap();
  const array = value => Array.isArray(value) ? value : JSON.parse(typeof value === 'string' ? value : value?.value || '[]');
  function controls() {
    refresh.disabled = loading || saves > 0;
    previous.disabled = loading || !rows.length || current === 0;
    next.disabled = loading || current >= rows.length - 1;
    picker.disabled = loading;
  }
  function isDirty(editor) { return [...dirty].some(form => editor.sheet.contains(form)); }
  function updateDrafts() {
    rows.forEach((row, index) => {
      const editor = editors.get(row.id), changed = editor && isDirty(editor);
      if (editor) editor.badge.hidden = !changed;
      picker.options[index].textContent = `${index + 1}. ${row.title}${changed ? ' · ' + t('수정 중') : ''}`;
    });
  }
  function busy(form, pending) {
    form.inert = pending; form.setAttribute('aria-busy', String(pending));
    if (pending) { saves++; submitted.set(form, revisions.get(form) || 0); }
    else saves = Math.max(0, saves - 1);
    controls();
  }
  function saved(form) {
    if (!root.contains(form)) return;
    if ((revisions.get(form) || 0) === submitted.get(form)) dirty.delete(form);
    updateDrafts();
  }
  function editorFor(article, index) {
    const sheet = el('article', undefined, 'article-review-sheet');
    sheet.dataset.articleId = article.id;
    const heading = el('h3', article.title); heading.id = 'review-title-' + index;
    heading.tabIndex = -1; heading.dataset.originalContent = '';
    sheet.setAttribute('aria-labelledby', heading.id);
    const badge = el('span', '저장하지 않은 변경', 'article-review-dirty'); badge.hidden = true;
    const identity = el('p', undefined, 'article-review-identity');
    const id = el('code', article.id); id.dataset.originalContent = '';
    identity.append(el('span', '기사 ID'), id);
    sheet.append(badge, heading, identity);
    const form = el('form'), summary = el('label', '요약'), bullets = el('textarea');
    bullets.setAttribute('aria-label', '수동 요약 (한 줄 한 항목)');
    bullets.value = array(article.summary_bullets).join('\n'); summary.append(bullets); form.append(summary);
    const options = el('fieldset', undefined, 'article-review-options');
    options.append(el('legend', '공개·잠금 설정'));
    const flags = {};
    for (const [name, label] of [['published', '공개'], ['summaryLocked', '요약 잠금'], ['tagsLocked', '태그 잠금']]) {
      const check = el('input'); check.type = 'checkbox';
      check.checked = name === 'published' ? article.public : name === 'summaryLocked' ? article.summary_locked : article.tags_locked;
      flags[name] = check;
      const node = el('label', label, 'check'); node.prepend(check); options.append(node);
    }
    const tagLabel = el('label', '태그 UUID 목록'), tagIds = el('input');
    tagIds.placeholder = '태그 UUID를 쉼표로 구분'; tagIds.setAttribute('aria-label', '수동 태그 UUID 목록');
    tagIds.value = array(article.tag_ids).join(','); let tagsChanged = false;
    tagIds.addEventListener('input', () => { tagsChanged = true; }); tagLabel.append(tagIds);
    const save = el('button', '요약·태그·공개 상태 저장'); save.type = 'submit';
    const note = el('p', '', 'article-review-save-status'); note.setAttribute('aria-live', 'polite');
    form.append(options, tagLabel, save, note);
    let saving = false;
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (saving || loading) return;
      saving = true; save.disabled = true; busy(form, true); note.textContent = t('저장 중입니다.');
      const payload = {summaryBullets: bullets.value.split('\n').filter(Boolean), summaryLocked: flags.summaryLocked.checked,
        tagsLocked: flags.tagsLocked.checked, published: flags.published.checked,
        tagIds: tagsChanged ? tagIds.value.split(',').map(value => value.trim()).filter(Boolean) : null};
      try {
        await request('/api/v1/admin/articles/' + article.id, {method: 'PATCH', body: payload});
        saved(form); note.textContent = t('저장했습니다.');
      } catch (error) { note.textContent = t(error.message); report(error); }
      finally { saving = false; save.disabled = false; busy(form, false); }
    });
    sheet.append(form, translationEditor(article.id, {onBusy: busy, onSaved: saved}));
    const changed = event => {
      const target = event.target.closest('form'); if (!target) return;
      revisions.set(target, (revisions.get(target) || 0) + 1); dirty.add(target); updateDrafts();
    };
    sheet.addEventListener('input', changed); sheet.addEventListener('change', changed);
    list.append(sheet); const editor = {sheet, heading, badge}; editors.set(article.id, editor); return editor;
  }
  function show(index, focus = false) {
    if (loading || !Number.isInteger(index) || index < 0 || index >= rows.length) return;
    current = index;
    const editor = editors.get(rows[index].id) || editorFor(rows[index], index);
    for (const other of editors.values()) other.sheet.hidden = other !== editor;
    picker.value = String(index); position.textContent = `${index + 1} / ${rows.length}`; controls();
    if (focus) editor.heading.focus({preventScroll: true});
  }
  async function load() {
    if (loading || saves) return;
    if (dirty.size && !window.confirm(t('저장하지 않은 수정 내용을 버리고 기사 목록을 새로 불러올까요?'))) return;
    const ticket = ++epoch, previousId = rows[current]?.id;
    loading = true; list.inert = true; controls(); root.setAttribute('aria-busy', 'true'); status.textContent = t('기사를 불러오는 중입니다.');
    try {
      const result = await request('/api/v1/admin/articles');
      if (ticket !== epoch) return;
      if (!Array.isArray(result) || result.length > 100 || result.some(row => typeof row.id !== 'string' || typeof row.title !== 'string')) throw new Error(t('기사 목록을 불러오지 못했습니다.'));
      const normalized = result.map(row => {
        const summary = array(row.summary_bullets), tags = array(row.tag_ids);
        if (!Array.isArray(summary) || !Array.isArray(tags) || summary.some(value => typeof value !== 'string') || tags.some(value => typeof value !== 'string')) throw new Error(t('기사 목록을 불러오지 못했습니다.'));
        return {...row, summary_bullets: summary, tag_ids: tags};
      });
      if (new Set(normalized.map(row => row.id)).size !== normalized.length) throw new Error(t('기사 목록을 불러오지 못했습니다.'));
      rows = normalized; editors.clear(); dirty.clear(); list.replaceChildren(); picker.replaceChildren();
      rows.forEach((row, index) => { const option = el('option', `${index + 1}. ${row.title}`); option.value = String(index); picker.append(option); });
      loading = false; toolbar.hidden = !rows.length;
      status.textContent = rows.length ? '' : t('검토할 기사가 없습니다.');
      refresh.textContent = t('기사 목록 새로고침');
      show(Math.max(0, rows.findIndex(row => row.id === previousId)));
    } catch (error) { if (ticket === epoch) { status.textContent = t(error.message); report(error); } }
    finally { if (ticket === epoch) { loading = false; list.inert = false; root.setAttribute('aria-busy', 'false'); controls(); } }
  }
  previous.addEventListener('click', () => show(current - 1, true));
  next.addEventListener('click', () => show(current + 1, true));
  picker.addEventListener('change', () => show(Number(picker.value), true));
  refresh.addEventListener('click', load);
  window.addEventListener('moapan-language', updateDrafts);
  function clear() { epoch++; rows = []; current = 0; root.setAttribute('aria-busy','false'); editors.clear(); dirty.clear(); list.replaceChildren(); picker.replaceChildren(); toolbar.hidden = true; loading = false; saves = 0; list.inert = false; controls(); }
  window.addEventListener('moapan-auth-lost', clear); window.addEventListener('moapan-clear', clear);
}
