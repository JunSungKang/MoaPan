# 계정별 뉴스레터 구독 권한 설계서

- 문서번호: MP-DOC-035
- 기준: 승인된 계정별 구독/404 설계. Sprint Delivery. 가격·실결제·자동청구는 제외.

## 데이터·도메인
V23 account_subscriptions는 users와1:0..1. account_id PK/FK(계정 삭제만 cascade), status ACTIVE/REVOKED, starts_at/expires_at timestamptz, version>0,updated_at. ACTIVE는종료>시작 CHECK. V24는REVOKED의시작전취소를허용하고기존회수종료를기록된updated_at으로보정한다. 변경된행만version을증가시켜이전관리자입력은409로거부한다. 기본행/기존계정 backfill 없음. 구독 없음은권한없음. ACTIVE이고시작≤요청현재<종료이고계정활성일때만 READ_NEWSLETTER/REUSE_NEWSLETTER/RESEARCH_WORKSHEET 가능. 기존 EntitlementPolicy/EntitlementProvider port를 사용한다. DatabaseEntitlements는매번PK조회하고 세션·Caffeine에권한을보관하지않는다. managed모드만지원, preview/paid는시작실패. 기간은 UTC저장·관리자입력/표시는Asia/Seoul.

## 서버 경계·404
Boundary는세션유효성검사후 newsletter.html 및 /api/v1/newsletters와하위경로를 검사한다. 비로그인·미구독·예정·만료·회수·삭제/비활성계정은공통404를반환한다. GET/HEAD·query/conditional에도적용한다. 뉴스레터 서비스계층 ContentAccess.require도발간캐시조회전에검사한다. 불명UUID와권한거절은동일not_found 응답. 브라우저Accept:text/html에는공통404정적문서, API에는JSON. /404.html 직접접속도404이며200/302가아니다. 없는HTML도같은문서다. 구독HTML/API/404는no-store이며기존정적asset캐시는유지한다. CSRF403·관리자권한·다른기능401·요청제한·DB실패503은의도한경계를유지한다. 결제완료/구독필요등거절사유는응답에넣지않는다. DB조회실패는허용하지않으며404로장애를숨기지도않는다.

## 관리자 운영
POST /api/v1/admin/subscriptions/search는username,page,size,sort를본문으로받고최대100(기본10),hasNext를반환한다. sort 기본 RECENT는회수하지않은구독의최근부여·연장(updated_at)순,다음회수계정,미구독순이다. EXPIRING은현재실제이용중인활성계정을종료일오름차순으로우선하고나머지는최근변경순으로이어준다. 동률은username/id로고정하며임의정렬문은400으로거부한다. 검색어를URL/로그/보관소에저장하지않는다. 응답은계정ID·아이디·역할·활성·구독상태/기간/version만,hash/세션/약관정보없음. PUT /api/v1/admin/subscriptions/{id}에version,startsAt,expiresAt를보낸다. POST .../{id}/revoke는version을받는다. Controller와서비스의ADMIN·CSRF,계정행잠금(최초구독경합포함),현재version검사409·원자적쓰기와감사(admin UUID/대상UUID/행위/시각)로처리한다. 회수는계정잠금/version검증후서버현재시각을microsecond정밀도로확정하고expires_at/updated_at/회수감사created_at에동일저장한다. 이미회수된행의현재version요청은기간/version/감사를더바꾸지않으며구version은409다. 시작예정일은보존하므로시작전회수는REVOKED의종료가시작보다빠를수있다. 재부여시에는다시유효한ACTIVE기간을검사한다. 권한행은계정삭제시삭제된다. 일반가입·회원요청은권한필드를받지않는다.

관리화면은구독탭첫진입에만자동조회하며기본최근구독·연장순10명이다. 표시인원10/30/50/100명과만료임박순을선택하면별도조회클릭없이첫페이지부터다시조회한다. 미구독계정도검색/선택할수있다. 늦은조회는AbortController와요청세대로취소·무시하며,저장중조건변경을차단한다. 이후탭왕복은편집입력을보존하고자동재조회하지않는다.

## 프런트엔드·운영 의미
현재계정DTO의canReadNewsletter는서버계산힌트이며최종권한은서버다. 메뉴/모든직접뉴스레터링크/메인teaser는이힌트가true일때만표시/요청한다. 권한없는직접접속은HTML을먼저내려주지않는다. 이미열린화면에서실패하면404문서로이동해본문을정리한다. 관리화면구독권한탭은아이디검색→계정선택→시작/종료입력→저장/회수이며UUID를입력시키지않는다. USER/ADMIN은운영역할로상품권한을우회하지않는다. 관리자는기존관리경로에서집필/미리보기할수있다. 수동열람권부여는실결제증거가아니며발간물의FREE_PREVIEW/PAID_SUBSCRIPTION 표시와결합하지않는다. 신규발간은기존DB기본표시,기존표시는재발간에도보존한다.

## 동시성·한계
회수commit후시작한요청부터차단한다. 이미전송된화면·복사본을회수하는DRM이아니다. URL/JS/과거링크에서기능존재가알려질수있으므로404만으로보안을보장하지않는다. 서버공통캐시내용은재사용하되구독판정은항상외부에둔다. 회원관심사·행위·결제/카드자료를새로수집하지않는다.

## 검증 연결
EntitlementPolicyTest,SubscriptionAccessIntegrationTest,SecurityIntegrationTest,PublicationCacheIntegrationTest,SubscriptionBrowserTest,SiteDesignAuditTest,StaticMetadataTest 및 전체보안검사를수행한다. 실제결과는MP-DOC-036을따른다. 개발후가드PR/독립리뷰까지마감하고develop병합은별도승인.

공식 근거: [RFC9110 403/404](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.4), [OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).
