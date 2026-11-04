import test from 'node:test';
import assert from 'node:assert/strict';
import {requestErrorMessage,responseErrorMessage} from '../../src/main/resources/static/assets/js/shared/request-errors.js';
test('only own closed error codes can select a translated message',()=>{
 assert.equal(requestErrorMessage('research_required','fallback'),'근거와 연구 계획을 작성하세요.');
 for(const code of ['toString','__proto__','constructor',undefined,null,{}])assert.equal(requestErrorMessage(code,'fallback'),'fallback');
 assert.equal(responseErrorMessage(403,'unknown'),'권한 또는 보안 토큰을 확인하세요.');
 assert.equal(responseErrorMessage(599,'unknown'),'요청에 실패했습니다.');
});
