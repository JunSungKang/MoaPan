# REST API 계약

동일 origin의 JSON API. 알려지지 않은 JSON 필드는400, 인증401, 권한/CSRF403, 입력400, 상태/버전/중복409, 제한429+Retry-After, DB/제한 확인 장애503. 상태 변경은 `X-CSRF-TOKEN` 필수. 쿠키는 HttpOnly·SameSite=Strict, 운영 Secure. 응답은 no-store. SQL·스택·요청 본문을 오류로 반환하지 않는다.

## 인증·계정

|Method|경로|입력·출력|
|---|---|---|
|GET|/api/v1/auth/csrf|token,headerName. JS 메모리만 저장|
|POST|/api/v1/auth/signup|username/password, termsAccepted=true, termsVersion=terms-1. 반환 id,username,role(USER)|
|POST|/api/v1/auth/login|username/password. 세션 ID 교체, 최소 계정 반환. 후속 GET csrf로 토큰 재발급|
|POST|/api/v1/auth/logout|서버 세션 종료. 후속 로그인 전 csrf 재발급|
|GET|/api/v1/account|id,username,role|
|PATCH|/api/v1/account/password|currentPassword,newPassword. 모든 기존 세션 종료, 재로그인 필요|
|DELETE|/api/v1/account|currentPassword. 계정·세션·연결 카운터 삭제|

username은 ASCII 영문·숫자·밑줄1~128, 대문자를 소문자로 정규화. 비밀번호는6~128 Unicode code point, trim/절단하지 않는다. 이메일·role 등 추가 필드 금지. 가입 전 비밀번호 분실 복구 불가 안내.

## 회원 뉴스

|Method|경로|계약|
|---|---|---|
|GET|/api/v1/news/edition?size=30|size1~50만. 다른 query parameter·중복 parameter 거부|
|GET|/api/v1/news/page?cursor=...|서명·계정·edition·위치·size·만료 검증. cursor 이외 query 거부|
|GET|/api/v1/tags|tags(id,slug,name,category),aliases(alias,tagId),redirects(oldId,canonicalId)|
|GET|/api/v1/sources|공개 허용 출처 최소 메타데이터 id,label,language,site,termsUrl|

뉴스 응답: editionId,editionAt,candidateCount,periodDays(7),items,nextCursor. 모든 회원에게 공통으로 최대300개 후보를 선정한다. 진입은 기본30개만 내려받고 다음 페이지는 사용자의 더보기 요청에 따른다.

기사 DTO: id,title,summaryBullets,source(id,name,site,language),publishedAt(null 가능),collectedAt,tags(id,slug,name,category,confidence),originalUrl,summaryMode. 발행시각이 없으면 수집시각임을 표시한다. 자동 요약 모드는 EXTRACTIVE_ORIGINAL_LANGUAGE 또는 INSUFFICIENT, 수동 MANUAL. 본문 전문·회원 관심사·내부 메모·secret은 없다. 숨김·권리 철회는 기존 edition에서도 매 응답에 재검사한다.

계정당 분당60회, 일일 반환 항목6000개(동일 기사 반복 포함), 동시 요청3개. 재로그인은 quota를 초기화하지 않는다. 계정별 기사 ID 접근 이력은 저장하지 않는다.

## 관리자 사이트·주소

모든 /api/v1/admin/**는 ADMIN과 service 권한 검사. 등록/수정은 JSON 입력 DTO. 사이트와 주소 응답은 DB 컬럼명 snake_case, version 포함이며 회원 공개 DTO와 구분한다.

- GET/POST `/api/v1/admin/sites`: 목록/등록. GET의 search,status,result,sort(name/success),page(기본0),size(기본20·최대100). status 빈 값은 보관 제외, result SUCCEEDED/FAILED/빈 값. 입력 name,homepageUrl,description,adminNote. 새 사이트 DRAFT.
- GET/PATCH/DELETE `/api/v1/admin/sites/{id}`: 상세/수정/ARCHIVED. PATCH는 전체 편집 필드와 현재 version 필요. 충돌409. DELETE는 cascade 삭제하지 않는다.
- POST `/api/v1/admin/sites/{id}/activate|pause|restore`: ACTIVE/PAUSED/PAUSED. 실행 가능한 주소 없으면 활성화409. 복원은 사용/기술/권리 재확인 필요.
- GET/POST `/api/v1/admin/sites/{id}/sources`: 소속 주소 목록/추가. 신규는 enabled=false,UNREVIEWED,NOT_TESTED. 권리 입력을 신규 등록 시 자동 승인하지 않으며 저장 후 명시적으로 수정한다.
- GET/PATCH/DELETE `/api/v1/admin/sources/{id}`: 상세/수정/논리 보관. GET은 publishedArticleCount로 권리 철회 영향 기사 수를 안내한다.
- POST `/api/v1/admin/sources/{id}/restore|validate|preview|collect`: 복원/기술 점검/최대5개 미저장 미리보기/202+job ID.
- GET `/api/v1/admin/sources/{id}/runs`: 최근50개 실행 결과. SUCCEEDED/NO_CHANGE/FAILED와 작업 접수를 구분한다.
- GET `/api/v1/admin/jobs/{id}`: QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED,attempts,시각,정제 오류 코드.
- GET `/api/v1/admin/workers`: worker ID·heartbeat.

주소 입력: label,type(RSS/ATOM/JSON_API),endpointUrl,language,intervalSeconds(기본900·300~86400),enabled,rightsStatus,allowMetadata,allowDisplay,allowSummary,allowBody,allowStore,allowTranslation,allowImage,allowExternalAi,termsUrl,termsCheckedOn,reviewNote,jsonMapping,secretRef,version. body/store/translation/image/externalAI의 true는 V1 미지원400. 승인·제한 권리는 조건 URL·확인일 필요. endpoint/type/secret 참조 변경 시 비활성·권리/기술 초기화. 승인된 endpoint의 공개 HTTPS hostname만 접근하고 secret은 서버 환경의 MOAPAN_SOURCE_* 참조로 Bearer header에만 사용한다. 값은 응답하지 않는다.

JSON_API는 단일 GET 응답의 제한된 점 구분 경로만 지원: items,id,title,url,publishedAt,description. 최대100개 처리·응답2MiB, 페이지네이션 코드를 실행하지 않는다. 필요 시 별도 검토 후 제한된 페이지네이션을 확장해야 하며 임의 표현식은 금지한다. 사이트별 코드 추가·재배포 없이 이 범위의 표준 source를 등록할 수 있다.

## 관리자 편집

- GET `/api/v1/admin/articles`: 최근100개 최소 편집 필드.
- PATCH `/api/v1/admin/articles/{id}`: summaryBullets(0~4·각350자 이하),summaryLocked,tagsLocked,published,tagIds(최대6). 수동 방식 표시·감사 기록. 공개 여부와 별개로 source 권리는 응답 시 검사한다.
- POST `/api/v1/admin/tags`: slug,name,category.
- PATCH `/api/v1/admin/tags/{id}`: slug,name,category 수정. 비활성 태그404.
- POST `/api/v1/admin/tags/{id}/aliases`: alias.
- POST `/api/v1/admin/tags/{id}/merge`: canonicalId. 이전 태그 비활성·기사 연결/별칭/redirect를 canonical로 정규화.

내부 /internal/live, /internal/ready는 요약 상태만. Caddy는 /internal/*와 /actuator/*를 외부에404로 차단한다. worker/migrate/admin-cli는 HTTP를 열지 않는다.

회원 개인 관심사 저장·클릭 추적·전체 export·원문 전문 프록시·미인증 뉴스 dump API는 만들지 않는다.

가입 약관 동의: termsAccepted 누락·false·null, termsVersion 누락·현재 버전 불일치는400이다. 동의 버전과 서버 시각을 계정과 함께 저장하며 공개 계정 DTO에 추가 노출하지 않는다. 로그인 DTO에는 이 필드를 받지 않는다. 기존 계정의 동의 이력은 소급 생성하지 않는다.

로그인 실패1~4회:401 invalid_credentials.5~9회:같은401 응답에 attemptsRemaining(5~1) 추가.10회부터429 login_limited와 실제 DB 제한 만료까지의 Retry-After(초)를 반환한다. 전역 로그인 횟수 제한은429 rate_limited로 구분한다. 존재하지 않는 아이디도 같은 실패 카운터 정책을 따른다. 제한된 요청은 만료를 연장하지 않는다.

## 공개 지면과 대표 관심사 (최신 사용자 변경)
GET /api/v1/public/news/edition: 비회원 허용, query 불허, 최근 공통 edition에서 최대30개, cursor 없음. 공개 상태·출처 권리·요약 허용 여부 재검사. 전체 공개 요청 분당600, 기존 전체 API 분당3000 카운터/실패 시503 유지. IP·기기 식별자 추가 없음.
GET /api/v1/public/interests: version,weights(UUID:1~5). 개인 설정이 아닌 관리자 공통 대표값.
GET /api/v1/public/tags: 공개 태그·별칭·병합 매핑.
GET /api/v1/admin/interests 및 PUT /api/v1/admin/interests: ADMIN; PUT은 CSRF, {version,weights}필수, 활성 태그1~30개, 가중치1~5, version 불일치409. 감사 action은 representative_interests이며 개인 관심사 API는 아님.
/settings.html,/account.html은 인증 필요, /admin.html은 ADMIN. 비회원 HTML 요청은 /login.html로, 일반회원의 admin.html은 /index.html로 이동한다. API는 기존401/403 JSON 유지.

## 수집 필터 확장
SourceInput에 publishedSince(YYYY-MM-DD 또는null), topicTerms(최대40개·각1~100자)를 추가한다. 제목/설명에 최소1개 키워드가 일치하면 통과(빈 목록은 주제 제한 없음). 해당 시작일 한국시간00:00 포함. 시작일이 있으면 미상 발행일 거부. 미리보기·worker 저장에 동일 필터. PATCH는 전체 설정을 보내야 하며 필터 변경 시 conditional request 캐시를 지우고 source version/작업 취소 규약 유지. 기존 저장 기사를 자동 삭제하지 않는다. JSON_API items 매핑은 $로 루트 배열을 지원한다. arXiv 공식 http://arxiv.org/abs/ 링크만 https로 정규화하며 다른 HTTP URL 허용은 하지 않는다. arXiv admission 충돌은 재시도, DB 게이트 장애는503/실패 처리하며 무제한 허용하지 않는다.
