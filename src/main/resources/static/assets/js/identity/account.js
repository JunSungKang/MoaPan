import {account,request} from '../shared/api.js';
import {report,requireAccount} from '../shared/common.js';
import {deletePreferences} from '../personalization/preferences.js';
let user;
try{user=await requireAccount();document.querySelector('#account-username').textContent=user.username;document.querySelector('#account-controls').disabled=false;}catch(e){report(e);}
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
