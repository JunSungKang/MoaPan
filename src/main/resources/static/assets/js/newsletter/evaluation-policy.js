export const groups=Object.freeze([
 {id:'llm',name:'LLM·RAG 개발자',unit:'명',target:6},{id:'database',name:'DB·검색 개발자',unit:'명',target:6},{id:'security',name:'보안 엔지니어',unit:'명',target:6},{id:'research',name:'연구자',unit:'명',target:6},{id:'lead',name:'아키텍트·기술 리드',unit:'명',target:6},
 {id:'startup',name:'스타트업 기술 책임자',unit:'개사',target:5},{id:'midmarket',name:'중소·중견기업 R&D 책임자',unit:'개사',target:5},{id:'enterprise',name:'대기업 연구·기술 전략 담당',unit:'개사',target:5}
]);
const keys=['id','invited','completed','improved','nextIssue','attributionCorrect','pilotDiscussion'];
export const blankEvaluation=()=>({version:1,groups:groups.map(g=>({id:g.id,invited:0,completed:0,improved:0,nextIssue:0,attributionCorrect:0,pilotDiscussion:0}))});
export function validateEvaluation(input){
 if(!input||Object.keys(input).sort().join(',')!=='groups,version'||input.version!==1||!Array.isArray(input.groups)||input.groups.length!==groups.length)throw Error('올바른 익명 집계 파일이 아닙니다.');
 const found=new Set();for(const row of input.groups){if(!row||Object.keys(row).sort().join(',')!==[...keys].sort().join(',')||!groups.some(g=>g.id===row.id)||found.has(row.id))throw Error('올바른 익명 집계 파일이 아닙니다.');found.add(row.id);for(const k of keys.filter(k=>k!=='id'))if(!Number.isInteger(row[k])||row[k]<0||row[k]>9999)throw Error('집계는 0 이상의 정수로 입력하세요.');if(row.completed>row.invited||['improved','nextIssue','attributionCorrect','pilotDiscussion'].some(k=>row[k]>row.completed))throw Error('성과 수는 참여 완료 수를 넘을 수 없습니다.');}
 return {version:1,groups:groups.map(g=>({...input.groups.find(r=>r.id===g.id)}))};
}
export function summarizeEvaluation(input){
 const safe=validateEvaluation(input);const rows=safe.groups.map(r=>{const design=groups.find(g=>g.id===r.id);return {...r,target:design.target,unit:design.unit,ratio:r.completed?r.improved/r.completed:null,metTarget:r.completed>=design.target};});
 const completed=rows.reduce((s,r)=>s+r.completed,0),ready=rows.every(r=>r.metTarget);
 return {rows,individuals:rows.filter(r=>r.unit==='명').reduce((s,r)=>s+r.completed,0),companies:rows.filter(r=>r.unit==='개사').reduce((s,r)=>s+r.completed,0),decision:!completed?'NOT_RUN':!ready?'INSUFFICIENT':rows.every(r=>r.ratio>.6)?'WITHIN_SAMPLE_CRITERION':'REVISE',populationSuccessProbability:null};
}
export const scoreDimensions=Object.freeze([{name:'개인 유용성',weight:20,baseline:8,target:16},{name:'기업 구매 이유',weight:20,baseline:4,target:16},{name:'출처·유입 연결',weight:15,baseline:3,target:12},{name:'지속 이용 가치',weight:15,baseline:6,target:12},{name:'제작·지원 반복성',weight:15,baseline:3,target:12},{name:'실제 구매·갱신 근거',weight:15,baseline:0,target:0}]);
export function readinessScore(levels){if(!Array.isArray(levels)||levels.length!==scoreDimensions.length||levels.some(x=>!Number.isInteger(x)||x<0||x>5))throw Error('평가 수준은 0~5 정수로 입력하세요.');return scoreDimensions.reduce((sum,d,i)=>sum+d.weight*levels[i]/5,0);}
