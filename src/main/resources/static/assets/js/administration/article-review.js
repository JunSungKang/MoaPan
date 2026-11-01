import {request} from '../shared/api.js';
import {el, report} from '../shared/dom.js';
import {compactDate as formatCompactDate} from '../shared/format.js';
import {t} from '../shared/i18n.js';
import {topicChecklist} from './topics.js';
import {translationEditor} from '../translation/administration.js';

/** One visible editor. Only unsaved or busy editors remain mounted when navigating. */
export function initializeArticleReview(root, refresh) {
  const list = root.querySelector('#article-list');
  const status = root.querySelector('#article-review-status');
  const filters=document.querySelector('#article-review-filters'),applied=document.querySelector('#article-review-applied');
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

  let appliedCriteria;
  function paintApplied(){if(!appliedCriteria){applied.replaceChildren();return;}const {from,to,sort}=appliedCriteria;applied.replaceChildren(el('span',t('현재 목록')),el('span',(from||t('전체 기간'))+' ~ '+(to||t('전체 기간'))),el('span',t({collected_desc:'수집일 최신순',collected_asc:'수집일 오래된순',title_asc:'제목 가나다순'}[sort])));}
  let rows = [], current = 0, loading = false, saves = 0, epoch = 0;
  const editors = new Map(), dirty = new Set(), revisions = new WeakMap(), submitted = new WeakMap();
  const compactDate=value=>{const instant=new Date(value);if(!value||!Number.isFinite(instant.getTime()))return t('수집일 미상');return formatCompactDate(instant);};
  const optionTitle=(row,index)=>`${index+1}. ${appliedCriteria?.sort==='title_asc'?row.title+' · '+compactDate(row.collected_at):compactDate(row.collected_at)+' · '+row.title}${row.site_name?' · '+row.site_name:''}`;
  const array = value => Array.isArray(value) ? value : JSON.parse(typeof value === 'string' ? value : value?.value || '[]');
  function controls() {
    for(const control of filters.elements)control.disabled=loading||saves>0;
    refresh.disabled = loading || saves > 0;
    previous.disabled = loading || !rows.length || current === 0;
    next.disabled = loading || current >= rows.length - 1;
    picker.disabled = loading;
  }
  function isDirty(editor) { return [...dirty].some(form => editor.sheet.contains(form)); }
  function releaseCleanEditors(){
    for(const [id,editor]of editors){if(id===rows[current]?.id||isDirty(editor)||editor.sheet.querySelector('[aria-busy="true"]'))continue;
      editor.dispose?.();editor.sheet.remove();editors.delete(id);
    }
  }
  function updateDrafts() {
    rows.forEach((row, index) => {
      const editor = editors.get(row.id), changed = editor && isDirty(editor);
      if (editor) editor.badge.hidden = !changed;
      picker.options[index].textContent = optionTitle(row,index)+(changed?' · '+t('수정 중'):'');
    });
  }
  function busy(form, pending) {
    form.inert = pending; form.setAttribute('aria-busy', String(pending));
    if (pending) { saves++; submitted.set(form, revisions.get(form) || 0); }
    else saves = Math.max(0, saves - 1);
    controls();if(!pending)releaseCleanEditors();
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
    identity.append(el('span',t('수집일')+' · '+compactDate(article.collected_at)));
    if(article.site_name||article.source_label){const source=el('span',[article.site_name,article.source_label].filter(Boolean).join(' · '));source.dataset.originalContent='';identity.append(source);}
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
    const tagChoice=topicChecklist(array(article.tag_ids));let tagsChanged=false;
    tagChoice.node.addEventListener('change',()=>{tagsChanged=true;});
    const save = el('button', '요약·태그·공개 상태 저장'); save.type = 'submit';
    const note = el('p', '', 'article-review-save-status'); note.setAttribute('aria-live', 'polite');
    form.append(options, tagChoice.node, save, note);
    let saving = false;
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (saving || loading) return;
      saving = true; save.disabled = true; busy(form, true); note.textContent = t('저장 중입니다.');
      const payload = {summaryBullets: bullets.value.split('\n').filter(Boolean), summaryLocked: flags.summaryLocked.checked,
        tagsLocked: flags.tagsLocked.checked, published: flags.published.checked,
        tagIds: tagsChanged ? tagChoice.values() : null};
      try {
        await request('/api/v1/admin/articles/' + article.id, {method: 'PATCH', body: payload});
        Object.assign(article,{summary_bullets:payload.summaryBullets,summary_locked:payload.summaryLocked,tags_locked:payload.tagsLocked,public:payload.published,tag_ids:payload.tagIds??article.tag_ids});
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
    list.append(sheet); const editor = {sheet, heading, badge,dispose:tagChoice.dispose}; editors.set(article.id, editor); return editor;
  }
  function show(index, focus = false) {
    if (loading || !Number.isInteger(index) || index < 0 || index >= rows.length) return;
    current = index;
    const editor = editors.get(rows[index].id) || editorFor(rows[index], index);
    for (const other of editors.values()) other.sheet.hidden = other !== editor;
    releaseCleanEditors();
    picker.value = String(index); position.textContent = `${index + 1} / ${rows.length}`; controls();
    if (focus) editor.heading.focus({preventScroll: true});
  }
  async function load() {
    if (loading || saves) return;
    if (dirty.size && !window.confirm(t('저장하지 않은 수정 내용을 버리고 기사 목록을 새로 불러올까요?'))) return;
    const from=filters.elements.collectedFrom.value,to=filters.elements.collectedTo.value,sort=filters.elements.sort.value;
    if(from&&to&&from>to){status.textContent=t('수집 시작일은 종료일보다 늦을 수 없습니다.');return;}
    const query=new URLSearchParams({sort});if(from)query.set('collectedFrom',from);if(to)query.set('collectedTo',to);
    const ticket = ++epoch, previousId = rows[current]?.id;
    loading = true; list.inert = true; controls(); root.setAttribute('aria-busy', 'true'); status.textContent = t('기사를 불러오는 중입니다.');
    try {
      const result = await request('/api/v1/admin/articles?'+query);
      if (ticket !== epoch) return;
      if (!Array.isArray(result) || result.length > 100 || result.some(row => typeof row.id !== 'string' || typeof row.title !== 'string')) throw new Error(t('기사 목록을 불러오지 못했습니다.'));
      const normalized = result.map(row => {
        const summary = array(row.summary_bullets), tags = array(row.tag_ids);
        if (!Array.isArray(summary) || !Array.isArray(tags) || summary.some(value => typeof value !== 'string') || tags.some(value => typeof value !== 'string')) throw new Error(t('기사 목록을 불러오지 못했습니다.'));
        return {...row, summary_bullets: summary, tag_ids: tags};
      });
      if (new Set(normalized.map(row => row.id)).size !== normalized.length) throw new Error(t('기사 목록을 불러오지 못했습니다.'));
      appliedCriteria={from,to,sort};rows = normalized;for(const editor of editors.values())editor.dispose?.();editors.clear(); dirty.clear(); list.replaceChildren(); picker.replaceChildren();
      rows.forEach((row, index) => { const option = el('option', optionTitle(row,index)); option.value = String(index); picker.append(option); });
      loading = false; toolbar.hidden = !rows.length;
      appliedCriteria={from,to,sort};paintApplied();
      status.textContent = rows.length ? '' : t('검토할 기사가 없습니다.');
      refresh.textContent = t('기사 목록 새로고침');
      show(Math.max(0, rows.findIndex(row => row.id === previousId)));
    } catch (error) { if (ticket === epoch) { status.textContent = t(error.message); report(error); } }
    finally { if (ticket === epoch) { loading = false; list.inert = false; root.setAttribute('aria-busy', 'false'); controls(); } }
  }
  previous.addEventListener('click', () => show(current - 1, true));
  next.addEventListener('click', () => show(current + 1, true));
  picker.addEventListener('change', () => show(Number(picker.value), true));
  filters.addEventListener('submit',event=>{event.preventDefault();load();});
  document.querySelector('#clear-article-filters').addEventListener('click',()=>{filters.reset();load();});
  window.addEventListener('moapan-language',()=>{updateDrafts();paintApplied();});
  function clear() { epoch++; appliedCriteria=undefined;paintApplied();rows = []; current = 0;for(const editor of editors.values())editor.dispose?.(); root.setAttribute('aria-busy','false'); editors.clear(); dirty.clear(); list.replaceChildren(); picker.replaceChildren(); toolbar.hidden = true; loading = false; saves = 0; list.inert = false; controls(); }
  window.addEventListener('moapan-auth-lost', clear); window.addEventListener('moapan-clear', clear);
}
