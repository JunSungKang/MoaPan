# 이메일 인증·복구 검증서

> 문서번호: MP-DOC-038 · 상태: 로컬 기능 검증, 보안/발신 설정 승인 대기 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

## 1. 검증 범위와 판정

사용자 승인 요구인 필수 수신 이메일 가입·인증, 비밀번호 찾기/재설정, 기존 회원 이메일 등록·변경, 목적 제한 고지와 기존 계정 보존을 검증한다. REQ-03/08/10/11의 이메일 예외다. 아이디1~128자·비밀번호6~128 Unicode code point, 개인 관심사 비전송을 유지한다. 결제 구현, develop/main 병합은 제외한다.

현재 운영은 기존 이미지/V24다. 아래 로컬 합성 검증 성공은 신규 기능의 운영 반영이나 실제 외부 받은편지함 전달 성공을 뜻하지 않는다. Postfix root 실행의 DS-0002 HIGH가 남아 전체 보안 게이트와 acceptanceTest는 미완료다.

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
| SMTP/TLS | SmtpMailTransport, scripts/mail | JSSE 실제 TLS 정상/다른 호스트/불신CA/STARTTLS 없음4개 시험, Postfix3.11.7 실제 합성 메일함 전달 | PASS |
| 정적 UI·표준·배포 설정 | HTML/ES Modules, Compose/Helm | 390/768/1440px 합성 스크린샷, Nu26.10.12(0a70d65) 메시지0, 기본/SMTP Helm 렌더와 잘못된 입력6개 거부 | PASS(렌더만, 클러스터 미적용) |

시험 코드는 src/integrationTest/java/com/moapan/identity, src/test/java/com/moapan/identity, src/e2eTest/java/com/moapan/BrowserTest에 있다. 실제 회원·메일 주소·비밀번호·인증번호는 증빙에 출력하지 않는다.

## 3. 실행 명령과 결과

```sh
sh scripts/build/gradle-local.sh --no-daemon check integrationTest e2eTest bootJar sbom
sh scripts/test/html-check.sh
sh scripts/test/smtp-check.sh
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
| Postfix master root, DS-0002 HIGH | 실제 미해결 설정 경고. 제한 capabilities/내부망/읽기 전용 rootfs는 완화책. 사용자 위험 수용 답변 전 예외·배포·병합 승인 금지 |
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
