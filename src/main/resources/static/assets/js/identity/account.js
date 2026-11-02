import {request} from '../shared/api.js';
import {report,requireAccount} from '../shared/common.js';
import {deletePreferences} from '../personalization/preferences.js';
import {emailVerification} from './email-verification.js';
import {t} from '../shared/i18n.js';
let user;
try{user=await requireAccount();document.querySelector('#account-username').textContent=user.username;document.querySelector('#account-controls').disabled=false;}catch(e){report(e);}
if(user)try{const profile=await request('/api/v1/account/email');document.querySelector('#email-current').textContent=profile.verified?profile.email:t('계정 복구를 위해 수신 이메일을 등록해 주세요.');}catch(error){report(error);}
const emailForm=document.querySelector('#account-email');
const verification=emailVerification(emailForm,'/api/v1/account/email/code',()=>({currentPassword:emailForm.elements.currentPassword.value,email:emailForm.elements.email.value,emailPolicyAccepted:emailForm.elements.emailPolicyAccepted.checked,emailPolicyVersion:'email-1'}));
emailForm.addEventListener('submit',async event=>{event.preventDefault();const button=emailForm.querySelector('[type=submit]'),note=document.querySelector('#email-bind-status');button.disabled=true;
  try{await request('/api/v1/account/email',{method:'PUT',body:{...verification.proof(),emailPolicyAccepted:emailForm.elements.emailPolicyAccepted.checked,emailPolicyVersion:'email-1'}});emailForm.reset();verification.clear();sessionStorage.removeItem('moapan-account');window.dispatchEvent(new Event('moapan-clear'));location.assign('/login.html');}catch(error){note.textContent=t(error.message);}finally{button.disabled=false;}
});
for(const [formId,path,method] of [['change-password','/api/v1/account/password','PATCH'],['delete-account','/api/v1/account','DELETE']]){
  const form=document.querySelector('#'+formId);
  form.addEventListener('submit',async e=>{
    e.preventDefault();const controls=document.querySelector('#account-controls');if(!user||controls.disabled)return;controls.disabled=true;
    try{
      const body={currentPassword:form.elements.currentPassword.value};
      if(method==='PATCH'){body.newPassword=form.elements.newPassword.value;const length=[...body.newPassword].length;if(length<6||length>128)throw new Error('새 비밀번호는 6~128자로 입력해 주세요.');}
      await request(path,{method,body});
      if(method==='DELETE')await deletePreferences(user.id);
      form.reset();sessionStorage.removeItem('moapan-account');location.assign('/login.html');
    }catch(error){report(error);controls.disabled=false;}
  });
}
