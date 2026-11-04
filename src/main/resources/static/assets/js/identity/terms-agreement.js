import {t,loadPolicyMessages} from '../shared/i18n.js';
const readableTags=new Set(['p','h2','h3','strong','em','ul','ol','li','br','span']);

// Consent is page-local. The canonical public document owns the legal text/version.
export function termsAgreement(form){
 const trigger=form.querySelector('[data-open-terms]'),consent=form.querySelector('[data-terms-consent]');
 const checkbox=form.elements.termsAccepted;
 const dialog=document.querySelector('#terms-dialog'),content=dialog.querySelector('[data-terms-content]');
 const agree=dialog.querySelector('[data-terms-agree]'),hint=dialog.querySelector('[data-terms-hint]');
 const scrollbar=termsScrollbar(content,dialog.querySelector('[data-terms-scrollbar]'),checkEnd);
 let accepted=false,ready=false,reachedEnd=false,generation=0,documentBody,request;
 const resize=new ResizeObserver(checkEnd);
 function setConsent(value){
  accepted=value;
  checkbox.checked=value;
  consent.textContent=t(value?'이용약관에 동의했습니다.':'약관을 읽고 동의해 주세요.');
  trigger.textContent=t(value?'약관 다시 읽기':'약관 전문 읽기');
  if(value){trigger.removeAttribute('aria-invalid');form.querySelector('#terms-error').textContent='';}
 }
 function checkEnd(){
  scrollbar.update(ready&&dialog.open);
  if(!ready||!dialog.open||content.clientHeight<=0)return;
  const atEnd=content.scrollHeight-content.scrollTop-content.clientHeight<=2;
  if(atEnd)reachedEnd=true;
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
  setConsent(false);ready=false;reachedEnd=false;agree.disabled=true;scrollbar.update(false);
  content.replaceChildren(document.createTextNode(t('약관을 불러오는 중입니다.')));
  hint.textContent=t('끝까지 내려 읽으면 동의할 수 있습니다.');
  dialog.showModal();content.scrollTop=0;scrollbar.update(false);content.focus();
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
 checkbox.addEventListener('click',event=>{
  if(accepted){setConsent(false);return;}
  event.preventDefault();open();
 });
 checkbox.addEventListener('invalid',event=>{
  event.preventDefault();trigger.setAttribute('aria-invalid','true');form.querySelector('#terms-error').textContent=t('약관을 읽고 동의해 주세요.');trigger.focus();
 });
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
 dialog.addEventListener('close',()=>{if(dialog.open)return;generation++;request?.abort();resize.disconnect();ready=false;reachedEnd=false;agree.disabled=true;scrollbar.update(false);trigger.focus();});
 window.addEventListener('moapan-language',()=>{if(dialog.open){reachedEnd=false;agree.disabled=true;requestAnimationFrame(checkEnd);}});
 setConsent(false);
 return {get accepted(){return accepted&&checkbox.checked;}};
}

// A real scroll control remains visible when the OS hides native overlay scrollbars.
function termsScrollbar(viewport,track,onScroll){
 const thumb=track.querySelector('[data-terms-thumb]');
 let active=false,drag=null;
 function update(enabled){
  const height=track.clientHeight,max=Math.max(0,viewport.scrollHeight-viewport.clientHeight);
  active=enabled&&max>2&&height>0;
  const size=active?Math.min(height,Math.max(32,height*viewport.clientHeight/viewport.scrollHeight)):height;
  const fraction=active?Math.max(0,Math.min(1,viewport.scrollTop/max)):0;
  thumb.style.height=size+'px';thumb.style.top=(height-size)*fraction+'px';
  track.setAttribute('aria-valuenow',String(Math.round(fraction*100)));
  track.setAttribute('aria-disabled',String(!active));track.tabIndex=active?0:-1;
  if(!active&&drag){const id=drag.id;drag=null;if(track.hasPointerCapture(id))track.releasePointerCapture(id);}
 }
 function move(event){
  if(!active||drag?.id!==event.pointerId)return;
  const rect=track.getBoundingClientRect(),travel=rect.height-thumb.getBoundingClientRect().height;
  if(travel<=0)return;
  viewport.scrollTop=Math.max(0,Math.min(1,(event.clientY-rect.top-drag.offset)/travel))*(viewport.scrollHeight-viewport.clientHeight);
  onScroll();
 }
 track.addEventListener('pointerdown',event=>{
  if(!active||!event.isPrimary||event.button!==0)return;
  event.preventDefault();track.focus({preventScroll:true});
  const rect=thumb.getBoundingClientRect();
  drag={id:event.pointerId,offset:event.target===thumb?event.clientY-rect.top:rect.height/2};
  track.setPointerCapture(event.pointerId);move(event);
 });
 track.addEventListener('pointermove',move);
 for(const name of ['pointerup','pointercancel','lostpointercapture'])track.addEventListener(name,event=>{
  if(drag?.id!==event.pointerId)return;drag=null;if(track.hasPointerCapture(event.pointerId))track.releasePointerCapture(event.pointerId);
 });
 track.addEventListener('keydown',event=>{
  if(!active)return;
  const page=viewport.clientHeight*.9;
  const moves={ArrowDown:40,ArrowUp:-40,PageDown:page,PageUp:-page};
  if(Object.hasOwn(moves,event.key))viewport.scrollTop+=moves[event.key];
  else if(event.key==='Home')viewport.scrollTop=0;
  else if(event.key==='End')viewport.scrollTop=viewport.scrollHeight;
  else return;
  event.preventDefault();onScroll();
 });
 return {update};
}
