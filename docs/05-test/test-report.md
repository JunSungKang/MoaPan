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

## 뉴스레터 편집 지면 재구성
최종 단위132/E2E31 PASS, 실패·오류·skip0. check·e2eTest·bootJar·sbom·securityCheck를 단일 Gradle 실행으로 검증했다. Nu11페이지 메시지0. 백엔드 미변경으로 PostgreSQL 통합테스트는 이번에 재실행하지 않았다(직전 뉴스레터 증빙46 PASS와 구분).

합성 발간물3개와 긴 한국어 원고·그림·인용·링크를 사용해390/768/1440의 목록/상세/편집/미리보기12장 및 빈 목록1장을 생성하고 직접 확인했다. 필터, 상세의 목록 소개 숨김, 관련 블록 필드만 표시, 미리보기 소개/본문 일치, 가로넘침 없음, 파일 입력 키보드 포커스와 기존 지연저장 경합을 검증했다. 최초 태블릿 표지의 min-height/aspect-ratio 넘침을 실패로 검출하고 폭 제한 후 재검증했다. 독립 리뷰의 보이지 않는 업로드 포커스 지적을 수정했다.

소스/JAR/SBOM 및 최종 앱이미지 gate PASS. 앱 HIGH0/CRITICAL0, MEDIUM23/LOW4 잔여 OS 공지는 기존 보안 검토를 유지한다. 실제 HTTPS 정적 자산4개 hash 일치, 비회원 HTML302/API401, 실행 JAR와 검증 JAR SHA256 일치. Compose web healthy, worker 실행 확인. DB·원고·실발간물은 UI 테스트로 변경하지 않았다. 증빙 [newsletter-redesign.json](evidence/newsletter-redesign.json), 스크린샷 build/reports/screenshots/newsletter-*.png.

## 뉴스레터 전체 삭제와 첫 주간호
최종 단위132/통합47/E2E32=211 PASS, 실패·오류·skip0. check integrationTest e2eTest bootJar sbom securityCheck 단일 실행 완료. Nu11페이지 메시지0. 관리자/CSRF·잘못된 토큰·일반회원 거부, 버전 누락/음수400, 충돌409, 삭제/없는ID404, 초안·발간본·그림 행 제거와 감사1건을 PostgreSQL에서 검증했다. 삭제 후 version0 생성 PUT 재전송은409로 거부한다. 브라우저 확인 취소/충돌 시 입력 보존/삭제 중 원고전환 차단/성공 시 편집기 정리/미저장 신규 원고의 로컬 삭제를 확인했다. 기존390/768/1440 화면·미리보기·키보드 회귀도 유지했다.

독립 리뷰가 발견한 지연 초기 PUT의 삭제 후 부활은 공통 UUID 트랜잭션 잠금과 영속 삭제 감사 표식으로 수정했다. 실발간 검수에서 발견한 시간대 누락도 목록/상세/관리자 목록의 UTC ISOZ 변환과 API 회귀로 수정했다. 최종 소스/JAR/SBOM/앱이미지 보안 gate PASS, HIGH0/CRITICAL0/MEDIUM23/LOW4. 앱 sha256:cf3e54452b050c43db1096772762ef3d6c75b6c65d6c9ca2a378032df6cc187b. 실제HTTPS정적자산4개 일치, 실행JAR와검증JAR SHA256일치. 삭제 증빙 [newsletter-deletion.json](evidence/newsletter-deletion.json).

사용자가 명시 요청한 실제 첫 호2026-001 「더 큰 AI보다, 덜 낭비하는 AI」를 발간했다. 이번 주10/26~30 범위의 실제 수집 arXiv 연구5편을 공식 공개 페이지에서 확인하고 독자적인 해설·출처링크5개·자체 개념도1개, 총24블록으로 작성했다. 원문/초록 전문을 별도 저장하지 않았다. 첫 실발간 화면 검사1회는 실패했고 당시 원인 진단을 남기지 못했으므로 성공으로 세지 않았다. 읽기 전용 재검증 후 시간대 수정까지 반영한 최종 실제HTTPS브라우저 검사에서390/768/1440 본문/호수/참고링크/넘침/관리자 삭제버튼을 확인했다. 원고 재발간 없이 최초시각을 유지했다. 임시 편집 계정·세션 삭제 및 잔존 계정0 확인. 실제 발간 증빙 [newsletter-first-issue.json](evidence/newsletter-first-issue.json).

## 2026-001호 주간 수집 근거 한정 R&D 개정
본문/자체그림 개정만 수행했고 애플리케이션 코드는 변경하지 않았다. 기존5근거의DB collected_at이2026-10-30로모두이번주범위임을검사하고,미공개초안과발간본이같은지확인후현재version으로PUT/발간했다. 같은호수1/동일UUID, version4, 42블록/참조5/그림1의모든필드를API에서대조했다. 본문은연구결과/세전망/제안기간·수치/전환·중단조건을구분한다. 독립원고검토에서확인한지표모호성은‘정답근거를정확히포함한답변의비율’로명시하고검색nDCG·DB성능에그대로적용하지않도록보완했다.

실제HTTPS Playwright Java390/768/1440에서제목·2026-001호·세전망·세연구기간·참고링크5·그림·가로넘침없음확인PASS,PC/모바일및로드맵그림스크린샷검수. 임시계정·세션제거/잔존0확인. 동일운영앱cf3e54452b050c43db1096772762ef3d6c75b6c65d6c9ca2a378032df6cc187b를유지한다. 전체Gradle211개테스트·취약점스캔은코드/의존성/이미지변경이없어이번콘텐츠개정에서는재실행하지않았으며직전결과와구분한다. 증빙 [newsletter-rd-rewrite.json](evidence/newsletter-rd-rewrite.json).


## 수집 뉴스 비회원 공개 (2026-10-30T17:11:40.413Z)
단위132/통합48/E2E34=214건 PASS, 실패/오류/skipped0. scanner정책4건과 Nu HTML검사0메시지, 문서검사통과. 실제Testcontainers PostgreSQL에서 공개edition42/no cursor·조작400·숨김/권리철회·익명전역600/분429·회원quota유지·CSRF/동일origin·뉴스레터401/보호화면302를 확인했다. Playwright는 대표관심사와이전회원IndexedDB분리·비회원메뉴·공개검색·390/768/1440넘침없음·인증해제시뉴스레터소개제거/지연응답폐기를검증했다.
최초독립리뷰의소개잔존지적과첫HTTPS검사의HTTP리다이렉트실패를수정하고전체회귀/보안/이미지검사를재실행했다. 실제HTTPS24개검증PASS, 지면42개/검색7개/최대140개, 비회원뉴스레터목록·실제발간본문401, 보호화면상대Location으로HTTPS로그인유지. 실제브라우저에서도비회원기사표시와HTTPS로그인전환을확인했다. JAR 102506508af4a808c0ce7eb07cc2d48c12f17db7471ae82e8a3df10948a1e5ac, 이미지 sha256:47b30aaf13c58138e0e46324c0f0ab328c34316300accf1768694b4db3d529c3 검사/실행일치. 기존OS와proxy조건부잔여위험은유지한다. 증빙 [public-newspaper.json](evidence/public-newspaper.json).


## 승인된 뉴스레터 사업화 기반 (2026-10-30T18:12:31.079Z)
전체229건: 단위140/PG통합50/Playwright39 PASS, 실패·오류·skip0. 별도scanner정책4 PASS, Nu HTML0메시지, 문서28개등록/참조검사PASS. 신규주간/기준시각·권리·근거·개정·구독혜택정책, 서버발간/권한/CSRF/정정ID유지, 출처무료/유료문구·안전HTML·양식·로컬익명집계·주간전환·점검경합·영문전환을검증했다. 초기컴파일/테스트순서/언어전환실패를수정했고기준을낮추지않았다.
실제V14마이그레이션성공, 실행이미지 sha256:db82be6087ffea3ffa11b38a2dbb6930833734b7cab1b047d2199380d6ae9b98, JAR e8f5b146c80f761ce8b9495f6ee11de9dc7c2273134a655c2de5140a06533edc 동일. 기존2026-001본문SHA유지/근거5/전망3/개정2, 최초발간시각을기존첫호증빙07:23:17.220855Z로복원(버전충돌검사·최소감사). 실제HTTPS보호/공개12검사, 실브라우저390/768/1440 독자/관리/평가화면·출처복사·그림포함HTML·업무양식3종확인PASS. 최초Live CSP평가스크립트실패는보안정책을완화하지않고일반UI조작으로수정했다. 임시계정0, 암호화V14백업복원/복원계정비활성·세션제거/임시DB·키·backup정리PASS. 자동백업이나실제고객조사성공을뜻하지않는다.
Helm migrationVersion14 lint/render/부정입력3PASS, 합성값렌더Trivy HIGH0/CRITICAL0/MEDIUM13/LOW2. registry예시12건은실배포전제, interval300 ConfigMap경고는credential아님, 기존vendor DB UID/GID70 LOW2는PVC호환이유로유지한다. 실제클러스터배포는미실행. 자세한결과 [newsletter-product.json](evidence/newsletter-product.json).

최종독립HTML출력보완: UTF-8/모바일viewport를명시하고기존출처/CSP구성을유지했다. 전체229회귀/최종JAR·SBOM·이미지검사재실행PASS. 최종앱 sha256:433f4af4bfaceecb8c74059c5ea3a12a4e1e0bf43c690e1788c4680394038011, JAR 6d216668118b46b78da963622288eca4543de82c85aea08254f274294b577865; Compose동일digest와추가HTTPS5경계·실제output모듈hash를확인했다. 앞선실인증브라우저/다운로드/복원증빙은직전이미지의전체시나리오이며최종변경은head메타데이터만으로분리기록했다.


## 오해로 추가한 제품 평가 기능 제거
사용자 재평가 요청을 서비스 기능으로 잘못 구현한 부분을 삭제했다. 평가 전용1개 E2E와3개 관련 접근 assertion을 제거했고 연구 gate·재게재·양식 테스트는 유지했다. 정적 링크 무결성 단위1건과 일반 파일404 PG회귀1건을 추가했다. 최초 운영 확인에서는 부재 파일이503으로 반환되어 NoResourceFoundException 전용404/no-store 처리를 추가했다. 새 PG시험의 초기1건 실패는 DB중단99 뒤 실행된 순서 문제였으며 Order1로 수정한 뒤 clean 전체를 재실행했다.
최종 단위141/PG51/Playwright38 =230 PASS, 실패/오류/skip0. Nu0·정책4·문서28 PASS. 로컬 및 운영 JAR에 삭제 파일/참조 없음, 보존 모듈 있음, 운영 이미지/JAR 일치. HTTPS 삭제 HTML/JS3개404 및 기존 뉴스/인증 경계·정적hash까지12건 확인했다. 과거229건 증빙은 당시 결과로 보존하고 현행 제거 결과는 [제거 증빙](evidence/newsletter-evaluator-removal.json)으로 대체한다.


## 고정 보안 체크리스트 인증 경합 회귀
초기3개 latch PG재현은 기대401/409/단일성공과 달리200/200/동시200으로 모두 실패했다. V15 인증버전·SQLCAS·세션검사 후 통과했다. 독립 리뷰의 삭제/동일아이디재가입 문제도 실패재현 후 UUID+version snapshot으로 수정했다. 버전이 늦은 세션은 다음 요청401/서버세션삭제를 검증한다. 최종 clean 단위141/PG56/Playwright38=235건 PASS, 실패/오류/skip0. 정책4/Nu0/Helm lint·render·부정3 PASS. 실제V15 마이그레이션, 합성HTTPS19동작·회원삭제, 암호화V15복원 후 active_accounts0/sessions0 및 격리DB삭제 확인. 검사도구전이취약점수정후최종재검사를별도증빙에기록한다.

## 보안 단독 PR 및 기능 유지 조합 검증 — 2026-10-31
- 보안 단독 develop 기준:218 PASS(단위137/PG통합51/브라우저30), FAIL0/SKIP0. licenseCheck 포함 전체 계약 task 실행.
- 현재 서비스 기능을 보존한 composite:240 PASS(단위146/PG통합56/브라우저38), FAIL0/SKIP0. 첫 조합의 영어 안내 사전 누락을 E2E가 검출해 수정 후 재실행했다. 단독/조합 결과를 합산하지 않는다.
- 실제 CPU 모델:번역1회/분류1회 합성 입력 smoke 성공. 모델 성능의 일반 평가가 아니다. 새 경계 회귀5개 및 기존 번역/분류 검사를 유지했다.
- Nu HTML:메시지0. Helm:lint/render/부정3개 통과, 실제 클러스터 설치 없음. 최초 helm 경로 미설정을 workspace 기존 바이너리 지정으로 수정했다.
- 최종 Compose6개 서비스 정상, 초기화/마이그레이션 종료0. DB V15, 기사270·발간1 보존, 검증 계정/격리 복원 DB0. HTTPS/로그인/비밀번호 교체/삭제/권한19개 통과. 실제 실행 JAR SHA와 검사 JAR SHA 일치.
- 최종7개 이미지 보고서 SHA·실행 digest·DB freshness·truststore14개 거부는 [증빙](evidence/security-ai-oss-validation-20261031.json). 전체20항목 미완료 사유는 동일 체크리스트에 유지.
- 보안 PR은 뉴스레터 PR8의 V14가 선행하는 V15 릴리스 순서를 명시한다. 기존 서비스에는 이 둘을 함께 검증한 조합을 사용했다. 병합은 미수행.

## G01~G11 가드레일 후속 검증
2026-10-31: clean check/integrationTest/e2eTest/bootJar/sbom/securityCheck/acceptanceTest 실제 성공. 단위153/PG63/Playwright39, 실패·오류·skip0. 실모델 공격6개 추론 전 보류, 정상2건, 취소1건 성공. HTTPS 합성 계정20검사 성공 후 삭제. Helm lint/render/부정3개·동노드/selector/다운로드·egress 경계와 렌더된 manifest Trivy config 검사 성공; 실클러스터 미제공. [실행 증빙](evidence/ai-guardrail-followup-20261031.json). 원래20개 정의는 유지하며 S05/S06/S07은 미완료다. 새 G01~G11의 구현·범위 검증 통과가 모든 취약점 제거를 의미하지 않는다.
