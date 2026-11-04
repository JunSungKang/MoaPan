import {request} from '../shared/api.js';
import {t} from '../shared/i18n.js';

/** Verification material lives only in the current page; never URL, storage or analytics. */
export function emailVerification(form,path,body){
  const button=form.querySelector('[data-request-code]'),note=form.querySelector('[data-email-status]');
  let challenge=null,pending=false,generation=0,deadline=0,timer;
  function render(){const left=Math.max(0,Math.ceil((deadline-Date.now())/1000));button.disabled=pending||left>0;button.textContent=left?t('다시 보내기')+' ('+left+')':t('인증번호 받기');if(!left){clearInterval(timer);timer=null;}}
  function invalidate(){generation++;challenge=null;form.elements.code.value='';}
  for(const name of ['username','email'])form.elements[name]?.addEventListener('input',invalidate);
  button.addEventListener('click',async()=>{
    if(pending||deadline>Date.now())return;
    for(const name of ['username','email','currentPassword']){const input=form.elements[name];if(input&&!input.reportValidity())return;}
    if(form.elements.emailPolicyAccepted&&!form.elements.emailPolicyAccepted.checked){note.textContent=t('이메일 이용 안내를 확인해 주세요.');return;}
    const ticket=++generation;pending=true;challenge=null;render();note.textContent=t('인증번호 발송을 요청하고 있습니다.');
    try{const response=await request(path,{method:'POST',body:body()});if(ticket!==generation)return;challenge=response.challengeId;deadline=Date.now()+response.resendAfterSeconds*1000;note.textContent=t('발송을 요청했습니다. 메일함에서 인증번호를 확인해 5분 안에 입력해 주세요.');clearInterval(timer);timer=setInterval(render,1000);
    }catch(error){if(ticket===generation){note.textContent=t(error.message);if(error.status===429&&error.retryAfterSeconds){deadline=Date.now()+error.retryAfterSeconds*1000;clearInterval(timer);timer=setInterval(render,1000);}}}
    finally{pending=false;render();}
  });
  window.addEventListener('pagehide',()=>{clearInterval(timer);timer=null;});window.addEventListener('pageshow',()=>{render();if(deadline>Date.now()&&!timer)timer=setInterval(render,1000);});window.addEventListener('moapan-language',render);
  return {proof(){if(!challenge)throw new Error(t('인증번호를 먼저 요청해 주세요.'));if(!/^[0-9]{6}$/.test(form.elements.code.value))throw new Error(t('인증번호 6자리를 입력해 주세요.'));return {challengeId:challenge,code:form.elements.code.value};},clear(){invalidate();clearInterval(timer);deadline=0;render();}};
}
