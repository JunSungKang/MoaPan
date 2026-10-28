// Local-only portable settings. No network or persistent key storage.
export const MAX_FILE_BYTES = 131072;
const FORMAT = 'moapan-preferences-v1';
const encoder = new TextEncoder();
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const identifier = /^[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}$/;
function fail() { throw new Error('지원하지 않거나 손상된 관심사 파일입니다.'); }
function fields(value, names) {
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).sort().join(',') !== [...names].sort().join(',')) fail();
}
function validId(id) { return typeof id === 'string' && identifier.test(id) && !['__proto__','constructor','prototype'].includes(id); }
export function validatePreferences(value) {
  fields(value, ['version','enabled','weights','excludedTags','excludedSources']);
  if (value.version !== 1 || typeof value.enabled !== 'boolean' || !value.weights ||
      typeof value.weights !== 'object' || Array.isArray(value.weights)) fail();
  const weights = {};
  const entries = Object.entries(value.weights);
  if (entries.length > 500) fail();
  for (const [id, weight] of entries) {
    if (!validId(id) || !Number.isInteger(weight) || weight < 1 || weight > 5) fail();
    weights[id] = weight;
  }
  for (const list of [value.excludedTags, value.excludedSources]) {
    if (!Array.isArray(list) || list.length > 500 || !list.every(validId) || new Set(list).size !== list.length) fail();
  }
  return {version:1, enabled:value.enabled, weights, excludedTags:[...value.excludedTags], excludedSources:[...value.excludedSources]};
}
function encode(bytes) { return btoa(Array.from(new Uint8Array(bytes), byte => String.fromCharCode(byte)).join('')); }
function decode(text, length) {
  if (typeof text !== 'string' || text.length > MAX_FILE_BYTES || !/^[A-Za-z0-9+/]*={0,2}$/.test(text)) fail();
  let bytes;
  try { bytes = Uint8Array.from(atob(text), c => c.charCodeAt(0)); } catch { fail(); }
  if (encode(bytes) !== text || (length && bytes.length !== length)) fail();
  return bytes;
}
async function keyFor(password, salt) {
  if (typeof password !== 'string' || [...password].length < 15 || [...password].length > 128)
    throw new Error('파일 비밀번호는 15~128자로 입력해 주세요. 로그인 비밀번호와 다르게 정해 주세요.');
  if (!globalThis.crypto?.subtle) throw new Error('이 브라우저의 보안 연결에서만 파일을 열 수 있습니다.');
  const material = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({name:'PBKDF2', hash:'SHA-256', salt, iterations:600000}, material,
    {name:'AES-GCM', length:256}, false, ['encrypt','decrypt']);
}
export async function exportPreferences(accountId, preferences, password) {
  if (!uuid.test(accountId)) fail();
  const settings = validatePreferences(preferences);
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await keyFor(password, salt);
  const plaintext = encoder.encode(JSON.stringify({accountId, settings}));
  try {
    if (plaintext.length > 90000) throw new Error('설정이 너무 많아 내보낼 수 없습니다. 사용하지 않는 설정을 줄여 주세요.');
    const ciphertext = await crypto.subtle.encrypt({name:'AES-GCM', iv, additionalData:encoder.encode(FORMAT)}, key, plaintext);
    return JSON.stringify({format:FORMAT, salt:encode(salt), iv:encode(iv), ciphertext:encode(ciphertext)});
  } finally { plaintext.fill(0); }
}
export async function importPreferences(text, accountId, password) {
  if (typeof text !== 'string' || encoder.encode(text).length > MAX_FILE_BYTES || !uuid.test(accountId)) fail();
  let file;
  try { file = JSON.parse(text); } catch { fail(); }
  fields(file, ['format','salt','iv','ciphertext']);
  if (file.format !== FORMAT) fail();
  const salt = decode(file.salt,16), iv = decode(file.iv,12), ciphertext = decode(file.ciphertext);
  if (ciphertext.length < 16) fail();
  const key = await keyFor(password,salt);
  let plaintext;
  try { plaintext = new Uint8Array(await crypto.subtle.decrypt({name:'AES-GCM',iv,additionalData:encoder.encode(FORMAT)}, key,ciphertext)); }
  catch { throw new Error('파일 비밀번호가 맞지 않거나 파일이 손상되었습니다.'); }
  try {
    let payload;
    try { payload = JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(plaintext)); } catch { fail(); }
    fields(payload,['accountId','settings']);
    if (payload.accountId !== accountId) throw new Error('이 파일을 만든 계정으로 로그인해 주세요.');
    return validatePreferences(payload.settings);
  } finally { plaintext.fill(0); }
}
