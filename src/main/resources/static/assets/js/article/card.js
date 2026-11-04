import {articleDate} from '../shared/format.js';
import {language,t} from '../shared/i18n.js';
import{el,originalEl}from'../shared/dom.js';
import {articleText} from './article-text.js';

// Article presentation factory: no requests, account state, storage or page bootstrap.
function field(label,value,className){
 const node=el('span',undefined,className);
 node.append(el('span',t(label)+': ','sr-only'),document.createTextNode(value));
 return node;
}
function originalUrl(value){try{const url=new URL(value);return ['http:','https:'].includes(url.protocol)?url.href:'#';}catch{return '#';}}
export function articleCard(article,index){
 const text=articleText(article,language),node=el('article',undefined,index===0?'lead':'');
 const heading=el('h2'),link=originalEl('a',text.title,undefined,text.language);
 link.href=originalUrl(article.originalUrl);link.target='_blank';link.rel='noopener noreferrer';link.lang=text.language;heading.append(link);
 const metadata=el('div',undefined,'article-meta-line');
 const subjects=field('주제',article.tags.map(tag=>t(tag.name)).join(' · ')||t('미분류'),'article-subjects');subjects.title=article.tags.map(tag=>tag.name).join(' · ');
 const written=field('작성일',article.publishedAt?articleDate(article.publishedAt):t('정보 없음'),'article-written');
 const author=field('작성자',article.author||t('정보 없음'),'article-author');if(article.author)author.dataset.originalContent='';author.title=article.author||'';
 metadata.append(subjects,written,author);
 const source=el('p',undefined,'article-source');source.append(el('span',t('출처')+': ','sr-only'),originalEl('span',article.source.site));
 node.append(heading,metadata,source);
 if(text.summaryBullets.length){const list=el('ul');list.dataset.originalContent='';list.lang=text.language;for(const bullet of text.summaryBullets){const item=el('li');item.textContent=bullet;list.append(item);}node.append(list);}
 else node.append(el('p','연구 관점으로 요약할 정보가 부족합니다.','muted'));
 return node;
}
