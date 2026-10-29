import {request,refreshCsrf} from '../shared/api.js';
import {report,viewer} from '../shared/common.js';
const form=document.querySelector('form');
if(form.dataset.mode==='login')viewer.then(a=>{if(a)location.replace('/index.html');}).catch(()=>{});
const button=form.querySelector('button'),status=document.querySelector('[role=status]');
let pending=false,deadline=0,timer,limitedUsername=null;
function clearLimit(announce=false){clearInterval(timer);deadline=0;limitedUsername=null;const panel=document.querySelector('#login-limit');if(panel)panel.hidden=true;button.disabled=pending;if(announce)status.textContent='다시 로그인할 수 있습니다. 비밀번호를 확인하고 시도해 주세요.';}
function tick(){const left=Math.max(0,Math.ceil((deadline-Date.now())/1000));if(!left){clearLimit(true);return;}document.querySelector('#login-countdown').textContent=String(Math.floor(left/60)).padStart(2,'0')+':'+String(left%60).padStart(2,'0');}
function startLimit(seconds,accountOnly,username){clearLimit();if(accountOnly&&username.toLowerCase()!==form.elements.username.value.toLowerCase()){status.textContent='';return;}deadline=Date.now()+seconds*1000;limitedUsername=accountOnly?form.elements.username.value.toLowerCase():null;document.querySelector('#login-limit').hidden=false;button.disabled=true;tick();timer=setInterval(tick,250);}
form.elements.username.addEventListener('input',()=>{if(deadline&&limitedUsername!==null&&limitedUsername!==form.elements.username.value.toLowerCase()){clearLimit();status.textContent='';}});
document.addEventListener('visibilitychange',()=>{if(deadline)tick();});
window.addEventListener('pagehide',()=>clearInterval(timer));
window.addEventListener('pageshow',()=>{if(deadline){tick();if(deadline)timer=setInterval(tick,250);}});
function error(name,message){const input=form.elements[name];input.setAttribute('aria-invalid','true');document.querySelector('#'+(name==='termsAccepted'?'terms':name)+'-error').textContent=message;input.focus();}
form.addEventListener('submit',async event=>{
  event.preventDefault();
  if(pending)return;
  if(deadline){tick();if(deadline)return;}
  for(const node of form.querySelectorAll('.field-error'))node.textContent='';
  for(const input of form.elements)input.removeAttribute('aria-invalid');
  document.querySelector('[role=status]').textContent='';
  const username=form.elements.username.value,password=form.elements.password.value,signup=form.dataset.mode==='signup';
  if(!/^[A-Za-z0-9_]{1,128}$/.test(username)){error('username','영문·숫자·밑줄 1~128자로 입력하세요.');return;}
  if([...password].length<6||[...password].length>128){error('password','비밀번호는 6~128자로 입력하세요.');return;}
  if(signup&&!form.elements.termsAccepted.checked){error('termsAccepted','약관을 읽고 동의해 주세요.');return;}
  pending=true;button.disabled=true;
  try{
    const body={username,password};
    if(!signup)body.rememberMe=Boolean(form.elements.rememberMe?.checked);
    if(signup){body.termsAccepted=true;body.termsVersion=form.dataset.termsVersion;}
    await request('/api/v1/auth/'+(signup?'signup':'login'),{method:'POST',body});
    form.elements.password.value='';await refreshCsrf();location.assign(signup?'/login.html':'/index.html');
  }catch(e){
    if(!signup&&e.status===429&&Number.isFinite(e.retryAfterSeconds)&&e.retryAfterSeconds>0){
      status.textContent=e.code==='login_limited'?'로그인 시도가 여러 번 실패해 잠시 제한되었습니다.':'로그인 요청이 많아 잠시 제한되었습니다.';
      startLimit(e.retryAfterSeconds,e.code==='login_limited',username);
    }else if(!signup&&e.code==='invalid_credentials'){
      status.textContent='아이디 또는 비밀번호를 확인해 주세요.'+(e.attemptsRemaining>=1&&e.attemptsRemaining<=5?' 로그인 제한까지 '+e.attemptsRemaining+'회 남았습니다.':'');
    }else if(e.code==='username_unavailable')error('username','이미 사용 중인 아이디입니다.');
    else if(e.code==='terms_version_outdated')error('termsAccepted','약관이 변경되었습니다. 새로고침 후 다시 확인해 주세요.');
    else report(e);
  }finally{pending=false;button.disabled=deadline>Date.now();}
});
