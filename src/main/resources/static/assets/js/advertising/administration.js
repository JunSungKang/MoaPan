import {request} from '../shared/api.js';
import {t} from '../shared/i18n.js';
import {renderBanner} from './banner.js';

export async function initializeAdvertising(){
  const form=document.querySelector('#advertising-form'),note=document.querySelector('#advertising-status');let version;
  const value=()=>({version,enabled:form.elements.enabled.checked,provider:form.elements.provider.value,title:form.elements.title.value,description:form.elements.description.value,targetUrl:form.elements.targetUrl.value,imagePath:form.elements.imagePath.value,publisherId:form.elements.publisherId.value,slotId:form.elements.slotId.value});
  function providerChanged(){const external=form.elements.provider.value==='ADSENSE';document.querySelector('#adsense-fields').hidden=!external;document.querySelector('#banner-fields').hidden=external;form.elements.enabled.disabled=external;if(external)form.elements.enabled.checked=false;document.querySelector('#adsense-pending').hidden=!external;}
  async function load(){const s=await request('/api/v1/admin/advertisements');version=s.version;for(const [key,v]of Object.entries(s))if(form.elements[key]){if(key==='enabled')form.elements[key].checked=v;else form.elements[key].value=v;}providerChanged();form.querySelector('[type=submit]').disabled=false;}
  form.elements.provider.addEventListener('change',providerChanged);
  document.querySelector('#reload-advertising').addEventListener('click',()=>load().then(()=>note.textContent='').catch(e=>note.textContent=e.message));
  document.querySelector('#preview-advertising').addEventListener('click',()=>{try{if(form.elements.provider.value==='ADSENSE'){note.textContent=t('애드센스는 연결 준비 상태입니다. 실제 광고를 요청하지 않습니다.');return;}renderBanner(document.querySelector('#advertising-preview'),{...value(),enabled:true});note.textContent=t('미리보기입니다. 설정을 저장해야 지면에 반영됩니다.');}catch{note.textContent=t('광고 문구와 HTTPS 링크를 확인해 주세요.');}});
  form.addEventListener('submit',async e=>{e.preventDefault();const button=form.querySelector('[type=submit]');button.disabled=true;try{const s=await request('/api/v1/admin/advertisements',{method:'PUT',body:value()});version=s.version;note.textContent=t(s.provider==='ADSENSE'?'애드센스 연결 정보를 저장했습니다. 실제 송출은 아직 연결되지 않았습니다.':'광고 설정을 저장했습니다. 다음 지면 조회부터 반영됩니다.');}catch(error){note.textContent=t(error.code==='version_conflict'?'다른 관리자가 설정을 변경했습니다. 다시 불러온 뒤 수정하세요.':error.code==='adsense_connection_required'?'애드센스 연결 확인 후 광고를 활성화할 수 있습니다.':'광고 설정을 저장하지 못했습니다. 입력값을 확인해 주세요.');}finally{button.disabled=false;}});
  await load();
}
