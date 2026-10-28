# 실제 검증 결과

최종 실행 시각·JUnit 집계·이미지 식별값·실제 수집은 [검증 요약](evidence/verification.json)에 기록한다. raw scanner/JUnit/스크린샷은 로컬 `build/reports`와 `build/test-results`, 명령 로그는 `.local/evidence`에 있으며 Git에 비밀값·원문 전문·DB dump를 넣지 않는다.

실행 명령:

```sh
sh scripts/gradle-local.sh --no-daemon --max-workers=2 clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest
node --test scripts/github-protection-bootstrap.test.mjs
node scripts/local-smoke.mjs
sh scripts/image-scan.sh moapan-local:1.0.0 moapan-proxy-local:2.11.7 moapan-db-local:18.6 moapan-build:1.0.0 moapan-proxy-build:2.11.7
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
|TLS|호스트와 실제 실행 이미지의 기본 truststore·일반 HTTPS 성공·공급자 연결된14개 검증 호스트 모두 SSLHandshakeException 거부|scripts/TrustAudit.java|
|Git 보호|최초 보호 설정 실행기9테스트·서버 보호 read-back·guard|bootstrap-tests.log, guarded wrapper|

재현 중 발견한 실패는 수정 후 재검증했다: Spring JDBC Instant 바인딩, DB 프록시 필드 접근, 세션 fixture 만료 시각, safe DNS CIDR 오차, JAR scanner 미인식, Caddy/DB 이미지 취약 구성요소, DB 장애 Retry-After 누락. 실패 기록을 성공으로 합산하거나 테스트 기준을 낮추지 않았다.

추가 실제 시험: 두 web 컨테이너에 번갈아 요청하여 계정당60회 이후 양쪽429와 재로그인 후 한도 유지를 확인했다. 실행 중 worker를 SIGKILL하고 재시작하여 실제2분 lease 만료 후 attempt2 성공·기사20건 보존·오래된 RUNNING fetch 이력 정리를 확인했다. 각각 multi-web-quota-check.mjs와 worker-recovery-check.mjs로 재현한다.

검증 한계: 장시간 부하와 모든 네트워크 timeout/5xx/리다이렉트 조합의 연결 수준 fault injection은 수행하지 않았다. 재시도 통합 테스트의503·timeout은 수집 계층 오류 fixture이며 실제 소켓 장애를 모두 재현했다는 뜻이 아니다. 사용자 변경 기준에 따라 로컬 Mac 공인 IP HTTPS와 휴대폰 데이터망 접속 확인 PASS(evidence/public-access.json). 별도 호스팅 배포는 제외했다. GitHub Actions/CI/CD/자동 보안 스케줄은 사용자 요청으로 제외했다.

## ENV-001 제한 시간 회귀 — 2026-10-28
`node --test scripts/local-start.test.mjs`: 실제 가짜 Docker 자식 프로세스로 무응답 엔진10초 종료·Compose 미실행, Compose 실패 코드 유지, 정상 시작 성공의3개 시험 PASS(FAIL0/SKIP0). `check bootJar sbom securityCheck` 성공, `e2eTest --rerun-tasks` 브라우저4개 재실행 성공. 새 DB/proxy/app 이미지를 빌드·재검사했다. PostgreSQL 통합17개를 다시 실행해 PASS했으며 전체 Gradle 인수 명령도 성공했다. proxy 디렉터리 탐색 권한 결함을 수정하고 실제 HTTP healthcheck를 추가했다. 최신 실제 수집 결과와 실행 이미지 일치는 evidence/verification.json에 기록한다.

AUTH-001: supplementaryUnicodePasswordUsesCodePointLimit 테스트는 수정 전 HTTP200 기대/400 실제로 실패했고 수정 후 PASS했다.14/129 code point 거부·128 code point 변경/로그인/탈퇴를 확인했다. PostgreSQL 통합18개, 전체 Java33개(FAIL0/SKIP0).

## 인증 화면·약관 동의 보완 (2026-10-28)
`sh scripts/gradle-local.sh --no-daemon check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest`: Java35 PASS, FAIL0, SKIP0 (단위11·PostgreSQL19·Playwright5). 미동의/구버전/필드 누락400 및 계정 미생성, 현재 버전·서버 시각 저장, 기존 계정 동의 NULL 보존을 검증했다. 390/768/1440px 로그인·가입 스크린샷과 넘침 없음, 기본 미동의·제출 차단·전문 새 탭·입력 보존·동의 후 전송을 검증했다. 스크린샷은 build/reports/screenshots의 login/signup-*에 있다.

공개 서비스 검증: V3 migration success=true, HTTPS login/signup/terms/privacy200, 미동의 가입400, 비회원 뉴스401, 약관 동의 후 합성 계정 가입·로그인·실제2출처 수집·계정/세션 삭제 PASS. 앱 교체 중 최초 compose --wait는 기존 proxy가 일시 unhealthy여서 exit1이었다. 후속 health 조회와 실제 HTTPS 검증은 정상이며 오류를 최초 성공으로 집계하지 않았다.

정책 문구 평이화: Java35 PASS/FAIL0/SKIP0 및 변경 이미지 검사 PASS. 실제 공개 HTTPS에서 쉬운 문구 제공·구버전 약관 가입400·2026-10-28.2 동의 가입/로그인/탈퇴 PASS. 상세 결과는 evidence/policy-update.json. 실제 수집 smoke는 앞선 실행 증빙을 유지하며 이번 문구 개정 때 출처 설정을 다시 변경하지 않았다.

로그인 제한 UI: Java37 PASS/FAIL0/SKIP0 (단위11·PostgreSQL20·Playwright6). 1~4회 잔여 횟수 미노출,5~9회5~1 반환,10회429, 실제 기한125초·기한 불연장·만료 후 성공 검증. 브라우저에서5회 안내·02:05→02:03 감소·버튼 비활성/만료 재활성·자동 요청 없음·아이디 변경 시 UI 해제를 검증했다. 합성 화면은 build/reports/screenshots/login-countdown-390.png.
