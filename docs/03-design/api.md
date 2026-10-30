# REST API 계약

> 문서번호: MP-DOC-008 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

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

뉴스 응답: editionId,editionAt,candidateCount,periodDays(7),items,nextCursor. 모든 회원에게 공통으로 최대300개 후보를 선정한다. API 기본 size는30·최대50이나 현재 웹 지면은 size=42를 한 번 요청해7개씩6슬라이드만 표시한다. 내부 후보300개/cursor 계약은 유지하지만 UI 더보기는 사용하지 않는다. 추가 기사는 별도 검색(최대140개)에서 조회한다.

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

주소 입력: label,type(RSS/ATOM/JSON_API),endpointUrl,language,intervalSeconds(기본900·300~86400),enabled,rightsStatus,allowMetadata,allowDisplay,allowSummary,allowBody,allowStore,allowTranslation,allowImage,allowExternalAi,termsUrl,termsCheckedOn,reviewNote,jsonMapping,secretRef,version. body/store/image/externalAI의 true는 V1 미지원400. allowTranslation은 권리 검토 후 로컬 제목·요약 번역만 허용. 승인·제한 권리는 조건 URL·확인일 필요. endpoint/type/secret 참조 변경 시 비활성·권리/기술 초기화. 승인된 endpoint의 공개 HTTPS hostname만 접근하고 secret은 서버 환경의 MOAPAN_SOURCE_* 참조로 Bearer header에만 사용한다. 값은 응답하지 않는다.

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
GET /api/v1/public/news/edition: 인증 필수인 호환 경로, query 불허, 최근 공통 edition에서 최대42개. 회원 cursor와 계정 quota를 동일 적용한다. 공개 상태·출처 권리·요약 허용 여부 재검사. 계정 분당60/일일6000항목/동시3 및 전체 API분당3000 제한과 실패 시503을 적용한다. IP·기기 식별자 추가 없음.
GET /api/v1/public/interests: version,weights(UUID:1~5). 개인 설정이 아닌 관리자 공통 대표값.
GET /api/v1/public/tags: 공개 태그·별칭·병합 매핑.
GET /api/v1/admin/interests 및 PUT /api/v1/admin/interests: ADMIN; PUT은 CSRF, {version,weights}필수, 활성 태그1~30개, 가중치1~5, version 불일치409. 감사 action은 representative_interests이며 개인 관심사 API는 아님.
/settings.html,/account.html은 인증 필요, /admin.html은 ADMIN. 비회원 HTML 요청은 /login.html로, 일반회원의 admin.html은 /index.html로 이동한다. API는 기존401/403 JSON 유지.

## 수집 필터 확장
SourceInput에 publishedSince(YYYY-MM-DD 또는null), topicTerms(최대40개·각1~100자)를 추가한다. 제목/설명에 최소1개 키워드가 일치하면 통과(빈 목록은 주제 제한 없음). 해당 시작일 한국시간00:00 포함. 시작일이 있으면 미상 발행일 거부. 미리보기·worker 저장에 동일 필터. PATCH는 전체 설정을 보내야 하며 필터 변경 시 conditional request 캐시를 지우고 source version/작업 취소 규약 유지. 기존 저장 기사를 자동 삭제하지 않는다. JSON_API items 매핑은 $로 루트 배열을 지원한다. arXiv 공식 http://arxiv.org/abs/ 링크만 https로 정규화하며 다른 HTTP URL 허용은 하지 않는다. arXiv admission 충돌은 재시도, DB 게이트 장애는503/실패 처리하며 무제한 허용하지 않는다.

### 카드 표시 개정
뉴스 DTO에 `author` 문자열(출처 제공 작성자, 최대 2,000자)을 추가한다. 미제공은 빈 문자열이며 UI에서 `정보 없음`으로 표시한다. `publishedAt`이 없으면 작성일도 `정보 없음`이다. 수집시각으로 작성일을 대체하지 않는다. 제목은 원문 링크이며 카드에는 작성일·작성자·출처·태그·R&D 중심 추출 요약만 표시한다. JSON_API 매핑에 선택 `author` 경로를 지원한다.

## 승인된 서버 기사 검색
POST /api/v1/public/news/search와 POST /api/v1/news/search 모두 회원 인증 필수. 두 경로 모두 CSRF 필수, 동일 origin, no-store. 공개 경로도 인증된 회원이면 계정 quota 적용.
JSON 입력: search(필수 문자열, 최대100자), category(필수 문자열, 최대80자), day(null 또는 YYYY-MM-DD, KST 기준), page(0..19). 빈 검색/카테고리는 전체 공개자료. size/offset/추가 필드/URL 파라미터 거부.
응답: items(공개 ArticleView 최대7개), page, pages(최대20), total(최대140). 최근7일/지면42개 밖의 저장 공개기사도 포함한다. 발행시각 또는 수집시각 내림차순/ID로 정렬하며 미래자료 제외. 개인 관심사/제외조건은 적용하지 않는다. 제목·표시 허용 요약·활성 태그의 문자 일치 검색이며 SQL 와일드카드를 문자로 취급한다. 카테고리는 정확 일치.
공개 권리와 공개상태를 매 요청 재확인한다. 요청 하나 안의 count/items는 repeatable-read. 페이지 간 새 수집·숨김·정정으로 결과가 달라질 수 있으며 검색 스냅샷이나 검색어를 영속 보관하지 않는다.
검색 전체 서비스120회/분, 회원 기존60회/분·동시3회·6000항목/일도 적용. 장애 시503, 한도429+Retry-After. 400 입력, 403 CSRF, 회원경로401. 검색어는 요청 처리 메모리 외 저장·로깅·외부 전송하지 않는다.

## 카드 번역
뉴스 DTO는 translations: {ko|en: {title,summaryBullets}}를 추가한다. 현재 입력 hash·공개 상태·출처 display/summary/translation 권리를 확인한 저장 결과만 포함한다. 원문 title/summaryBullets는 보존하며 provider·내부 해시는 공개하지 않는다. 번역이 없거나 유효하지 않으면 원문 표시. 검색은 허용된 저장 번역도 검색하며 검색어는 보관하지 않는다.


### 관리자 번역 상태
GET /api/v1/admin/translations (ADMIN): runtime(state,failures,next_probe_at,checked_at,error_code), counts(state,count), failures(error_code,count), recent(최근20개 article_id,state,error_code,attempts,infra_failures,updated_at). 회원정보·원문·실패 번역 전문·secret 제외. 관리자 화면 새로고침으로 관측하며 작업 승인 없이 자동 처리한다.

관리 기사 편집: GET /api/v1/admin/articles의 tag_ids는 현재 태그 UUID JSON 문자열이다. PATCH tagIds=null/미지정은 기존 연결을 보존하고, []는 명시적 전체 제거, UUID 목록은 교체한다.

## 분류 운영
ADMIN GET `/api/v1/admin/classification`: policy/catalog(version/topics/hash), 상태별 counts, runtime, 최근20개 작업. ADMIN+CSRF PUT `/api/v1/admin/classification/topics`: `{version,topics:[{tagId,definition}]}`. 1~8개/중복ID 금지/활성태그/정의1~200자. 400 입력오류,409 version충돌. 뉴스 응답 필드는 변경하지 않는다.

로그인 JSON은 username/password와 선택 Boolean rememberMe를 받는다. true일 때 현재 브라우저 SESSION을 최대30일 보관하고, 기본 false는30분유휴/12시간절대다. / 및 /index.html도 인증 필수이며 미로그인은 /login.html로 이동한다. 공개 sitemap은 /about.html,/privacy.html,/terms.html만 제공한다.

## 관리자 광고 설정
GET /api/v1/admin/advertisements 및 PUT 동일 경로: ADMIN만 허용하고 PUT에는 CSRF가 필요하다. 입력/관리응답은 version, enabled, provider(BANNER/ADSENSE), title(80), description(160), targetUrl(2048), imagePath(240), publisherId, slotId다. provider의 모든 ID는 공개 식별자이며 비밀번호/API키 입력 기능이 아니다.
HTTPS 목적지·서버 /assets/images/의 PNG/JPEG/WebP/GIF 파일만 허용한다. 잘못된 값400, 권한403, version 충돌409, 실제 연결 전 ADSENSE 활성화409(adsense_connection_required). 임의 HTML/JS/광고 스크립트는 입력받지 않는다.
GET /api/v1/advertisements: 회원만 접근. 활성 BANNER의 enabled/title/description/targetUrl/imagePath만 반환하고 비활성·ADSENSE 준비 상태는 enabled=false와 빈 표시값을 반환한다. 모든 응답은 no-store. 모아판의 클릭 추적/집계/중계 API는 없다.

뉴스레터 추가(승인 범위): 회원 GET /api/v1/newsletters 및 /{id}는 발간본만 조회한다. ADMIN GET /api/v1/admin/newsletters 및 /{id}, PUT /{id}(version/manuscript), POST /{id}/publication(version/visible). 상태 변경에는 CSRF, 서비스 권한과 version을 적용한다. 원고는 title/introduction/topic/blocks이며 블록은 paragraph/heading/quote/image/reference다. 이미지는 브라우저에서 JPEG 압축 후 DB의 원고와 발간 스냅샷에 포함하므로 기존 DB volume/암호화 백업이 보존한다. 미리보기나 초안 저장은 발간본을 변경하지 않는다. PUT 원고만 최대1MiB, 나머지 API16KiB. 프록시는1MB 상한, 최종 앱 경계에서 경로별 제한을 강제한다.
