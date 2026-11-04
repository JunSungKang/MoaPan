import {issueYear} from '../shared/format.js';
import {readerTools} from './reader-tools.js';
import{request}from'../shared/api.js';
import{requireNewsletter,el,date,report,newsletterElement,originalEl}from'../shared/common.js';
import{render,manuscriptLanguage}from'./render.js';
import{language}from'../shared/i18n.js';
import{brand}from'../shared/config.js';
export function issueLabel(number,publishedAt){if(!Number.isSafeInteger(number)||number<1||!Number.isFinite(Date.parse(publishedAt)))return '';const year=issueYear(publishedAt);const code=year+'-'+String(number).padStart(3,'0');return language==='en'?'Issue '+code:code+'호';}
const target=document.querySelector('#letters'),filter=document.querySelector('#letter-filter');
const href=id=>'/newsletter.html?id='+encodeURIComponent(id);
function teaser(row,index){
 const item=el('article',undefined,'letter-teaser'+(index===0?' latest-letter':''));item.dataset.topic=row.topic;
 const content=el('div',undefined,'teaser-content'),title=el('h2'),link=originalEl('a',row.title);link.href=href(row.id);title.append(link);
 link.lang=manuscriptLanguage(row);
 const meta=el('div',undefined,'teaser-meta');const issue=el('span',issueLabel(row.issue_number,row.first_published_at||row.published_at));if(issue.textContent)meta.append(issue);meta.append(el('span',date(row.published_at)),newsletterElement('span','editorial'));
 const read=el('a','읽어보기 ↗','read-letter');read.href=href(row.id);
 const introduction=el('p',undefined,'teaser-description');introduction.textContent=row.introduction;introduction.dataset.originalContent='';introduction.lang=manuscriptLanguage(row);content.append(originalEl('p',row.topic,'eyebrow',manuscriptLanguage(row)),title,introduction,meta,read);item.append(content);
 if(index===0){const cover=el('div',undefined,'teaser-cover placeholder');cover.setAttribute('aria-hidden','true');cover.append(newsletterElement('strong','name'),newsletterElement('span','english'));item.append(cover);}
 return item;
}
try{
 await requireNewsletter();const list=await request('/api/v1/newsletters');
 const id=new URLSearchParams(location.search).get('id');
 if(id){
  const data=await request('/api/v1/newsletters/'+encodeURIComponent(id)),m=data.manuscript;
  for(const selector of ['#letters','#letter-archive-heading','#letter-archive-tools'])document.querySelector(selector).hidden=true;
  const heading=el('h1');heading.id='letter-title';heading.dataset.originalContent='';heading.textContent=m.title;document.querySelector('#letter-title').replaceWith(heading);
  document.querySelector('#letter-introduction').textContent=m.introduction;for(const selector of ['#letter-title','#letter-introduction'])document.querySelector(selector).lang=manuscriptLanguage(m);
  const setMeta=()=>{const meta=document.querySelector('#letter-meta');meta.replaceChildren();if(m.topic)meta.append(originalEl('span',m.topic,undefined,manuscriptLanguage(m)),document.createTextNode(' · '));meta.append(document.createTextNode([issueLabel(data.issueNumber,data.firstPublishedAt||data.publishedAt),date(data.publishedAt),data.revision>1?(language==='en'?'Revision '+data.revision:'개정 '+data.revision+'판'):''].filter(Boolean).join(' · ')));};setMeta();window.addEventListener('moapan-language',setMeta);
  const setTitle=()=>document.title=m.title+' | '+(language==='ko'?brand.name+' '+brand.english:brand.english);setTitle();window.addEventListener('moapan-language',setTitle);render(document.querySelector('#letter-body'),m);readerTools(document.querySelector('#letter-detail'),data,issueLabel);document.querySelector('#letter-detail').hidden=false;
  const index=list.findIndex(x=>x.id===id);
  if(index>=0)for(const offset of [-1,1]){const row=list[index+offset];if(row){const link=el('a');link.append(el('span',(offset<0?'더 최근 발간물:':'이전 발간물:')+' '),originalEl('span',row.title,undefined,manuscriptLanguage(row)));link.href=href(row.id);document.querySelector('#letter-neighbours').append(link);}}
  document.querySelector('#letter-neighbours').hidden=!document.querySelector('#letter-neighbours').childElementCount;
 }else{
  for(const [i,row]of list.entries())target.append(teaser(row,i));
  if(!list.length){const empty=el('div',undefined,'archive-empty');empty.append(el('h2','다음 이야기를 준비하고 있습니다.'),el('p','새로운 관점과 함께 곧 찾아뵙겠습니다.'));target.append(empty);document.querySelector('#letter-archive-tools').hidden=true;}
  for(const topic of new Set(list.map(row=>row.topic))){const option=originalEl('option',topic);option.value=topic;filter.append(option);}
  if(list.length)try{const latest=await request('/api/v1/newsletters/'+encodeURIComponent(list[0].id));const cover=latest.manuscript.blocks.find(b=>b.type==='image'&&/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(b.url));if(cover){const image=el('img',undefined,'letter-cover');image.src=cover.url;image.alt=cover.text;const slot=target.querySelector('.teaser-cover');slot.removeAttribute('aria-hidden');slot.classList.remove('placeholder');slot.replaceChildren(image);}}catch{/* The archive remains usable if its optional cover is unavailable. */}
 }
}catch(e){if(e.status!==404)report(e);}
filter.addEventListener('change',()=>{for(const article of target.querySelectorAll('article'))article.hidden=!!filter.value&&article.dataset.topic!==filter.value;});
