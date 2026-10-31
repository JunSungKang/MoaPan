# 데이터 모델

> 문서번호: MP-DOC-007 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

SQL 진실 원천: src/main/resources/db/migration/V1__schema.sql, V2__tags.sql.

users: UUID·정규화 아이디·해시·활성·권한만. spring_session/attributes: 세션 운영. auth_rate_limits/api_quotas/api_request_leases: 짧은 목적별 집계와 동시 요청 lease.

sites 1:N sources 1:N articles. sites와 sources는 상태·권리·기술점검·version을 분리한다. source GUID와 canonical URL 각각 고유 제약으로 중복을 막고 article_revisions는 입력 해시 변경 이력을 보관한다. 태그는 aliases·redirects·article_tags로 연결한다. edition items는 공통 위치를 고정한다. content_jobs는 source별 활성 작업 1개, lease 선점과 재시도를 관리한다. fetch_runs·worker_status·admin_actions는 운영 최소 정보다.

사이트·주소·기사·작업 간 FK는 보관 시 cascade 삭제하지 않는다. 계정 삭제에 연결된 세션은 명시 삭제하고 quota는 FK cascade로 삭제한다. 만료 세션·카운터는 짧게, 오래된 edition은 만료 후1일, 완료 작업·fetch 이력은30일로 정리한다. 회원별 기사 행위 테이블은 생성하지 않는다.

## 가입 약관 기록 (V3)
users.terms_version varchar(32), terms_accepted_at timestamptz. 둘 다 NULL이거나 둘 다 값이 있어야 한다. 기존 회원은 NULL 유지(과거 동의 추정 금지), 신규 가입은 서버가 현재 버전 및 현재 시각을 저장한다. 탈퇴 시 계정과 함께 삭제한다. 동의용 IP·UA·기기 정보를 추가하지 않는다.

V4: users.username을 varchar(128)로 확대하고 CHECK를 영문 소문자·숫자·밑줄1~128자로 변경했다. 기존 계정·고유성·식별자·동의 기록을 보존하며 기존 마이그레이션 파일은 수정하지 않는다.

V5 representative_interests: singleton boolean PK/check true, weights jsonb object, version bigint>=0. 회원 FK/개인 관심사 없음. 활성 태그 검증·최대30개·가중치1~5는 service에서 강제하며 ADMIN 갱신은 version 조건 UPDATE. 기존 계정/동의 데이터 보존.

V6: sources.published_since(date), topic_terms(jsonb array max40); source_access_gate는 arXiv 공개 요청의 공유 간격/동시성 제어용 단일행이며 회원 데이터와 무관하다.

V7: `articles.author varchar(2000) NOT NULL DEFAULT ''` 추가. 회원 프로필이 아닌 공개 기사 저작자 메타데이터이며 기존 계정 정보와 연결하지 않는다. `processing_version=research-v2`는 연구 관점 문장 우선 추출을 나타내고 재수집 시 이전 버전도 재처리한다. 수동 요약 잠금은 보존한다.

## 기사 원문 미보관
원문·피드 원본·설명문·초록을 별도 DB 칼럼, 파일 캐시, 작업 payload, 수정 이력에 저장하지 않는다. SafeFetch의 응답과 Item.description은 처리 중 메모리 입력이며 Worker는 요약과 메타데이터/링크/해시만 저장한다. 본문 웹페이지/PDF를 별도로 가져오는 기능은 없고 allowBody/allowStore 요청은 서버에서 거부한다. 입력 문자열은 처리가 끝나면 별도 보관하지 않으며 JVM 메모리의 즉시 물리 소거를 보장한다는 의미는 아니다. 기사 수정 이력은 이전 입력 해시만 보관한다.

V8: article_translations는 기사별 대상 언어·현재 제목/요약 hash·번역 제목/요약·provider만 저장한다. article_translation_jobs는 ID·시도 횟수·lease·정제된 오류만 저장하며 원문 payload가 없다. 원본 제목/요약 변경 trigger가 번역과 대기 상태를 무효화한다. 최대3회 재시도,10분 lease/backoff, SKIP LOCKED 선점. 권리 철회 후 응답/저장 거부.


### V9 번역 자동화
article_translation_jobs: state,policy_version,progress(jsonb 검증된 번역 checkpoint),field_attempts,updated_at,infra_failures. PENDING/RUNNING/RETRY/DONE/ORIGINAL/CANCELLED. translation_runtime singleton은 readiness/backoff 집계만 저장한다. checkpoint는 종결 시 삭제한다. V9는 기존 파생 번역만 재검사 목적으로 삭제하며 기사·회원은 유지한다.

V10: classification_config(낙관적 version), classification_topics(기존 tag_id와 정의), article_classification_jobs(기사ID/입력hash/정책hash/상태/lease/최대3시도), classification_runtime(전역 추론 lease). 입력 전문·모델 출력·회원정보는 저장하지 않는다. articles 제목/요약 변경 trigger는 분류 결과를 무효화한다. article_tags.method=LLM은 고정 목록 선택 결과이며 confidence=1은 확률 추정이 아니다.

advertising_settings: singleton CHECK(true) PK, provider BANNER/ADSENSE, enabled, title/description/target_url/image_path/publisher_id/slot_id, version. V12에서 비활성 BANNER 1행 생성. 활성은 BANNER만 가능하며 version 조건 UPDATE와 최소 admin_actions 감사를 한 트랜잭션에서 처리한다. 회원별 광고·클릭 데이터는 생성하지 않는다.

newsletters(V13): UUID、issue_number(고유 호수)、draft/published JSONB、version、published_at/updated_at. 발간 스냅샷은 초안 저장과 별개로 한 트랜잭션에서 교체한다. 발간 취소는 published만 비우고 초안과 호수는 유지한다. 자체 원고와 압축 이미지가 같은 스냅샷에 있으므로 파일 이동·볼륨 누락으로 그림이 사라지지 않으며 기존 PostgreSQL 백업이 함께 보관한다. 뉴스 원문·회원 관심사·열람 이벤트를 저장하지 않는다.

## V14 뉴스레터 연구·개정
newsletters에 draft_dossier/published_dossier(JSONB nullable), publication_revision(nonnegative), first_published_at(timestamptz), distribution(FREE_PREVIEW/PAID_SUBSCRIPTION)을 추가했다. 기존 발간본문/원고/호수/시각은 보존하고 최초시각·개정1로 기준선을 잡는다. 새마이그레이션은additive이며원래V13데이터를삭제하지않는다.
dossier={weekStart,cutoff,articleIds[UUID],forecasts[{question,observation,outlook,horizon,experiment,successCriteria,stopCondition,evidenceIds[UUID]}],rightsConfirmed,editorialReviewed,correctionNote}. 기사ID와편집부의독자적연구메모이며기사원문/초록/회원개인정보가아니다. 최대40근거/8전망,필드별길이제한. publication은draft본문+dossier를같은트랜잭션에snapshot하고 개정수/시각/감사갱신, 최초호수/시각은유지한다.
저장·삭제·발간은동일newsletter UUID advisory lock, 선택source→article 공유잠금과권리재검사로오래된공개를방지한다. 숨김/권리철회된근거는독자목록·본문·출력에서다시검사한다. 실제구독·기업·평가참가자용테이블은만들지않았다.


## V15 인증 버전
users.auth_version bigint NOT NULL DEFAULT0 CHECK>=0. 비밀번호 변경은 현재 UUID/version 조건부 UPDATE로 증가시키고 세션을 삭제한다. 탈퇴는 현재 UUID/version 조건부 DELETE다. 로그인은 인증 전 UUID+version을 함께 보관하고 인증 후 동일 UUID와 현재 DB version을 재검사한다. 세션에는 MOAPAN_CREDENTIAL_VERSION 숫자만 추가하고 요청마다 계정 버전과 비교한다. 늦은 세션 저장·동일아이디 재가입의 이전 인증을 거부한다. 기존 회원·암호해시·아이디는 유지되며 기존 version 속성이 없는 로그인 세션은 재로그인이 필요하다.
