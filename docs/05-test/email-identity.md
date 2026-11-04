# 이메일 인증·복구 검증서

> 문서번호: MP-DOC-038 · 상태: 이 프로젝트 SMTP 책임 분리 검증 중, 외부 운영 전환 보류 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

## 1. 검증 범위와 판정

사용자 승인 요구인 필수 수신 이메일 가입·인증, 비밀번호 찾기/재설정, 기존 회원 이메일 등록·변경, 목적 제한 고지와 기존 계정 보존을 검증한다. REQ-03/08/10/11의 이메일 예외다. 아이디1~128자·비밀번호6~128 Unicode code point, 개인 관심사 비전송을 유지한다. 결제 구현, develop/main 병합은 제외한다.

현재 운영은 기존 이미지/V24다. 아래 로컬 합성 검증 성공은 신규 기능의 운영 반영이나 실제 외부 받은편지함 전달 성공을 뜻하지 않는다. 초기 내장 Postfix의 DS-0002 차단은 제13절의 최신 수정·재검증 결과를 따른다. 제2~12절은 각 시점의 시험 이력이며 외부 MailBox 검출과 운영 전환은 별도다.

## 2. 요구사항별 증빙

| 요구 | 코드 | 실제 시험·증빙 | 판정 |
|---|---|---|---|
| 필수 인증 이메일·안내 확인·USER 고정 | EmailController, AuthController, EmailIdentity, signup.html | EmailIdentityIntegrationTest의 가입/role 주입/동의/암호문 및 본인 전용 조회 시험 | PASS |
| 5분·1회·5회 오입력·세션/용도 결합 | EmailIdentity, EmailSecrets | 만료/목적 혼동/재사용/다른 세션/오입력5회/동시 소비 시험 | PASS |
| 비밀번호 찾기와 모든 세션 종료 | recovery.js, forgot-password.html, EmailIdentity | 실제 PostgreSQL 복구·기존 기억 세션 종료·코드 소비, Playwright 로그인 링크/한영 전환/입력 중 비전송/저장 미사용 | PASS |
| 기존 회원 이메일 등록·변경 | account.js, EmailIdentity | 현재 비밀번호 확인·인증 후 ID/권한 유지·모든 세션 종료 시험 | PASS |
| 재발급/비밀번호 변경 경합 | EmailIdentity | 계정 잠금 대기 중 challenge NOWAIT 획득, credential 버전 변경 중에도 RESET 같은202/사용불가 증명, 비밀번호 변경 시 기존 도전 폐기 | PASS |
| 탈퇴와 용량 제한 | AuthController, EmailOutbox | 이메일 도전/발송/계정·주소 카운터 삭제, outbox10000건 시 원자적503 및 도전 롤백 | PASS |
| 입력 길이 유지 | CredentialPolicy, AuthController | 실제 가입 증명으로5자/129자 거부·6자 허용, 기존1/128자 아이디 및128 Unicode 회귀 | PASS |
| 마케팅·임의 발송 금지 | EmailPurpose, EmailOutbox, 정책/폼 | 닫힌 목적/고정 템플릿·동의 전 미발송·마케팅 입력 없음·네트워크/브라우저 저장 시험 | PASS |
| worker의 비웹 실행 | MoapanApplication, EmailWorker, 웹 조건부 identity 빈 | EmailWorkerRoleIntegrationTest: HTTP 없는 컨텍스트 시작, 인터랙티브 인증 빈 제외, 실제 PG outbox 소비 | PASS |
| V24 기존 데이터 보존 | V25 migration | EmailMigrationIntegrationTest: V24에 합성 계정·해시·역할·활성/버전·약관·세션 bytes·구독을 먼저 저장, V25 후 동일성·해시 일치·이메일NULL·부분 상태 거부·재실행 멱등 확인 | PASS |
| SMTP/TLS(초기 이력) | SmtpMailTransport, 퇴역한 내장 서버 | JSSE 실제 TLS 정상/다른 호스트/불신CA/STARTTLS 없음4개 시험, Postfix3.11.7 실제 합성 메일함 전달 | PASS |
| 정적 UI·표준·배포 설정 | HTML/ES Modules, Compose/Helm | 390/768/1440px 합성 스크린샷, Nu26.10.12(0a70d65) 메시지0, 기본/SMTP Helm 렌더와 잘못된 입력6개 거부 | PASS(렌더만, 클러스터 미적용) |

시험 코드는 src/integrationTest/java/com/moapan/identity, src/test/java/com/moapan/identity, src/e2eTest/java/com/moapan/BrowserTest에 있다. 실제 회원·메일 주소·비밀번호·인증번호는 증빙에 출력하지 않는다.

## 3. 실행 명령과 결과

```sh
sh scripts/build/gradle-local.sh --no-daemon check integrationTest e2eTest bootJar sbom
sh scripts/test/html-check.sh
node scripts/test/mail-deployment-check.mjs
```

최종 전체 결과: 단위179 + PostgreSQL146 + 브라우저72 =397 PASS, FAIL0, SKIP0. V24 업그레이드 시험1개를 추가한 뒤 upgrade-full-regression.log의 전체 명령이 성공했다. runtime 코드와 JAR은 변경하지 않았으며 현재 호스트 JAR SHA256이 검증 이미지의 d33f34155fdabc1906a6ba4e13bbfcb4e8f3888ea6f2fb6b870d1155a460b4a3과 같음을 확인했다. 운영 DB는 별도의 읽기 전용 조회로 V24와 email_cipher 컬럼 부재를 확인했다.

SMTP 시험은 독립 테스트 프로젝트를 만들고 종료 시 컨테이너/볼륨을 정리한다. 외부 수신자를 사용하지 않는다. Postfix read-only rootfs, 제한된 메모리 큐/메일함, 내부 전용 SMTP, LMDB를 사용했다. 입출력은 필수 TLS이며 외부 전달은 `smtp_tls_security_level=verify`, `smtp_tls_verify_cert_match=hostname`이다. SMTP 수락과 실제 수신함 도착은 구분한다. 재시작 시 SMTP 메모리 큐가 소실될 수 있으며 인증번호 재요청이 필요할 수 있다.

## 4. 산출물과 공급망

| 항목 | 검증 식별 |
|---|---|
| JDK | Temurin27.0.1.0.2+1, 실제 컨테이너 java -version |
| 앱 JAR SHA256 | d33f34155fdabc1906a6ba4e13bbfcb4e8f3888ea6f2fb6b870d1155a460b4a3 |
| 앱 이미지 | sha256:e095d9a36f260e8adb6e0105c7f900ff7e55fa6644e06dcf1c1d3906e3ba66ec |
| SMTP 이미지 | sha256:96737b40de9f24d54bae15c22499dd706351db4cd534a955b0b43c70cd618a7b |
| SBOM | runtime107개 구성요소 |
| 검사 도구 | Trivy0.76.1+moapan-security.1 |
| DB 마지막 성공 동기화 | 2026-11-02T22:09:52.108676Z; 이 시점 검사에 한정, 배포 전24시간 조건 재검증 필요 |
| 공급망 | 신규 runtime5개를 Maven Central 원본 다운로드/공개 checksum과 대조, LICENSE/NOTICE/대응 소스 보관 |

두 이미지의 취약점/비밀 검사 gate는 통과했다. 전체 source의 기존 go-jose 메타데이터 경고는 기존의 검증된 source backport 근거를 유지하며 이번에 새 예외를 추가하지 않았다. Debian12/13 기반의 미해결 HIGH 이미지는 채택하지 않았다. 이미지 검사 성공은 미공개 취약점 부재의 보장이 아니다.

추가 시험과 문서만 변경한 경우 runtime 코드/JAR 동일성을 확인하고 해당 이미지 증빙을 유지한다. 재빌드로 digest가 달라지거나 의존성/설정이 바뀌면 다시 검사한다.

## 5. 발견·조치와 미완료 항목

| 항목 | 조치·현재 상태 |
|---|---|
| 테스트가 JDBC Session 대신 MockHttpSession을 재사용 | 실제 SESSION 쿠키 흐름으로 수정·재검증 |
| 발급/소비 잠금 역전 | 동일 advisory lock과 계정→challenge 순서, 잠금 전 해시 슬롯 확보로 수정·PG 경합 재검증 |
| SMTP 외부 구간 평문 fallback | verify/호스트명/CA 검증 필수로 수정 |
| 비웹 PasswordEncoder 의존성으로 worker 시작 실패 | 인터랙티브 identity 빈을 웹 역할에만 등록, 비웹 부팅 회귀 PASS |
| Postfix master root, DS-0002 HIGH | 실제 미해결 설정 경고. 제한 capabilities/내부망/읽기 전용 rootfs는 완화책. 2026-11-04 사용자 예외 미승인 확정. 구현 보존·운영 전환 보류, 경고 예외/배포 승인으로 처리 금지 |
| 운영 발신 주소/DNS | 사용자에게 확인 요청. 소스 기본 도메인 SPF는 다른 MX를 허용하므로 현재 Mac에서 임의 활성화하지 않음 |
| 외부 받은편지함 전달 | 미실행. 로컬 합성 메일함 성공으로 대체하지 않음 |
| securityCheck/acceptanceTest | release-acceptance 명령에서 securityCheck가 실제 exit1. acceptanceTest는 실행 완료/PASS가 아님 |
| 운영 배포·마이그레이션 | 미실행. 기존 V24·계정·구독·뉴스 유지 |

## 6. 검토와 추적

[PR #28](https://github.com/JunSungKang/MoaPan/pull/28)은 구독 PR27을 의존성으로 포함하며 develop 대상, 병합 보류다. 독립 리뷰의 기존 지적은 수정했고 기존 HEAD b733de82f08b24a7489581445c9994142007a829의 App 리뷰는 DS-0002 미승인 때문에 CHANGES_REQUESTED다. 이 문서나 시험 추가 후에는 새로운 현재 HEAD로 다시 리뷰한다. 실제 현재 상태는 PR을 진실 원천으로 확인한다.

로컬 증빙: .local/email-auth-20261103의 identity-delivery-tests.log, v24-upgrade-test.log, upgrade-full-regression.log, release-runtime.txt, release-image-scans.log, release-acceptance.log, smtp-memory-check.log, html-final.log 및 GitHub guard 결과. 계획/검토 의도와 실제 실행 성공을 구분한다.

## 7. 회원가입 약관 팝업 후속 검증

사용자 요청으로 새 창 약관 링크와 직접 체크박스를 가입 전용 native dialog로 바꿨다. canonical terms.html과 판 번호를 서버에서 한 번 읽어 공개 GET /api/v1/auth/terms의 불변 텍스트 트리로 제공한다. 브라우저는 HTML 문자열을 해석하지 않고 DOM API로 조립한다. 약관 문구·판 번호·기존 서버 동의 검증은 유지했다. 번역 사전은 첫 팝업 열기 때 적재하며 개인 입력을 조회 요청에 보내지 않는다.

| 검증 | 실제 결과 |
|---|---|
| 끝까지 읽기 전 동의 비활성, 중간 스크롤, 키보드 End 후 활성 | PASS |
| 동의/거절/닫기/Escape, 재열기 시 미동의·처음 위치, 가입 요청 차단 | PASS |
| 가입 입력값 보존, 포커스 순환·복귀, 새 탭 생성 없음 | PASS |
| 390×500 / 768×900 / 1440×900 본문 스크롤과 하단 버튼 가시성 | PASS, terms-dialog-{width}.png |
| 503·다른 판 거부, 다시 열어 정상 문서 재조회 | PASS |
| 영어 본문·버튼, 사전 첫 다운로드 실패→닫기→재열기 복구 | PASS |
| 강제 Trusted Types 아래 HTML 문자열 sink 차단 및 텍스트 DOM 표시 | PASS |
| 공개 GET/no-store/CSP/세션 미생성, 익명 POST 거부, 메일 큐 미변경 | PASS |
| 정적 약관에서 내비게이션·실행 요소·HTML 속성 제거, 불변 읽기 모델 | PASS |

최종 기능 회귀는 단위181 + PostgreSQL147 + Playwright76 = **404 PASS, FAIL0, ERROR0, SKIP0**다. 집중 팝업 시험6개와 Nu HTML 메시지0, CSS 중복 선언/문서 연결 검사도 통과했다. 증빙은 .local/email-auth-20261103/terms-csp-retry.log, terms-release-verification.log, terms-html-final.log와 build/test-results, build/reports/screenshots에 있다. 독립 사전 리뷰의 실패한 번역 Promise 캐시 문제를 수정하고 실제 일시 다운로드 실패 후 재시도를 검증했다. 보안 헤더를 적용하면서 드러난 시험의 unsafe-eval 의존 대기는 locator assertion으로 바꿨으며 제품 CSP는 약화하지 않았다.

이번 JAR SHA256은 `dff536cc7cb8532f65b1a1164ddbeea98c558dfc38312edb7f5690f4f3c51b2b`다. 소스/JAR/SBOM 보안 재검사 결과는 build/reports/security를 따른다. 기존 SMTP DS-0002 HIGH와 전체 보안 게이트 보류는 그대로이며 경고 예외나 위험 수용을 추가하지 않았다. 이 새 JAR의 컨테이너 이미지는 만들거나 운영에 올리지 않았다. 앞 절의 기존 이미지 검사는 이번 변경 이미지 검사로 간주하지 않는다. 실제 서비스/V24는 유지하며 메일 수신 검증은 사용자가 직접 수행할 예정이고 발신 설정·서비스 전환·병합은 미실행이다. 실제 현재 커밋과 독립/App 리뷰 상태는 PR28에서 확인한다.

## 8. 스크롤 안내와 실제 약관 체크박스

스크롤 영역 오른쪽에 native scrollbar 스타일과 아래로 읽기 화살표 버튼을 추가했다. 화살표는 본문을 한 화면씩 내리며 내용이 모두 보이거나 끝에 도달하면 숨긴다. 본문과 겹치지 않도록 별도 줄의 오른쪽에 배치했다. 가입 폼의 실제 필수 체크박스는 팝업 동의 때 선택되며, 미동의 체크 시 검토를 열고 직접 체크를 허용하지 않는다. 기존 체크를 해제하면 내부 동의도 취소한다. 거절/닫기/Escape/재열기도 체크를 해제한다.

집중 브라우저 시험7개에서 자동 체크·해제·Space 키 검토·native 필수검증과 가입 차단, 화살표 클릭 스크롤·끝 숨김·한영·입력 보존을 검증했다. 전체 회귀181/147/77 =405 PASS, FAIL/ERROR/SKIP0이며, 본문과 안내를 분리한 최종 CSS 이후 check/e2eTest/bootJar/sbom/securityCheck를 다시 실행해 단위181·UI77을 재검증했다. Java/DB 코드는 변경하지 않았으며 PostgreSQL147 결과는 앞 전체 실행에서 확인했다. HTML 메시지0, CSS 중복 검사 PASS. 최종 JAR SHA256은 b99a4f25867099685bac3745ef569d4bcf808d29b5cbd1323f1c6b720a1a7df9이며 JAR/SBOM 스캔 취약점0이다. 전체 securityCheck는 기존 Postfix DS-0002 HIGH 때문에 실제 exit1이며 예외를 추가하지 않았다. 증빙: terms-affordance-focused.log, terms-affordance-release.log, terms-affordance-final.log, terms-affordance-html.log. 합성 캡처는 terms-dialog-{width}.png와 terms-consent-{width}.png이며 실제 서비스 화면이 아니다. 서버·DB·약관 문구·메일 설정과 기존 보안 게이트 상태는 변경하지 않았다.

## 9. 버튼 제거·오른쪽 스크롤바 정정

사용자 정정에 따라 아래로 읽기 버튼과 관련 HTML/JS/CSS/번역을 제거했다. 약관 본문 오른쪽에 24px 독립 트랙과 손잡이를 항상 표시한다. 실제 본문 위치와 손잡이 크기/위치·ARIA 값을 동기화하며 포인터 캡처 드래그, 트랙 클릭, Arrow/Page/Home/End를 지원한다. 본문 자체 휠·터치·키보드 스크롤은 유지한다. 짧은 본문에서는 전체 높이 손잡이와 비활성 상태를 표시한다. 운영체제의 native overlay 자동 숨김과 관계없이 표시되며 본문을 가리지 않는다. 동의 게이트와 체크박스 동기화는 유지했다.

집중 브라우저7개와 Nu HTML 메시지0/CSS 검사를 통과했다. 390/768/1440에서 실제 마우스 드래그·트랙 클릭·키보드·끝 값100/동의 활성, 짧은 본문 비활성·한영 표시를 검증했다. 합성 캡처는 terms-dialog-{width}.png다. 최종 check/e2eTest/bootJar/sbom/securityCheck에서 단위181·전체UI77 PASS/FAIL·ERROR·SKIP0을 확인했다. Java/DB 코드는 변경하지 않았으며 PostgreSQL147은 앞 전체 실행의 결과를 유지한다. 최신 JAR SHA256은 2e5023dc707b4064d8d2302a3aa0c58fca28d888aca1058cd08550d193e87b6b이며 JAR/SBOM 스캔 취약점0이다. 전체 securityCheck는 기존 Postfix DS-0002 HIGH로 실제 exit1, 예외를 추가하지 않았다. 증빙 terms-scrollbar-focused.log / terms-scrollbar-final.log / terms-scrollbar-html.log. 운영/SMTP/DB/병합은 변경하지 않는다.

## 10. 약관 팝업 디자인 정돈

사용자 캡처 피드백에 따라 외곽 테두리·스크롤 손잡이·닫기 버튼 상자의 무게를 줄이고, 종이색 외곽/밝은 읽기 패널/안쪽 여백을 구성했다. 제목21px·본문14px·소제목15px의 UI 글꼴 위계, 보조 거절/주 동의 버튼과 비활성 색을 구분한다. 24px 스크롤 조작 영역은 그대로이며 시각적 손잡이만 가늘게 했다. 키보드 포커스를 없애지 않고 모달 안쪽으로 제한한다. 실제 초기 상태와 읽기 완료·키보드 상태를 별도 캡처로 검수했다.

집중 브라우저7 PASS. 390/768/1440의 terms-dialog-{width}.png(초기), terms-dialog-ready-{width}.png(읽기 완료)와 동의 체크 캡처를 확인했다. 약관 본문/서버/JS 동작/CSP/DB는 변경하지 않았다. 최종 check/e2eTest에서 단위181/UI77 PASS, FAIL/ERROR/SKIP0과 Nu 메시지0/CSS/docs 통과를 확인했다. JS/Java/DB 동작 코드는 바꾸지 않았으며 기존 PostgreSQL147 결과를 유지한다. 새 JAR SHA256은 684f13607aaeae9216f8ac373d35f82fff48ea7968e182208390c6978daef6fb이며 JAR/SBOM 스캔 취약점0이다. 전체 securityCheck는 기존 Postfix DS-0002 HIGH로 실제 exit1이며 예외를 추가하지 않았다. 증빙 terms-design-focused.log, terms-design-final.log, terms-design-html.log. 운영·DB·SMTP·이미지·병합은 변경하지 않는다.


## 11. 사용자 기동 MailBox 실제 연결 재점검

2026-11-04 KST에 사용자가 별도 기동한 MailBox를 읽기 전용으로 점검했다. 기존 합성 SMTP fixture의 성공을 이번 서버의 성공으로 재사용하지 않는다.

| 항목 | 실제 결과 |
|---|---|
| SMTP/IMAP/Webmail 컨테이너 | 3개 실행 확인 |
| SMTP strict TLS NOOP | FAIL: curl exit64, STARTTLS not supported. 메일 DATA 전송 없음 |
| SMTP 설정 | TLS 인증서·STARTTLS·SMTP AUTH 미설정, root 실행, 공개25번, 사설망 전체 mynetworks |
| 실제 SMTP 이미지 검사 | FAIL: HIGH33/CRITICAL1 원시 검출, 게이트 exit1. 중복 패키지/영향 검토 필요 |
| 최신 앱 이미지 준비 | PASS: UID10001, Temurin27.0.1.0.2+1, JAR 호스트/이미지 SHA256 일치, 이미지 gate PASS |
| 실제 수신함 도착/외부 배달 | BLOCKED/미실행: TLS 연결 실패 후 인증번호를 전송하지 않음 |
| 서비스 전환 | BLOCKED/미실행: 보안 게이트·이전 root 예외 미승인 유지 |
| 기존 서비스 | HTTPS 로그인200/healthy, 실행 이미지24fc8045a50c 유지 |

SMTP 이미지 ID는 sha256:594e743418aadf1ed23478ba448bcb6cad5809c99858ecca9c06e43d7de12c99다. DB Version2, UpdatedAt2026-11-02T19:15:11Z/DownloadedAt2026-11-02T22:09:52Z/NextUpdate2026-11-03T19:15:11Z이며 실제 검사 시각2026-11-03T15:40:12Z 기준24시간/다음갱신 조건을 통과했다. 이전 검사 결과나 오래된 DB로0건 성공을 만들지 않았다.

[Debian Postfix CVE-2026-43964](https://security-tracker.debian.org/tracker/CVE-2026-43964)는 설치3.7.11-0+deb12u1을 vulnerable로 표시하고 bookworm no-dsa/minor 주석도 제공한다. 스캐너 HIGH와 공급자 우선순위를 구분하며 미수정 상태를 성공으로 바꾸지 않는다. CRITICAL [CVE-2023-45853](https://security-tracker.debian.org/tracker/CVE-2023-45853)는 MiniZip에 관한 것으로 Debian은 해당 zlib에서 취약 MiniZip 바이너리를 만들지 않는다고 명시한다. 실행 이미지의 zlib1g1:1.2.13.dfsg-1/별도 MiniZip 패키지·파일 미검출을 확인했지만 기존 gate/ignore를 수정하지 않았다. 전체34건을 검토 완료하거나34개 독립 취약점으로 주장하지 않는다.

최신 앱 이미지 ID는 sha256:dcbd1540948a855db099880321384086e9f090d767dd52cb81e6f5c0538f5d86이며 내장 JAR은684f13607aaeae9216f8ac373d35f82fff48ea7968e182208390c6978daef6fb다. 앱 이미지 검사 PASS와 전체 서비스 승인/메일 서버 검사 PASS는 별개다. 이번에는 기능 전체 시험을 재실행하지 않았으며 앞 절의 결과와 구분한다. 증빙은 .local/mailbox-live-20261104/{strict-tls-probe.txt,smtp-image-scan.log,app-image-build.log,app-image-scan.log}와 build/reports/security/image-{imageId}.json이다.


## 12. STARTTLS 적용 후 실제 전달 재검증

2026-11-04 09:16~09:24 KST 사용자 변경을 다시 점검했다. MailBox는 같은 SMTP 이미지594e743418aa를 사용하며 smtpd_tls_security_level=may/사설 CA 인증서를 추가했다. 기본 OS 신뢰만 사용하는 연결은 issuer 검증에서 거부됐고, 사용자 MailBox 원본의 공개 CA를 읽기 전용으로 확인해 SMTP 전용 CA로 지정한 연결은 통과했다. TLS/호스트 검증을 끄거나 JDK·호스트 전역 truststore를 바꾸지 않았다.

| 항목 | 실제 결과 |
|---|---|
| CA·인증서·호스트 검증 | PASS: 운영자 CA chain/현재 유효기간/서버 이름 확인. CA SHA256 EF381BA95920F5D1032771255C8BB404B4993034F7C9F0B6A0951E15EA06D15C |
| 실제 생산 SMTP adapter | PASS: 현재 SmtpMailTransport로 합성 메일1건 발송, STARTTLS 필수·CA/호스트 검증 유지 |
| 수신함 도착 | PASS: MailBox의 기존 테스트 수신함 Maildir에 UUID가 일치하는 메일1건 확인, Received TLS 확인 |
| 비루트 앱 이미지 Docker 연결 | PASS: UID10001/읽기 전용/cap ALL 제거한 일회 컨테이너에서 mailbox_default의 SMTP 이름·CA 검증 NOOP 성공. 실제 worker 미변경 |
| 직접 연결 Compose | PASS(local/public/server): 내장 SMTP 생성 없음, worker의 기존 모델 네트워크 유지, web은 backend만 사용, 키/공개CA 파일 secret, private TLS key 미마운트, 필수 호스트 누락 거부 |
| 최신 DB 동기화 | PASS: Version2/UpdatedAt2026-11-03T19:16:30Z/DownloadedAt2026-11-04T00:17:15Z/NextUpdate2026-11-04T19:16:30Z |
| 현재 SMTP 이미지 재검사 | FAIL: HIGH33/CRITICAL1 원시 검출, 게이트 exit1. 개별 영향 판정은 제11절의 한계 유지 |
| 최신 앱 이미지 재검사 | PASS: 기존 dcbd1540948a 이미지 digest에 최신 DB로 다시 검사 |
| 전체 securityCheck | FAIL: 실제 exit1, scripts/mail/Dockerfile의 DS-0002 HIGH 유지. 예외/기준 변경 없음 |
| 실제 가입·복구 서비스 전환/외부 수신함 | BLOCKED/미실행: 기존 web/worker/V24 유지. Gmail 등 인터넷 수신함으로 발송하지 않음 |

합성 메시지 ID는 e5717e42-6a37-4fe7-b22a-dc35165032eb, 수신 파일 SHA256은88d581bef1b686b6fcaafc299f9586e85531a6f7d506783e38f161fc788885fb다. 테스트는 운영자 기존 수신함을 사용했고 실제 회원정보·비밀번호·인증번호를 넣지 않았다. 수신함의 다른 메시지·계정은 조사하지 않았다. 첫 adapter 실행은 오래된 시험 classpath에서 Jakarta Mail 클래스를 찾지 못해 발송 전에 실패했다. 현재 의존성으로 classpath를 다시 만든 뒤 성공했으며 준비 실패를 메일 전달 성공으로 합산하지 않는다. 임시 Java probe는 검증 뒤 삭제했다.

직접 연결 설정은 scripts/deploy/compose-mail-external.yaml에만 추가했다. Compose 공개 설정 시험의 최초 실행은 시험용 MOAPAN_PUBLIC_HOST가 없어 실패했고, 필요한 환경을 명시한 재시험에서 통과했다. MailBox 원본/컨테이너 설정과 실제 .env·이메일 키·운영 DB를 변경하지 않았다. Java/JS/DB 제품 코드는 동일해 기존405 시험을 이번에 재실행했다고 주장하지 않는다. 증빙은 .local/mailbox-live-20261104의 smtp-probe-metadata.json/smtp-adapter-live.log/nonroot-network-starttls.log/external-compose-check.json/starttls-security-check.log/starttls-{smtp,app}-image-scan.log와 build/reports/security다.


## 13. 이 프로젝트의 내장 SMTP root 원인 제거

사용자 지시로 먼저 [외부 MailBox 결함 조치 프롬프트](../07-operation/mailbox-security-handoff.md)를 작성했다. 다른 프로젝트는 수정하지 않는다. 이 프로젝트에서 외부 MailBox와 중복된 Postfix daemon의 Dockerfile/소스/준비·Compose/Helm 배포/전용 smoke11파일을 제거했다. application의 SmtpMailTransport·암호화 outbox·가입/복구·V25는 보존한다. 서버를 실행하지 않도록 실제 책임을 분리한 것이며 USER 위장이나 source/JAR/SBOM/Helm/image scanner 제외·위험 예외를 추가하지 않는다.

외부 SMTP Compose의 local/public/server와12개 설정 누락 거부, Helm STARTTLS/SMTPS/비루트/서버 미생성/4개 메일 설정 거부를 검사한다. 기존 mail.server 옵션은 schema에서 거부하며 숨은 서버로 fallback하지 않는다. 키 준비는 프로젝트 내부 키 생성·기존 키 보존·유효성·파일경계/링크/권한 검증만 담당하고 SMTP CA나 서버 개인키를 만들지 않는다. Java JSSE fixture로 STARTTLS 성공·호스트 오류·불신CA를 보강하고 기존 SMTPS/STARTTLS 미지원 거부를 유지한다.

검증 진행 중: 전체 Gradle 계약/check/integrationTest/e2eTest/bootJar/sbom/securityCheck/acceptanceTest와 최종 앱/빌드 이미지 결과는 후속 확정 전까지 PASS로 표시하지 않는다. 이전 단위181/PG147/UI77 결과를 이번 실행 결과와 합산하지 않는다. 실제 .env/이메일 키/운영 DB·서비스/외부 MailBox 컨테이너는 변경하지 않는다. 외부 MailBox의 마지막 고위험 검출과 운영 차단은 이 프로젝트 소스 수정으로 해결됐다고 주장하지 않는다.


최종 이 프로젝트 결과: **clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest PASS**, 19 tasks 실행. JUnit 단위184/PG147/UI77 =408 PASS, FAIL/ERROR/SKIP0. 키 경계/보존7건, Compose3모드/12누락 거부, Helm STARTTLS·SMTPS와4메일설정 거부·기본3거부, Nu 메시지0을 확인했다. 첫 clean은 새 시험의 build 디렉터리 준비 누락으로 sourceCheck 실패했고 이를 고친 뒤 동일 clean 전체 명령을 재실행해 통과했다. 판정 기준·assertion·timeout을 낮추지 않았다.

소스 DS-0002 HIGH는 실제 서버 구성 퇴역 후0건이며 source/JAR/SBOM/두 Helm 시나리오 및 최종 앱·빌드 이미지 gate PASS다. source에는 JOSE3건과 x/crypto UNKNOWN2건(중복)이 원시 검출됐으며, JOSE에는 종전의 검증된 백포트/영향 근거를, UNKNOWN에는 기존 OpenPGP 코드 부재 판단을 유지했고 새로운 ignore/예외나 scanner 제외를 추가하지 않았다. JAR/SBOM·최종 이미지 취약점/secret은0건이다. JAR684f13607aaeae9216f8ac373d35f82fff48ea7968e182208390c6978daef6fb, runtime058f45121034986b64c7f64f4e20b26654f494421a7cb00deddd3d5065e4945e, builder23bd0e0bcc555c0186f1a4baeb43809e0a22fcb9ffb4e0cb5067709a89e80fc9를 대조했다.

추가 사용자 요청에 따라 미사용 서버/데몬·시작 설정을 조사했다. 체크섬이 기존 소유 소스와 같은 생성 SMTP context7파일과 미사용 내장 SMTP 이미지1개를 제거했다. 컨테이너 마운트/열린 파일/현재 시작 참조가 없는 이전 Mac Ollama·llama/RPC 런타임2곳116파일과 불필요한 검증된 소스 압축파일1개, 현재 컨테이너/소스/최종 검증 이미지가 참조하지 않는 과거 프로젝트 이미지 태그36개(실제 이미지ID34개)를 제거했다. 전역 prune/force/볼륨 삭제·타 프로젝트 변경은 하지 않았다.

현재 Java85파일의 compiler symbol(비프레임워크 private264/무주석 type113) 참조 후보0·오류0, HTML13개 진입점의 JS42개 모두 연결/누락0을 확인했다. record 자동 필드·프레임워크 진입점은 삭제 근거로 삼지 않는다. 현재6서비스와 정상 종료된 필수 migration/model-init3개를 보존했다. host Node2개는 ChatGPT CUA 도구이고 TCP listener가 없어 앱 데몬으로 취급하지 않았다. 기존 DB V24와 앱24fc8045a50c·HTTPS200 유지, 실제 회원/키/모델/볼륨은 미변경이다. 외부 MailBox의 고위험과 root 미승인은 미해결이며 본 프로젝트 gate PASS를 외부 해결/운영 전환으로 확대하지 않는다.

[최종 기계 증빙](evidence/smtp-boundary-cleanup-20261104.json). 실행 로그는 .local/smtp-boundary-fix-20261104의 clean-final-retry.log/final-image-scans.log/helm-check.log이며 최초 실패도 보존했다. 임시 분석 Java 코드는 제거했고 삭제 코드·실행파일의 별도 백업은 만들지 않았다.

최종 추가 점검에서 퇴역한 native 런타임을 호출하는 이전 비교 실행기와 localhost 전용 측정기·일회성 출처 초기화 도구3개도 제거했다. 실행 프로세스/열린 파일/현재 운영 참조가 없음을 확인했고 내용·자격증명을 복사하거나 실행하지 않았다. 현재 관리자 출처 API와 Docker 모델 검증은 유지한다. 이 변경은 Git에 포함되지 않던 로컬 폐기 도구와 결과 문서뿐이며 검증한 앱/빌드 산출물은 동일하다.
