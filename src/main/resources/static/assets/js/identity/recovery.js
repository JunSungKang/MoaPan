import {request} from '../shared/api.js';
import '../shared/common.js';
import {t} from '../shared/i18n.js';
import {emailVerification} from './email-verification.js';
const form=document.querySelector('#password-recovery'),note=document.querySelector('#recovery-status');
const verification=emailVerification(form,'/api/v1/auth/password/reset-code',()=>({username:form.elements.username.value,email:form.elements.email.value}));
form.addEventListener('submit',async event=>{event.preventDefault();const button=form.querySelector('[type=submit]');button.disabled=true;
  try{const newPassword=form.elements.newPassword.value;if([...newPassword].length<6||[...newPassword].length>128)throw new Error(t('비밀번호는 6~128자로 입력하세요.'));await request('/api/v1/auth/password/reset',{method:'POST',body:{username:form.elements.username.value,newPassword,...verification.proof()}});form.reset();verification.clear();sessionStorage.removeItem('moapan-account');window.dispatchEvent(new Event('moapan-clear'));note.textContent=t('비밀번호를 재설정했습니다. 새 비밀번호로 로그인해 주세요.');}
  catch(error){note.textContent=t(error.message);}finally{button.disabled=false;}
});
