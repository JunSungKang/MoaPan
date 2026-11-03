import {t,loadPolicyMessages} from '../shared/i18n.js';
const readableTags=new Set(['p','h2','h3','strong','em','ul','ol','li','br','span']);

// Consent is page-local. The canonical public document owns the legal text/version.
export function termsAgreement(form){
 const trigger=form.querySelector('[data-open-terms]'),consent=form.querySelector('[data-terms-consent]');
 const dialog=document.querySelector('#terms-dialog'),content=dialog.querySelector('[data-terms-content]');
 const agree=dialog.querySelector('[data-terms-agree]'),hint=dialog.querySelector('[data-terms-hint]');
 let accepted=false,ready=false,reachedEnd=false,generation=0,documentBody,request;
 const resize=new ResizeObserver(checkEnd);
 function setConsent(value){
  accepted=value;
  consent.textContent=t(value?'이용약관에 동의했습니다.':'약관을 읽고 동의해 주세요.');
  trigger.textContent=t(value?'약관 다시 읽기':'약관 전문 읽기');
  if(value){trigger.removeAttribute('aria-invalid');form.querySelector('#terms-error').textContent='';}
 }
 function checkEnd(){
  if(!ready||!dialog.open||content.clientHeight<=0)return;
  if(content.scrollHeight-content.scrollTop-content.clientHeight<=2)reachedEnd=true;
  agree.disabled=!reachedEnd;
  hint.textContent=t(reachedEnd?'약관을 확인했다면 동의 여부를 선택해 주세요.':'끝까지 내려 읽으면 동의할 수 있습니다.');
 }
 function readableNode(node,depth=0){
  if(!node||depth>16||typeof node.text!=='string'||!Array.isArray(node.children))throw new Error('terms_unavailable');
  if(node.kind==='text'&&node.children.length===0)return document.createTextNode(node.text);
  if(!readableTags.has(node.kind)||node.text!=='')throw new Error('terms_unavailable');
  const element=document.createElement(node.kind);
  for(const child of node.children)element.append(readableNode(child,depth+1));
  return element;
 }
 async function fetchTerms(signal){
  const response=await fetch('/api/v1/auth/terms',{credentials:'omit',cache:'no-store',signal});
  if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw new Error('terms_unavailable');
  const source=await response.text();
  if(source.length>131072)throw new Error('terms_unavailable');
  const policyDocument=JSON.parse(source);
  if(policyDocument.version!==form.dataset.termsVersion)throw new Error('terms_version_outdated');
  if(!Array.isArray(policyDocument.nodes))throw new Error('terms_unavailable');
  const body=document.createElement('div');
  for(const child of policyDocument.nodes)body.append(readableNode(child));
  if(body.textContent.trim().length<20)throw new Error('terms_unavailable');
  return body;
 }
 async function open(){
  if(dialog.open)return;
  setConsent(false);ready=false;reachedEnd=false;agree.disabled=true;
  content.replaceChildren(document.createTextNode(t('약관을 불러오는 중입니다.')));
  hint.textContent=t('끝까지 내려 읽으면 동의할 수 있습니다.');
  dialog.showModal();content.scrollTop=0;content.focus();
  const ticket=++generation,controller=new AbortController();request=controller;
  const timeout=setTimeout(()=>controller.abort(),10000);
  try{
   const [,body]=await Promise.all([loadPolicyMessages(),documentBody||fetchTerms(controller.signal)]);
   if(ticket!==generation||!dialog.open)return;
   documentBody=body;content.replaceChildren(body.cloneNode(true));content.scrollTop=0;ready=true;
   resize.observe(content);resize.observe(content.firstElementChild);
   requestAnimationFrame(checkEnd);
  }catch(error){
   if(ticket!==generation||!dialog.open)return;
   content.textContent=t(error.message==='terms_version_outdated'?'약관이 변경되었습니다. 새로고침 후 다시 확인해 주세요.':'약관을 불러오지 못했습니다. 닫고 다시 시도해 주세요.');
  }finally{clearTimeout(timeout);}
 }
 trigger.addEventListener('click',open);
 content.addEventListener('scroll',checkEnd,{passive:true});
 agree.addEventListener('click',()=>{if(!ready||!reachedEnd)return;setConsent(true);dialog.close();});
 for(const button of dialog.querySelectorAll('[data-terms-decline],[data-terms-close]'))button.addEventListener('click',()=>{setConsent(false);dialog.close();});
 dialog.addEventListener('cancel',()=>setConsent(false));
 dialog.addEventListener('keydown',event=>{
  if(event.key!=='Tab')return;
  const items=[...dialog.querySelectorAll('button:not(:disabled),[tabindex="0"]')];
  const first=items[0],last=items.at(-1);
  if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
  else if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
 });
 dialog.addEventListener('close',()=>{if(dialog.open)return;generation++;request?.abort();resize.disconnect();ready=false;reachedEnd=false;agree.disabled=true;trigger.focus();});
 window.addEventListener('moapan-language',()=>{if(dialog.open){reachedEnd=false;agree.disabled=true;requestAnimationFrame(checkEnd);}});
 return {get accepted(){return accepted;}};
}
