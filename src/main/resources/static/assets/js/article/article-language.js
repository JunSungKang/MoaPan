import {el} from '../shared/common.js';

/** Switch only between supplied article texts; no network request or tracking. */
export function articleLanguage(article, headingLink, summaryList) {
 const original={title:article.title,summaryBullets:article.summaryBullets};
 const translation=article.translation;
 const valid=translation?.language==='ko'&&typeof translation.title==='string'&&translation.title.trim()
   &&Array.isArray(translation.summaryBullets)&&translation.summaryBullets.length===original.summaryBullets.length
   &&translation.summaryBullets.every(s=>typeof s==='string'&&s.trim());
 const controls=el('div',undefined,'article-language');
 const note=el('p',undefined,'byline');
 const show=translated=>{
  const text=translated?translation:original;
  headingLink.textContent=text.title;
  headingLink.lang=translated?'ko':article.source.language;
  summaryList.lang=headingLink.lang;
  summaryList.replaceChildren(...text.summaryBullets.map(s=>el('li',s)));
  note.textContent=translated?'한국어 자동 번역 · 정확한 표현은 원문을 확인하세요.':`${article.summaryMode==='MANUAL'?'수동 편집':'자동 추출'} · 원문 언어 ${article.source.language}`;
 };
 if(valid){
  const english=el('button','원문'),korean=el('button','한국어');
  controls.setAttribute('role','group');controls.setAttribute('aria-label','기사 언어');
  for(const button of [english,korean])button.type='button';
  const select=translated=>{show(translated);english.setAttribute('aria-pressed',String(!translated));korean.setAttribute('aria-pressed',String(translated));};
  english.addEventListener('click',()=>select(false));korean.addEventListener('click',()=>select(true));
  controls.append(english,korean);select(false);
 }else show(false);
 return {controls,note};
}
