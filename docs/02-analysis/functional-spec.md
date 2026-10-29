# 기능명세

> 문서번호: MP-DOC-005 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

아래 경로는 src/main/java/com/moapan 또는 src/main/resources 기준 구현 위치다. 구현 중인 계약이며 검증 결과는 TEST_REPORT·ACCEPTANCE_MATRIX에서 별도로 판정한다.

|FS|REQ|기능|입력|처리·전이|출력·예외|구현|
|---|---|---|---|---|---|---|
|FS-01|03,08|회원가입|username/password·현재 약관 명시 동의|형식 정규화·USER 고정·Argon2id·알 수 없는 필드 거부|최소 계정 DTO;400/409/429|auth/AuthController|
|FS-02|03,08|로그인·CSRF|자격·세션·CSRF|AuthenticationManager 인증 후 UUID principal 저장·session ID 교체·토큰 폐기|계정 DTO;401/403;30분 유휴·12시간 절대|security/SecurityConfig|
|FS-03|03,04|계정 관리|현재 비밀번호|변경·탈퇴 시 DB 세션 무효화·브라우저 설정 정리|200/403;복구 없음|auth/AuthController|
|FS-04|02,09|사이트·주소 분리|site/source ID|영속 FK 연결|사이트 1:N 주소 1:N 기사|site/CatalogRepository|
|FS-05|02,09|사이트 목록|운영 검색·필터·sort/page/size|기본20·최대100·집계|목록/400/403|site/CatalogService|
|FS-06|02|사이트 등록·수정|이름·homepage·version|URL 정규화·중복·버전 충돌|DRAFT/400/409|site/CatalogService|
|FS-07|01,02,06|주소·권리|피드 방식·endpoint·권리·기간·근거|초기 비활성·미검토;주소 변경 시 승인 초기화|주소 DTO/400/409|site/CatalogService|
|FS-08|02,08|점검·미리보기|주소 ID·명시 요청|메타데이터 허용 후 안전한 fetch·최대5·기사 미저장|PASSED/FAILED 또는 미리보기/403/502|source/SafeFetch|
|FS-09|02,09|상태·보관|사이트·주소·동작|사이트 잠금·버전·작업 취소;논리 보관;복원 비활성|새 수집 차단·기존 기사 보존;권리철회 별도 숨김|site/CatalogService|
|FS-10|02,09|즉시 수집|source ID|조건 확인 후 고유 live job 큐·Retry-After 준수|202+ID;상태는 별도 조회|site/AdminController|
|FS-11|01,06|초기 출처|실제 이용조건·feed|공식 근거 확인 후만 승인|출처별 실제 기사와 성공 증거|docs/07-operation/sources.md|
|FS-12|01,10|worker|승인된 주소·job|DNS 고정·TLS·크기·XXE·중복·해시·lease·version|수집 결과·실패·재시도·취소|jobs/Worker|
|FS-13|06|요약|허용 설명문|추출형 문장·최대4·부족시 정보 부족·사실 생성 없음|요약·방식·원문 언어 표시|news/ContentProcessor|
|FS-14|05,06|태그|별칭·입력 근거|경계 매칭·최대6·잠금·병합 canonical ID|태그·신뢰도·이전ID 매핑|news/EditorialService|
|FS-15|08|보안 목표|모든 API|인증·권한·상태·제한|허용 응답 복사는 막는다고 주장하지 않음|security/SecurityConfig|
|FS-16|07,08|브라우저 접근|동일 origin 요청|CSRF·Host/Origin·CSP·안전 DOM|401/403/400/409/429/503|security/SecurityConfig|
|FS-17|04,05,08|edition|인증 계정·size|7일·최대300·공통 고정 순서·현재 공개상태 재검사|API 기본30·최대50;현재 UI42개·6슬라이드|news/NewsService|
|FS-18|08|cursor|서명 토큰|계정·edition·위치·만료·size 검증|400 변조/만료;인증 별도|news/NewsService|
|FS-19|08|quota|호출·반환량|PostgreSQL 원자 카운터·lease·TTL·장애시503|분60·일6000·동시3·429+Retry-After|security/Limits|
|FS-20|04,08|DTO·캐시|기사 상태|공개 필드 DTO·no-store·추적 없음|원문전문·secret·검토메모 제외|news/NewsService|
|FS-21|04,05|로컬 관심사|문장·태그·가중치·제외|IndexedDB UUID namespace·사용자 확인·문장 폐기|서버 비전송·초기화·삭제|static/assets/js/preferences.js|
|FS-22|05|추천|현재 후보·로컬 설정|0.8 match+0.2 freshness;제외 우선;다양성|결정적 순위·일치 태그 이유·로컬 필터|static/assets/js/ranking.js|
|FS-23|07|정적 화면|공통 후보·계정 상태|4/2/1단·신문 위계·접근성·인쇄|로딩·빈결과·오류·직접 원문 링크|static/index.html|
|FS-24|09|관리자|ADMIN 명령|service 권한·기사 편집·숨김·태그·작업·감사|최소 감사·관심사 조회 없음|news/EditorialService|
|FS-25|03,06|안내|서비스 정책|실제 구현과 일치·복구불가·연락처 임의생성 금지|소개·출처·개인정보·약관 초안|static/privacy.html|
|FS-26|01~12|REST 계약|명시된 routes·DTO|인증·권한·오류·필드 일치|API.md·코드·테스트 연결|docs/03-design/api.md|

검증: 단위는 ContentProcessor/SafeFetch, 통합은 PostgreSQL+MockMvc로 인증·관리·quota·worker 상태를, UI는 Playwright Java로 실제 ES Modules·IndexedDB·네트워크와 반응형을 검사한다. 미구현·미검증은 통과로 처리하지 않는다.

2026-10-28 사용자 승인 보완: 가입 시 현재 약관 전문 링크와 미선택 필수 체크박스를 제공한다. 서버도 true 및 현재 버전을 검증하고 버전·시각만 저장한다. 로그인과 가입의 긴 설명은 정책 페이지로 이동했다.

FS-03 계정 관리 화면은 /account.html 및 assets/js/account.js, FS-21 관심사 설정은 기존 /settings.html 및 assets/js/settings.js로 분리했다. 공통 메뉴에 각각 노출한다. 관심 문장 제안은 브라우저의 별칭 사전 매칭이며 LLM 처리나 문장 전송을 하지 않는다.

## FS-21 확장: 기기 간 수동 이전
관심사 화면에서 파일 전용 비밀번호로 내보내기, 같은 계정의 다른 브라우저에서 파일 확인 후 명시적 적용/취소를 지원한다. 입력 문장·로그인 비밀번호·기사·검색 이력은 포함하지 않는다. 자동 동기화는 제공하지 않는다. API/DB 변경 없음. MAX128KiB, v1 고정 암호 형식과 설정 whitelist 검증, 오류 시 기존 설정 유지. 테스트: BrowserTest.encryptedPreferencesTransferBetweenIndependentBrowsers / portableSettingsRejectTamperingOtherAccountsAndInvalidSchemas.

## 공개 관심지면·권한별 메뉴 개정
FS-15/16: 비회원 공개 endpoint 허용, 회원 전용 endpoint401 및 관리자403 유지. settings/account/admin HTML 직접 접근 제한 및 초기 hidden으로 메뉴 노출 방지. 공통 메뉴는 동일 viewer 상태 사용.
FS-17/21/22: 공개42개, 회원 지면42개(내부 후보 최대300개·UI 더보기 없음). 관리자 대표 weights와 version을 DB 저장·ADMIN/CSRF/낙관적 잠금으로 수정. 개인 관심사 없거나 끄면 대표값을 사용하고 제외 설정 유지. 전체/관심 탭 및 일반 최신 구역 제거.
FS-23: native dialog 검색, 검색어/날짜/카테고리 적용·취소·Escape·초기화. 검색 실행과 페이지 이동 시에만 서버 일시 처리. 검색 기록 저장 없음.
FS-25: 공식 개시 전 사용자 요청으로 현재 이용약관·개인정보를 최초 제1판으로 표시한다. 신규 동의 ID는 terms-1. 공개 이력 페이지·과거 본문·링크는 제거하고 기존 회원 동의 ID/시각은 변경하지 않는다.
FS-23 최신 화면: 상단 MoA-PaN과 config.js의 mastheadQuote를 표시한다. 검색 버튼은 상단 우측이며 중간 지면명/대표값 설명/수량 줄은 제거했다. 기사 스크랩 갤러리는4/2/1단으로 DOM 순서대로 읽으며 인쇄 시 장식을 제거한다.
