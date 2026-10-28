import {exportPreferences,importPreferences,MAX_FILE_BYTES,validatePreferences} from './preferences-transfer.js';
import {loadPreferences,savePreferences,canonicalize} from './preferences.js';

export function setupTransfer(user, dictionary, onApplied) {
  const panel = document.querySelector('#transfer-controls');
  const password = document.querySelector('#transfer-password');
  const input = document.querySelector('#transfer-file');
  const message = document.querySelector('#transfer-status');
  const apply = document.querySelector('#transfer-apply');
  const cancel = document.querySelector('#transfer-cancel');
  let pending = null;
  function reset() { pending = null; apply.hidden = true; cancel.hidden = true; }
  async function run(work) {
    panel.disabled = true;
    message.textContent = '파일을 처리하고 있습니다.';
    try { await work(); }
    catch (error) { reset(); message.textContent = error.message; }
    finally { password.value = ''; panel.disabled = false; }
  }
  input.addEventListener('change', () => { reset(); message.textContent = ''; });
  password.addEventListener('input', () => { reset(); message.textContent = ''; });
  cancel.addEventListener('click', () => { reset(); input.value = ''; message.textContent = '가져오기를 취소했습니다. 기존 설정은 그대로입니다.'; });
  document.querySelector('#transfer-export').addEventListener('click', () => run(async () => {
    reset();
    const text = await exportPreferences(user.id, await loadPreferences(user.id), password.value);
    const url = URL.createObjectURL(new Blob([text],{type:'application/json'}));
    const link = document.createElement('a');
    link.href = url; link.download = 'moapan-interests.moapan';
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    message.textContent = '파일 다운로드를 요청했습니다. 파일과 비밀번호를 따로 보관하고, 다른 기기에서 같은 계정으로 로그인해 가져오세요.';
  }));
  document.querySelector('#transfer-import').addEventListener('click', () => run(async () => {
    reset();
    const file = input.files[0];
    if (!file || file.size > MAX_FILE_BYTES) throw new Error('128KB 이하의 모아판 관심사 파일을 선택해 주세요.');
    pending = validatePreferences(canonicalize(await importPreferences(await file.text(),user.id,password.value),dictionary.redirects));
    const available = new Set(dictionary.tags.map(tag => tag.id));
    const unknown = Object.keys(pending.weights).filter(id => !available.has(id)).length;
    message.textContent = `관심 주제 ${Object.keys(pending.weights).length}개, 제외 주제 ${pending.excludedTags.length}개, 제외 출처 ${pending.excludedSources.length}개를 확인했습니다. 적용하면 이 브라우저의 기존 설정을 모두 바꿉니다.${unknown ? ' 현재 제공되지 않는 주제도 보존되며, 제공되는 뉴스에만 적용됩니다.' : ''}`;
    apply.hidden = false; cancel.hidden = false;
  }));
  apply.addEventListener('click', () => run(async () => {
    if (!pending) throw new Error('먼저 파일을 확인해 주세요.');
    const settings = pending;
    await savePreferences(user.id,settings);
    onApplied(settings);
    reset(); input.value = '';
    message.textContent = '이 브라우저에 관심사를 가져왔습니다. 이후 변경 사항은 각 기기에 따로 저장됩니다.';
  }));
  document.querySelector('#clear-local').addEventListener('click', reset);
  window.addEventListener('pagehide', () => { reset(); password.value = ''; input.value = ''; });
  panel.disabled = false;
}
