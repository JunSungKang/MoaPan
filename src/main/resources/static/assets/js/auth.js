import {request,refreshCsrf} from './api.js';
import {report} from './common.js';
const form=document.querySelector('form');
function error(name,message){const input=form.elements[name];input.setAttribute('aria-invalid','true');document.querySelector('#'+(name==='termsAccepted'?'terms':name)+'-error').textContent=message;input.focus();}
form.addEventListener('submit',async event=>{
  event.preventDefault();
  for(const node of form.querySelectorAll('.field-error'))node.textContent='';
  for(const input of form.elements)input.removeAttribute('aria-invalid');
  document.querySelector('[role=status]').textContent='';
  const username=form.elements.username.value,password=form.elements.password.value,signup=form.dataset.mode==='signup';
  if(!/^[A-Za-z0-9_]{4,32}$/.test(username)){error('username','영문·숫자·밑줄 4~32자로 입력하세요.');return;}
  if([...password].length<15||[...password].length>128){error('password','비밀번호는 15~128자로 입력하세요.');return;}
  if(signup&&!form.elements.termsAccepted.checked){error('termsAccepted','약관을 읽고 동의해 주세요.');return;}
  const button=form.querySelector('button');button.disabled=true;
  try{
    const body={username,password};
    if(signup){body.termsAccepted=true;body.termsVersion=form.dataset.termsVersion;}
    await request('/api/v1/auth/'+(signup?'signup':'login'),{method:'POST',body});
    form.elements.password.value='';await refreshCsrf();location.assign(signup?'/login.html':'/index.html');
  }catch(e){
    if(e.code==='username_unavailable')error('username','이미 사용 중인 아이디입니다.');
    else if(e.code==='terms_version_outdated')error('termsAccepted','약관이 변경되었습니다. 새로고침 후 다시 확인해 주세요.');
    else report(e);
  }finally{button.disabled=false;}
});
