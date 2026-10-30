# 실제 검증 결과

> 문서번호: MP-DOC-013 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

최종 실행 시각·JUnit 집계·이미지 식별값·실제 수집은 [검증 요약](evidence/verification.json)에 기록한다. raw scanner/JUnit/스크린샷은 로컬 `build/reports`와 `build/test-results`, 명령 로그는 `.local/evidence`에 있으며 Git에 비밀값·원문 전문·DB dump를 넣지 않는다.

실행 명령:

```sh
sh scripts/build/gradle-local.sh --no-daemon --max-workers=2 clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest
node --test scripts/governance/github-protection-bootstrap.test.mjs
node scripts/test/local-smoke.mjs
sh scripts/security/image-scan.sh moapan-local:1.0.0 moapan-proxy-local:2.11.7 moapan-db-local:18.6 moapan-build:1.0.0 moapan-proxy-build:2.11.7
```

|범위|검증 내용|증빙|
|---|---|---|
|단위|정확한 Temurin 런타임·truststore, URL 정규화·IPv4/IPv6 거부, XXE·크기·HTML 제거·추출 요약·태그 경계|ContentTest, RuntimeSecurityTest|
|PostgreSQL 통합|실제 Flyway 빈 DB, CSRF·과다 필드·Argon2id·UUID 세션 principal·session ID 교체·30분/12시간 만료·비활성·변경·탈퇴|SecurityIntegrationTest|
|인증·조회 제한|401·ADMIN403·size/cursor 변조·타 계정·만료·숨김/권리 변경 반영, 두 Limits 인스턴스의 원자 일일6000 경쟁·동시3, 보안 카운터10000 용량 제한, 실제 DB 중단 후503|SecurityIntegrationTest|
|수집 운영|초안 활성화 거부·409·중지 후 늦은 반영 차단·보관/복원·preview 미저장·주소 변경 승인 초기화·중복/변경/수동 잠금·lease 회복·429 Retry-After/최대 시도·TTL 정리|SecurityIntegrationTest|
|브라우저|실제 ES Modules/IndexedDB 계정 namespace·고정 clock 순위·제외/별칭/병합·문장 폐기·관심사 네트워크 비전송·390/768/1440px·긴 제목·인쇄·로딩/빈결과/오류·키보드|BrowserTest, 합성 스크린샷|
|실제 데이터|NASA/NOAA HTTPS 기술 점검·preview·worker job·영속 기사·edition 응답, 합성 관리자 계정 삭제|local-smoke.mjs|
|Compose|db healthy·migrate exit0·web healthy·worker heartbeat·proxy200·비회원 news401·DB/web host 포트 비공개|실제 Compose와 SQL 조회|
|영속·백업|컨테이너 재생성 후20기사 유지, 새 볼륨에 암호화 dump 복원, 격리 복원 articles=20/active_accounts=0/sessions=0 후 격리 DB 제거|backup.sh, restore-check.sh|
|보안|Trivy 원본 소스/전체 lock·runtime SBOM·실제 JAR 인식91패키지·실행/빌드 이미지·secret/config 검사, 공식 공지·KEV 대조|SECURITY_REVIEW.md|
|TLS|호스트와 실제 실행 이미지의 기본 truststore·일반 HTTPS 성공·공급자 연결된14개 검증 호스트 모두 SSLHandshakeException 거부|scripts/security/TrustAudit.java|
|Git 보호|최초 보호 설정 실행기9테스트·서버 보호 read-back·guard|bootstrap-tests.log, guarded wrapper|

재현 중 발견한 실패는 수정 후 재검증했다: Spring JDBC Instant 바인딩, DB 프록시 필드 접근, 세션 fixture 만료 시각, safe DNS CIDR 오차, JAR scanner 미인식, Caddy/DB 이미지 취약 구성요소, DB 장애 Retry-After 누락. 실패 기록을 성공으로 합산하거나 테스트 기준을 낮추지 않았다.

추가 실제 시험: 두 web 컨테이너에 번갈아 요청하여 계정당60회 이후 양쪽429와 재로그인 후 한도 유지를 확인했다. 실행 중 worker를 SIGKILL하고 재시작하여 실제2분 lease 만료 후 attempt2 성공·기사20건 보존·오래된 RUNNING fetch 이력 정리를 확인했다. 각각 multi-web-quota-check.mjs와 worker-recovery-check.mjs로 재현한다.

검증 한계: 장시간 부하와 모든 네트워크 timeout/5xx/리다이렉트 조합의 연결 수준 fault injection은 수행하지 않았다. 재시도 통합 테스트의503·timeout은 수집 계층 오류 fixture이며 실제 소켓 장애를 모두 재현했다는 뜻이 아니다. 사용자 변경 기준에 따라 로컬 Mac 공인 IP HTTPS와 휴대폰 데이터망 접속 확인 PASS(evidence/public-access.json). 별도 호스팅 배포는 제외했다. GitHub Actions/CI/CD/자동 보안 스케줄은 사용자 요청으로 제외했다.

## ENV-001 제한 시간 회귀 — 2026-10-28
`node --test scripts/deploy/local-start.test.mjs`: 실제 가짜 Docker 자식 프로세스로 무응답 엔진10초 종료·Compose 미실행, Compose 실패 코드 유지, 정상 시작 성공의3개 시험 PASS(FAIL0/SKIP0). `check bootJar sbom securityCheck` 성공, `e2eTest --rerun-tasks` 브라우저4개 재실행 성공. 새 DB/proxy/app 이미지를 빌드·재검사했다. PostgreSQL 통합17개를 다시 실행해 PASS했으며 전체 Gradle 인수 명령도 성공했다. proxy 디렉터리 탐색 권한 결함을 수정하고 실제 HTTP healthcheck를 추가했다. 최신 실제 수집 결과와 실행 이미지 일치는 evidence/verification.json에 기록한다.

AUTH-001: supplementaryUnicodePasswordUsesCodePointLimit 테스트는 수정 전 HTTP200 기대/400 실제로 실패했고 수정 후 PASS했다.14/129 code point 거부·128 code point 변경/로그인/탈퇴를 확인했다. PostgreSQL 통합18개, 전체 Java33개(FAIL0/SKIP0).

## 인증 화면·약관 동의 보완 (2026-10-28)
`sh scripts/build/gradle-local.sh --no-daemon check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest`: Java35 PASS, FAIL0, SKIP0 (단위11·PostgreSQL19·Playwright5). 미동의/구버전/필드 누락400 및 계정 미생성, 현재 버전·서버 시각 저장, 기존 계정 동의 NULL 보존을 검증했다. 390/768/1440px 로그인·가입 스크린샷과 넘침 없음, 기본 미동의·제출 차단·전문 새 탭·입력 보존·동의 후 전송을 검증했다. 스크린샷은 build/reports/screenshots의 login/signup-*에 있다.

공개 서비스 검증: V3 migration success=true, HTTPS login/signup/terms/privacy200, 미동의 가입400, 비회원 뉴스401, 약관 동의 후 합성 계정 가입·로그인·실제2출처 수집·계정/세션 삭제 PASS. 앱 교체 중 최초 compose --wait는 기존 proxy가 일시 unhealthy여서 exit1이었다. 후속 health 조회와 실제 HTTPS 검증은 정상이며 오류를 최초 성공으로 집계하지 않았다.

정책 문구 평이화: Java35 PASS/FAIL0/SKIP0 및 변경 이미지 검사 PASS. 실제 공개 HTTPS에서 쉬운 문구 제공·구버전 약관 가입400·2026-10-28.2 동의 가입/로그인/탈퇴 PASS. 상세 결과는 evidence/policy-update.json. 실제 수집 smoke는 앞선 실행 증빙을 유지하며 이번 문구 개정 때 출처 설정을 다시 변경하지 않았다.

로그인 제한 UI: Java37 PASS/FAIL0/SKIP0 (단위11·PostgreSQL20·Playwright6). 1~4회 잔여 횟수 미노출,5~9회5~1 반환,10회429, 실제 기한125초·기한 불연장·만료 후 성공 검증. 브라우저에서5회 안내·02:05→02:03 감소·버튼 비활성/만료 재활성·자동 요청 없음·아이디 변경 시 UI 해제를 검증했다. 합성 화면은 build/reports/screenshots/login-countdown-390.png.

계정 입력 길이 변경: Java39 PASS/FAIL0/SKIP0 (단위11·PostgreSQL21·Playwright7). 아이디 빈값/129자 거부,1/128자 가입·로그인·변경·탈퇴, 비밀번호5자 및5개 보조평면문자 거부·공백 포함6자 보존·6자 한글 변경, 기존128 Unicode 상한 테스트 PASS. 모바일128자 계정 메뉴 넘침 없음과 각 입력의 길이 속성 검증 PASS.

실제 실행: V4 success=true, 공개 HTTPS에서1자·128자 아이디/6자 비밀번호 가입·로그인·변경·탈퇴,5자 비밀번호 거부 PASS. 합성 계정은 삭제했다. 근거 evidence/account-lengths.json; db/web/proxy healthy,worker running.

관심사·계정 화면 분리: Java40 PASS/FAIL0/SKIP0 (단위11·PostgreSQL21·Playwright8). 별도 메뉴·페이지 책임, 관심사 저장 후 왕복 유지, 계정 화면의 tags/sources API 미요청, 탈퇴 시 A설정 삭제/B설정 보존,390/768/1440px 표시·넘침 없음 PASS. 기존 관심 문장 비전송 테스트도 선택적 문장 입력 영역을 열어 검증했다. 스크린샷 settings-*/account-*는 build/reports/screenshots에 있다.

공개 HTTPS: settings/account200, 각 페이지 기능 분리, 새 모듈200, 비회원 account API401 PASS(evidence/settings-pages.json). 기존 admin 계정 활성·ADMIN 유지 확인. 최종 실행 이미지와 검사 digest/JAR SHA 일치 확인.

## 관심사 파일 이전 (2026-10-28)
Java42개(unit11/integration21/E2E10) PASS, FAIL0/SKIP0. 독립 BrowserContext 두 개로 같은 계정의 설정이 처음에는 분리됨, 암호화 다운로드/파일 입력/확인/취소/명시적 교체/새로고침 후 유지/이후 독립 변경을 실제 Chromium에서 검증했다. 잘못된 파일 비밀번호, 다른 계정, 암호문 변조, 크기 초과, 미지원 구조, 가중치 범위·프로토타입 키·중복/과다 배열 거부 확인. 파일·비밀번호·설정의 네트워크 전송 없음. 최초 모바일 가로 넘침 실패2건을 CSS 수정 후 전체 재검증해 해결했다.

## 공개 지면·메뉴·검색·약관 개정
Java47개(unit11/integration23/E2E13) PASS/FAIL0/SKIP0. PostgreSQL 공개30개·query 거부·숨김 기사 재검사·공유429·보호 HTML/계정 API 접근·ADMIN/CSRF/대표값 갱신/409/입력400/감사 검증. 기존 회원 cursor/quota 회귀 유지. 브라우저 전체 메뉴 역할 점검은 MENU_AUDIT.md. 첫 dialog Escape 실패를 수정 후 전체 회귀 통과. 신규 약관 동의 ID terms-4와 기존 동의 기록 불변을 검증. 변경 후 UI 재실행 및 보안/배포 증빙은 최종 verification.json 참조.

## 최초판·스크랩 지면 (2026-10-28T12:49:33.292Z)
Java48개(unit11/integration23/E2E14) PASS/FAIL0/SKIP0. 전체 check/integrationTest/e2eTest/bootJar/sbom/securityCheck/acceptanceTest 통과. 최초 삭제 URL 검증의 빈404 응답을 브라우저 navigation으로 읽을 때 발생한 테스트 도구 오류는 실제 HTTP 응답 검사로 수정했고 8개 모두404를 확인했다. 검증 기준은 유지했다.
MoA-PaN/중앙 문구 설정/상단 검색 위치/중간 설명 제거/4·2·1단/가로 넘침 없음/인쇄 장식 제거/검색 비전송/권한 메뉴/신규 terms-1 가입을 검증했다. 스크린샷: build/reports/screenshots/scrap-gallery-{390,768,1440}.png (합성 기사). 공개 HTTPS 실제13기사와 최초판2문서, 구 동의 거부400·신 동의200·로그인200·회원 API200·일반회원 admin403·테스트 계정 삭제 PASS. 기존 admin 활성/ADMIN 유지, Compose db/web/proxy healthy·worker running. evidence/scrap-layout.json 및 verification.json 참조.

## 검색 상호작용·모바일 상단·푸터
CSS 변경 후 e2eTest/bootJar/sbom/securityCheck/acceptanceTest 통과. 기존 E2E14개 모두 재실행 PASS, 전체 최신 보고서48 PASS/FAIL0/SKIP0. 390/768/1440px 합성 지면 스크린샷 확인, 모바일 중복 구분선 제거 및 가로 넘침 없음. 검색 팝업·키보드·역할 메뉴·인쇄 회귀 유지. 최종 이미지/공개 CSS 일치 증빙은 evidence/header-layout.json 및 verification.json.

## 2026-10-28 실험 뉴스 재구성
기존23기사 삭제 및 관련 지면/이력 정리 완료. NIST40건·NSF15건 실제 worker SUCCEEDED, 공개 HTTPS200·최근7일3건 확인. NASA/NOAA0건·비활성, 기존admin ADMIN 유지·합성 계정0건 확인. FTC 실제 점검502 source_http_rejected는 실패로 기록하고 비활성 유지. 소스코드/의존성/이미지 변경 없음; 전체 Gradle·이미지 검사를 이번 데이터 작업에서 재실행했다고 주장하지 않는다. 증빙: evidence/news-reseed.json, 출처·제외 사유: SOURCES.md.

## 2026-10-29 주제·날짜·논문 수집 완료
관리자 발행 시작일/키워드 필터·V6 마이그레이션·루트 JSON 배열·arXiv Atom 링크 호환과 PostgreSQL 공유 요청 gate 구현. Java 테스트54 PASS/FAIL0/SKIP0(단위·통합·Playwright 포함). 실제 GitHub 보안30건, arXiv5분야 각20건 총100건 저장. NIST 주제RSS는200/정상형식이나 대상항목0건. 모든 신규 공개자료는2026-10-10 KST 포함 이후, 날짜위반0/중복공개URL0, 합성계정0. 공개HTTPS200/30항목, 최근7일 공통후보127건. 이전55건은 비공개 보존. 처음 권리 확인일의 UTC/KST 차이400은 UTC 확인일로 바로잡고, 주소 사이 arXiv cooldown 실패는4초 대기로 해결한 후 실제 성공을 확인했다. 최초 보안검사HIGH2 발견·v4수정/v3설정별 비노출 판정은 SECURITY_REVIEW.md 참조. 실행 증빙 evidence/topic-collection.json.

## 카드 여섯 항목 / research-v2
2026-10-29 KST: `sh scripts/build/gradle-local.sh --no-daemon check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest` 성공. JUnit XML 합계 58 tests, failures/errors/skipped 모두 0. 작성자 Atom 다중 저자·JSON 매핑/해시 변경, 연구 근거 문장 선택, 카드 정확한 항목/누락 표시/추천 이유·버튼 제거 검증 포함. 390/768/1440px 기존 화면 회귀 통과. 이전 미연결 번역 UI 모듈 테스트 포함(실제 번역 기능 완료를 의미하지 않음).

재수집 범위 밖 기존 요약을 `research-v2-stored`로 갱신하는 추가 통합 테스트 통과. 최초 추가 테스트를 DB 장애 테스트 뒤에 배치해 연결 실패 1건 발생했으며, PostgreSQL을 의도적으로 종료하는 기존 테스트보다 앞에 실행하도록 순서를 수정했다. 전체 integrationTest 재실행 PASS. 최종 합계 59 tests, failures/errors/skipped 0. 기존 장애 거부 테스트는 유지했다.

## 2026-10-29 서버 검색·슬라이드 실제 검증
63 tests PASS, failures/errors/skipped 0. PostgreSQL 통합 검색(10일 이전150개 fixture, 최신순7개·140상한·20페이지·CSRF·권리철회·와일드카드 문자 처리·허용 필드·429), Playwright 제출 전 비전송/POST/한 슬라이드씩 조회/메모리 외 미저장/초기화,390·768·1440px 화면을 확인했다. 최초 MockMvc CSRF 테스트의 파라미터 주입은 실제 클라이언트와 같은 헤더 방식으로 수정했고, 빈 지면 문구 기대값을 새 문구로 맞춘 뒤 재실행했다. 테스트 삭제/skip 없음.
최종 check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest 성공. 실제 HTTPS 기본42개, 검색136개·최대20페이지·페이지당7개, 기본42개 밖 결과 조회, CSRF 누락403/초과페이지400/no-store 확인. 합성 검색어가 web/proxy/db 최근 로그에 없음을 확인했다. 운영 DB는 log_statement=none, log_min_duration_statement=-1, log_min_error_statement=panic, log_parameter_max_length_on_error=0. 증빙: [search-delivery.json](evidence/search-delivery.json).

원문 미보관 회귀: check/integrationTest 성공. 통합28개 PASS/FAIL0/SKIP0, 기존 단위17개 결과는 UP-TO-DATE. collectionDoesNotPersistOriginalInputEvenInRevisions는 입력 전용 표식이 기사·수정 이력·작업·실행 이력에 남지 않고, 요약/링크 보존 및 원문 변경 해시 갱신을 확인했다. 운영 DB191개 기사 및12개 수집원 조사: 원문 저장 칼럼 없음, 본문 접근/보관 활성0. docs/05-test/evidence/no-original-retention.json 참조. 운영 코드·의존성·이미지 변경이 없으므로 E2E/이미지 스캔/재배포는 이번에 재실행하지 않았다.

## Index Translate 2B + glossary-v1
2026-10-29: unit21/integration29/Playwright20=70 tests PASS, failure/error/skip0. 원문 매칭/부분문자열 제외/한국어조사/역방향 canonical 용어, 숫자·CVE 검증, 권리철회/내용변경 무효화/lease/translated search 및 브라우저 저장번역 선택 포함. 로컬 명령 check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest 성공. 운영실제 번역 검증은 후속 증빙으로 기록한다.


## Linux 자동 번역 납품 검증 (2026-10-29)
Index Translate 2B Q8_0를 Linux CPU 컨테이너로 이관했다. Docker Desktop Linux ARM64 VM에서 실제 실행했으며 원격 Linux 서버·AMD64·GPU는 미검증이다. native Mac 모델 프로세스는 중지했고 worker는 내부 translator:11435만 사용한다. 빈 모델 볼륨 다운로드·SHA256 검증과 실제 CPU 추론 성공. 번역 자식 프로세스 SIGKILL 후 Docker RestartCount 0→1, healthy 및 runtime READY 복구 확인. worker PID1 kill 요청은 실제 재시작되지 않았으므로 worker 강제종료 복구 성공으로 계산하지 않는다. worker lease/heartbeat/권리철회/재시도는 PostgreSQL 통합 테스트로 검증했다.

check·integrationTest·e2eTest·bootJar·sbom·securityCheck·acceptanceTest 실행: 173 PASS(단위124/통합29/E2E20), FAIL0/ERROR0/SKIP0. 고정 validator100사례는 25문장쌍×양방향×정상/수치변조이며 100개 독립 LLM 번역 품질 점수가 아니다. 실제 CPU 짧은 문장4개 3619~4360ms, 중앙값4321ms(2CPU/2thread/context4096/parallel1/GPU0). 긴 논문 요약 처리량으로 일반화하지 않는다. 증빙: docs/05-test/evidence/translation-linux-cpu.json.

실행 앱 이미지 sha256:59f4c97c8c7ee18b88ff29ec18b978b78c3602c09b3d1d98c8082147615e40fe, translator sha256:2221c46cc144f87d4b371b0264809a3ad3d8fec9642b83b7ab14ee990cec4ee0. 최종 이미지·JAR·SBOM·소스 검사 gate 통과. 자동 검사 통과가 완전한 의미 정확성을 보장하지 않는다. 전체 기존 기사 재번역은 백그라운드 진행 중이며 모두 번역 완료라고 보고하지 않는다.

V9 적용 DB 암호화 백업·별도 DB 복원 PASS: 기사191개, 복원 계정 활성0/세션0. 운영 DB를 덮어쓰지 않았으며 시험 DB는 제거했다. 현재 공개 HTTPS200 확인.


## 중간 구조 정리 및 Helm 준비 (2026-10-29)
운영 지원 파일을 scripts/build·deploy·translation·backup·security·test·governance·experiments로 이동하고 Gradle/Docker/문서/스크립트 참조를 함께 갱신했다. README의 번역 미연결/AMD64 전용 설명을 정정하고 docs/README 색인을 추가했다. 기존 Mac 번역 비교 실행기는 재현용 experiments로 분리, 운영 미사용. 데이터/비밀값/모델/캐시는 Git 제외를 유지했다. Compose 모든 실행 조합 config검사, shell/Node 문법검사, Node12 tests, 전체 이미지4종 빌드·보안 검사 및 새 경로 Compose 기동/HTTPS200 확인.

Helm4.3.1 공식 archive checksum검증 후 lint/template 및 필수값·unpinned image·잘못된 migrationVersion 거부3건 PASS. 실제 Kubernetes context 없음: 설치/Pod기동/PVC/TLS/registry pull은 NOT_RUN. rendered Trivy config HIGH0/CRITICAL0, MEDIUM11/LOW2 남음: 합성 registry 주소 허용목록 경고10, 비밀값 없는 DATABASE_URL/USER ConfigMap 탐지1, PostgreSQL 공식 alpine UID/GID70 경고2. egress 차단 미구현과 운영 검증 조건은 chart README에 명시했다. 초기 path 이동으로 VEX검사 ENOENT와 Helm range namespace 렌더링 오류를 발견·수정하고 재검증했다. 빈 렌더링 검사 결과를 PASS로 사용하지 않았다. 증빙 docs/05-test/evidence/directory-helm-checkpoint.json.

최종 clean 전체검증: 173 PASS(단위124/통합29/E2E20), FAIL0/ERROR0/SKIP0, 모든16 Gradle task 실제 실행 성공(1분3초). 소스/JAR/SBOM 보안 gate PASS. Node12 tests PASS. 비밀값/캐시 추적경로 없음 및 필수 빌드/Wrapper/lock/verification 파일 존재 확인.

독립 리뷰4건 수정 후 clean 전체검증 176 PASS(단위124/통합30/E2E22), FAIL0/ERROR0/SKIP0. scripts/build 실행기 추적 확인, 소스/JAR/SBOM 보안 gate 및 앱 이미지 검사 통과. 현재 head 재리뷰 후 develop 병합 예정.


## Qwen3.5-4B 주제 분류 적용 (2026-10-29)
Q4_K_M 고정 HF revision/download SHA 검증, Linux CPU classifier 별도 Compose 서비스와 worker 비동기 분류를 적용했다. V10/V11 migration 성공, 기사191/계정2 보존 및 사전 암호화 백업 완료. 허용된 제목·요약만 입력하고 원문/모델 출력 전문을 추가 저장하지 않는다. 관리자 고정 주제 편집(ADMIN/CSRF/version), 단일 lease/재시도/늦은 결과 거부, 수동 잠금과 총6태그 보호 구현.

최종 테스트190 PASS(단위127/통합40/E2E23), FAIL0/ERROR0/SKIP0. 중간 독립 리뷰2건(잠금 태그 삭제/총태그 초과)을 V11/서비스 및 회귀로 수정했다. 기존 E2E 로딩 문구와 네트워크 요청 도착 순서의 race를 요청 도착 대기로 수정했고 검증 항목은 유지했다. 소스/JAR/SBOM/앱·classifier 이미지 보안 gate PASS. HTTPS200/비회원 분류관리API401. 실제 LLM 태그 DB 저장 확인, 증빙 시점 분류가능136건 중2건 DONE/1 RUNNING/133 PENDING이며 전체 분류 완료를 뜻하지 않는다.

Helm classifier Deployment·PVC·ClusterIP·worker Secret·NetworkPolicy 및 migrationVersion11 준비. lint/template/부적합 입력3건 거부 PASS. 렌더링 스캔 HIGH0/CRITICAL0/MEDIUM13/LOW2. 실제 클러스터 설치·원격 Linux AMD64/GPU는 NOT_RUN. CI/CD 미추가. 증빙 docs/05-test/evidence/qwen-classification.json.

## SI 산출물·도메인 리팩토링
191 PASS(단위128/통합40/E2E23), 실패/오류/건너뜀0. docsCheck 26개 산출물/링크/ES모듈/도메인 경계 PASS. 3개 핵심 정책을 framework classpath 없이 JDK 단독 컴파일 PASS. 소스/JAR/SBOM 및 최종 앱 이미지 gate PASS. 기존 증빙 byte-identical 확인. 상세 evidence/domain-refactor.json.

## 회색 신문지 질감
CSS gradient 기반 종이 질감과 중립색 기사 조각 적용. E2E23 PASS/실패0/건너뜀0, 390·768·1440px 합성 화면과 print 확인. SVG 방식에서 언어 전환 시 이미지 추가 요청이 발생해 순수CSS로 수정했고 기존 무통신 검증을 유지했다. JAR/SBOM/소스·최종 앱 이미지 보안 gate PASS. 앱sha256:2584ff56ac496eaca9199f7526d5732c256f0447d0d44c0b8c98374d0fa764bb.

## 연속 신문 지면·광고 영역
E2E24 PASS, FAIL/ERROR/SKIP0. 390/768/1440px에서1/2/6열 그리드와 넘침 없음, 카드 그림자·회전 제거, 인쇄 확인. 합성 광고 이미지 및 HTTPS 링크/rel, 도구가 광고 아래에 있는지와 외부 요청 없음·영문 광고 표시 검증. 소스/JAR/SBOM/앱이미지 gate PASS. 최종앱sha256:35596a2e85b6194e220611e33e3a2f31077595bbc832b41ef0f48d96c8185a82.

## 모바일 블로그 카드
E2E24 PASS, FAIL/ERROR/SKIP0. 390px에서 모든 카드와 지면폭 일치(0.5px 허용), 카드배경·제목크기 일관·가로넘침 없음 확인. 768/1440px와 print 기존배치 검증. 소스/JAR/SBOM/최종앱이미지 gate PASS: sha256:ea29796215f16f356c9b2ffa05d87c2c6cd953b238308bfd27410caafc2c7728.

## 지면 발행시각과 모바일 메타
단일 재실행 E2E25 PASS/FAIL0/ERROR0/SKIP0. 최근수집 순서와 발행순서가 다른 고정fixture, 분단위 표시/언어전환/발행미상시수집기준/모바일세로우측정렬 검증. 초기 두 검증 프로세스의 결과파일 충돌(EOF/NoSuchFile)은 성공으로 세지 않았고 종료 후 --rerun-tasks 단일실행으로 재검증. 소스/JAR/SBOM 및 앱이미지 gate PASS, sha256:b8b5dbcbafa3a528993850385a025fe56cda795bfa6268a63faea774749688d9.

## 로그인·탭 제목·SEO/HTML 표준
197 PASS(단위130/통합41/E2E26), FAIL/ERROR/SKIP0. 정적9페이지 Nu26.10.12 검사 messages0. 초기 빈 time/heading 수정, DB중단 테스트 후 실행되어503을 받은 신규 통합케이스는 DB중단 전에 검증하도록 순서를 수정한 뒤 재검증. 초기 누락된 영문 제목 번역을 추가했고 최종 모든9페이지 탭 제목/로그인390·1440중앙정렬 검증 PASS. 소스/JAR/SBOM/이미지 gatePASS. 공식문서·범위·남은조건은 seo-web-audit.md, 증빙 evidence/login-seo.json.

## 로그인 언어 선택 재배치
E2E26 PASS, 오류/건너뜀0. 390/1440px에서 header언어선택0/footer언어선택1 및 약관 아래 배치/언어전환 확인. Nu9HTML messages0. 소스/JAR/SBOM 및 최종 앱 이미지 gatePASS: sha256:b48680e08e4d908ffaaadc2a12f3ebe2cde84ad0f062f388889732bb689393f8.

## 로그인 언어 도구 상단 분리
E2E26 PASS(390/1440px 우측24px 여백/제호 위 배치/푸터선택없음/중앙정렬), Nu9HTML messages0. 소스/JAR/SBOM/이미지 gatePASS. 앱sha256:f4da0654b5ac212a4540c77c3e9a77e5b49553c217bed78976fbec23aff1e95e.

## 로그인 필수 및 선택적 자동 로그인
최종200 PASS(단위130/통합43/E2E27), FAIL/ERROR/SKIP0. CookieMaxAge·HttpOnly·Strict, sessionfixation교체, 기존쿠키로그아웃후401, 타브라우저유지,30일absolute/pw/delete검증. 비회원뉴스API401/HTMLredirect, legacypublic경로계정quota강제. 기존 비회원검색 검증은 최신인증계약에 맞춰 로그인 세션으로 실행하고 무인증거부검증을 추가했다. Nu9HTML messages0, 소스/JAR/SBOM/앱이미지 gate PASS.

실제 HTTPS 자동 로그인 smoke PASS: 로그인 필수 이동·영속 쿠키·로그아웃 이전 쿠키 무효·별도 브라우저 유지 확인. 합성 계정 삭제. evidence/remember-https.json 참조.

아이디 저장 UI: 단위130/E2E27 PASS(실패·skip0), Nu9페이지 메시지0, 소스/JAR/SBOM/최종 이미지 gate PASS. 증빙 saved-username.json. 백엔드 변경이 없어 통합테스트는 재실행하지 않음.

2026-10-30 보안재검증: truststore조치후 --rerun-tasks check integrationTest e2eTest bootJar sbom securityCheck 실행. 단위132/통합44/E2E27=203 PASS, 실패·skip0. scanner정책4PASS별도. 정상TLS/14문제체인거부/실제HTTPS·401·chunked413확인. 최초새테스트구현전미정의클래스컴파일실패는성공으로세지않고구현후전체재실행완료. 증빙evidence/security-refresh.json.

로그인 옵션·광고 관리 최종 검증: 단위132/통합45/E2E28=205 PASS, 실패·skip0. 초기 통합 fixture의 Jackson JsonNode 컴파일 오류는 ObjectNode로 수정 후 통과. HTML9페이지 Nu0, Helm lint/render 및 잘못된 입력3거부, 소스/JAR/SBOM/최종이미지 gate PASS. 390/768/1440 가로정렬·헤드라인여백·추적요청없음·관리자저장/미리보기·AdSense비활성 검증. HTTPS합성회원1회 생성/삭제로 API401/403/최소출력 확인. 증빙evidence/layout-advertising.json.

트렌드 뉴스레터 최종: 단위132/통합46/E2E30=208 PASS, 실패·skip0. 정책4, Nu11페이지 메시지0, Helm lint/render·잘못된 설정 거부, 소스/JAR/SBOM/최종 앱·변경 proxy 이미지 gate PASS. 초안 비공개/발간 후 초안 수정 분리, 안전한 블록 출력, 390/768/1440, 지연 저장 중 원고 전환 차단·편집 유지 확인. 실제HTTPS 회원 접근·관리권한·합성계정정리 및 V13·실행JAR일치 확인. 초기 병렬 테스트 결과 파일 충돌은 실패로 기록하고 단일 실행으로 재검증했다. evidence/newsletter.json 참조.
