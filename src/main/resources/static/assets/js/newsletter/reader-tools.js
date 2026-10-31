import {request} from '../shared/api.js';
import {el,date,report} from '../shared/common.js';
import {language,t} from '../shared/i18n.js';
import {plainNewsletter,htmlNewsletter,worksheet,download} from './publication-output.js';
export function readerTools(parent,data,label){
 if(!data.dossier?.forecasts?.length)return;
 const url=new URL('/newsletter.html',location.origin);url.searchParams.set('id',data.id);
 const code=()=>label(data.issueNumber,data.firstPublishedAt||data.publishedAt);
 const tools=el('div',undefined,'publication-tools'),feedback=el('p');feedback.setAttribute('aria-live','polite');
 const button=(name,fn)=>{const b=el('button',name);b.type='button';b.addEventListener('click',async()=>{b.disabled=true;try{await fn();}catch(e){report(e);}finally{b.disabled=false;}});tools.append(b);};
 if(data.reuseAllowed){
  button('출처 포함 복사',async()=>{const fresh=await request('/api/v1/newsletters/'+data.id+'/reuse');await navigator.clipboard.writeText(plainNewsletter(fresh,label(fresh.issueNumber,fresh.firstPublishedAt||fresh.publishedAt),url.href,language));feedback.textContent='출처와 원문 링크를 포함해 복사했습니다.';});
  button('재게재용 파일',async()=>{const fresh=await request('/api/v1/newsletters/'+data.id+'/reuse');download(htmlNewsletter(fresh,label(fresh.issueNumber,fresh.firstPublishedAt||fresh.publishedAt),url.href,language),'moapan-'+data.id+'-r'+fresh.revision+'.html','text/html;charset=utf-8');});
 }
 for(const [kind,name]of [['meeting','회의용 요약'],['priority','연구 우선순위표'],['experiment','실험·평가 양식']])button(name,async()=>{const fresh=await request('/api/v1/newsletters/'+data.id+'/worksheet');download(worksheet(fresh,label(fresh.issueNumber,fresh.firstPublishedAt||fresh.publishedAt),url.href,kind,language),'moapan-'+kind+'-r'+fresh.revision+'.txt');});
 button('인쇄',()=>{details.open=true;window.print();});
 const details=el('details',undefined,'research-notes');details.append(el('summary','근거와 연구 메모'));
 const period=el('p');const renderPeriod=()=>period.textContent=t('근거 주간')+': '+data.dossier.weekStart+' · '+date(data.dossier.cutoff);renderPeriod();window.addEventListener('moapan-language',renderPeriod);details.append(period);
 if(data.dossier.correctionNote)details.append(el('p',data.dossier.correctionNote));
 for(const f of data.dossier.forecasts){const article=el('article'),h=el('h3',f.question),list=el('dl');h.dataset.originalContent='';for(const [key,title]of [['observation','확인된 사실'],['outlook','편집부의 전망·가설'],['horizon','연구 기간'],['experiment','다음 실험'],['successCriteria','성공·전환 기준'],['stopCondition','중단·보류 조건']]){const value=el('dd',f[key]);value.dataset.originalContent='';list.append(el('dt',title),value);}article.append(h,list);for(const id of f.evidenceIds){const e=data.evidence?.find(e=>e.id===id);if(!e)continue;const link=el('a',e.title);link.dataset.originalContent='';try{const target=new URL(e.url);if(target.protocol!=='https:'||target.username||target.password)continue;link.href=target.href;}catch{continue;}link.target='_blank';link.rel='noopener noreferrer';const p=el('p');p.append(link,el('span',' · '+date(e.collectedAt)));article.append(p);}details.append(article);}
 parent.append(tools,feedback,el('p','재게재할 때 출처·제공 문구·원문 링크를 함께 표시해 주세요. 수집 기사의 원문과 제3자 그림의 권리는 포함하지 않습니다.','muted'),details);
}
