import {brand} from '../shared/config.js';
import {render} from './render.js';
export function attribution(data,code,url,lang='ko'){
 if(!['FREE_PREVIEW','PAID_SUBSCRIPTION'].includes(data.distribution))throw Error('출처 유형을 확인할 수 없습니다.');
 const paid=data.distribution==='PAID_SUBSCRIPTION',revision=data.revision||1;
 return lang==='en'?[data.manuscript.title+' · '+code+' · Revision '+revision,'Source: '+brand.english,paid?'This content was provided through '+brand.english+"'s paid subscription newsletter.":'This content was provided through '+brand.english+"'s free preview newsletter.",'Original: '+url].join('\n'):[data.manuscript.title+' · '+code+' · 개정 '+revision+'판','출처: '+brand.name+'('+brand.english+')','이 콘텐츠는 '+brand.name+'('+brand.english+')의 '+(paid?'유료 구독':'무료 열람')+' 뉴스레터를 통해 제공되었습니다.','원문: '+url].join('\n');
}
export function plainNewsletter(data,code,url,lang='ko'){
 const parts=[data.manuscript.title,data.manuscript.introduction];
 for(const b of data.manuscript.blocks||[])parts.push(b.type==='reference'?b.text+'\n'+b.url:b.type==='image'?[b.text,b.caption].filter(Boolean).join(' · '):b.text);
 return parts.join('\n\n')+'\n\n—\n'+attribution(data,code,url,lang);
}
export function htmlNewsletter(data,code,url,lang='ko'){
 if(!data.reuseAllowed)throw Error('재게재 권한 확인이 필요합니다.');
 const doc=document.implementation.createHTMLDocument(data.manuscript.title);doc.documentElement.lang=lang;
 const encoding=doc.querySelector('meta[charset]')||doc.createElement('meta');encoding.setAttribute('charset','utf-8');doc.head.prepend(encoding);const viewport=doc.createElement('meta');viewport.name='viewport';viewport.content='width=device-width,initial-scale=1';doc.head.append(viewport);
 const meta=doc.createElement('meta');meta.httpEquiv='Content-Security-Policy';meta.content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'";doc.head.append(meta);
 const css=doc.createElement('style');css.textContent='body{max-width:760px;margin:3rem auto;padding:0 1rem;font-family:Georgia,serif;line-height:1.9;color:#252525}img{max-width:100%;height:auto}blockquote{border-left:2px solid;padding-left:1rem}footer{border-top:1px solid;margin-top:3rem;white-space:pre-wrap}a{overflow-wrap:anywhere}';doc.head.append(css);
 const article=doc.createElement('article'),heading=doc.createElement('h1'),intro=doc.createElement('p');heading.textContent=data.manuscript.title;intro.textContent=data.manuscript.introduction;const body=doc.createElement('div');render(body,data.manuscript);article.append(heading,intro,body);
 const footer=doc.createElement('footer');footer.textContent=attribution(data,code,url,lang);doc.body.append(article,footer);return '<!doctype html>\n'+doc.documentElement.outerHTML;
}
export function worksheet(data,code,url,kind='meeting',lang='ko'){
 const d=data.dossier;if(!d?.forecasts?.length)throw Error('준비된 연구 메모가 없습니다.');
 const en=lang==='en',title={meeting:en?'Meeting brief':'회의용 요약',priority:en?'Research priorities and decision record':'연구 우선순위·의사결정표',experiment:en?'Experiment and evaluation worksheet':'실험·평가 계획서'}[kind];if(!title)throw Error('invalid worksheet');
 const lines=[title,data.manuscript.title,code,(en?'Evidence week: ':'근거 주간: ')+d.weekStart,(en?'Review cutoff: ':'검토 기준: ')+d.cutoff,''];
 for(const [i,f]of d.forecasts.entries()){
  lines.push((i+1)+'. '+f.question,(en?'Observed: ':'확인한 사실: ')+f.observation,(en?'Editorial scenario: ':'편집부 전망: ')+f.outlook,(en?'Research horizon (proposed): ':'연구 기간(제안): ')+f.horizon);
  if(kind!=='meeting')lines.push((en?'Next experiment: ':'다음 실험: ')+f.experiment,(en?'Success/transition criteria (proposed): ':'성공·전환 기준(제안): ')+f.successCriteria,(en?'Stop/hold conditions: ':'중단·보류 조건: ')+f.stopCondition);
  if(kind==='priority')lines.push(en?'Priority [ ] High [ ] Medium [ ] Low; Decision [ ] Adopt [ ] Hold [ ] Stop; Owner/target date: ______':'우선순위 [ ] 높음 [ ] 중간 [ ] 낮음 / 결정 [ ] 도입 [ ] 보류 [ ] 중단 / 담당·목표일: ______');
  if(kind==='experiment')lines.push(en?'Baseline/environment/sample: ______\nMeasurements/results: ______\nDecision and limitations: ______':'기준선·실험 환경·표본: ______\n측정 결과: ______\n판정과 한계: ______');
  for(const id of f.evidenceIds){const e=data.evidence?.find(e=>e.id===id);if(e)lines.push(e.title+' | '+e.url+' | '+(en?'Collected ':'수집 ')+e.collectedAt);}lines.push('');
 }
 lines.push(en?'A proposal for evaluation, not a reproduced result or a promise of performance.':'실험 제안이며 재현된 성과나 성능 보장이 아닙니다.','',attribution(data,code,url,lang));return lines.join('\n');
}
export function download(text,name,type='text/plain;charset=utf-8'){const blob=new Blob([text],{type}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}
