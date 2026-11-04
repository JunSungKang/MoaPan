import {request} from '../shared/api.js';
import {el,originalEl} from '../shared/dom.js';
import {date} from '../shared/format.js';
import {t} from '../shared/i18n.js';

const states={QUEUED:'대기',RUNNING:'수집 중',SUCCEEDED:'완료',FAILED:'실패',CANCELLED:'취소',NO_CHANGE:'새 자료 없음',PASSED:'점검 통과'};
export function showCollectionResult(value,kind='result') {
  const node=document.querySelector('#source-result');
  let text='';
  if(kind==='accepted')text=t('작업을 접수했습니다. 아직 수집 성공을 뜻하지 않습니다.')+'\n'+t(states[value.status]||'대기');
  else if(Array.isArray(value))text=value.length?value.map(row=>`${row.started_at?date(row.started_at):''} · ${t(states[row.result]||row.result||'결과 없음')}${row.error_code?' · '+row.error_code:''}`).join('\n'):t('실행 기록이 없습니다.');
  else if(Array.isArray(value.items))text=t(value.notice||'미리보기이며 기사로 저장·공개되지 않았습니다.')+'\n'+value.items.slice(0,5).map(item=>`${item.title}\n${t('발행일')}: ${item.publishedAt?date(item.publishedAt):t('알 수 없음')}\n${item.originalUrl||''}\n${item.description||t('제공된 설명이 없습니다.')}`).join('\n\n');
  else if(states[value.status])text=t(states[value.status])+(value.attempts!==undefined?' · '+t('시도 횟수')+' '+value.attempts:'')+(value.error_code?'\n'+value.error_code:'');
  else text=JSON.stringify(value,(key,item)=>['id','source_id','site_id','article_id','lease_token'].includes(key)?undefined:item,2);
  node.textContent=text;
}
export function initializeCollectionJobs(){
  const form=document.querySelector('#job-query'),select=document.querySelector('#job-id'),note=document.querySelector('#job-status');
  let loading=false;const optionData=new WeakMap();
  function paintOption(option,{job,source}){option.textContent=`${source} · ${job.created_at?date(job.created_at):t('방금 요청')} · ${t(states[job.status]||job.status)}`;}
  window.addEventListener('moapan-language',()=>{for(const option of select.options){const data=optionData.get(option);if(data)paintOption(option,data);}});
  function capture(job,source){
    let option=[...select.options].find(option=>option.value===job.id);
    if(!option){option=originalEl('option');option.value=job.id;select.append(option);}
    const data={job,source};optionData.set(option,data);paintOption(option,data);select.value=job.id;
  }
  document.querySelector('#refresh-jobs').addEventListener('click',async()=>{
    if(loading)return;loading=true;const button=document.querySelector('#refresh-jobs');button.disabled=true;
    try{const rows=await request('/api/v1/admin/jobs'),previous=select.value;select.replaceChildren();const blank=el('option','작업을 선택하세요.');blank.value='';select.append(blank);
      for(const job of rows)capture(job,[job.site_name,job.source_label].filter(Boolean).join(' · '));select.value=[...select.options].some(option=>option.value===previous)?previous:'';
      note.textContent=t(rows.length?'작업을 선택한 뒤 상태 조회를 누르세요.':'최근 수집 작업이 없습니다.');
    }catch(error){note.textContent=t(error.message);}finally{loading=false;button.disabled=false;}
  });
  form.addEventListener('submit',async event=>{event.preventDefault();if(!select.value)return;
    try{const job=await request('/api/v1/admin/jobs/'+encodeURIComponent(select.value));showCollectionResult(job);note.textContent=t('상태를 확인했습니다.');}
    catch(error){note.textContent=t(error.message);}
  });
  return {capture};
}
