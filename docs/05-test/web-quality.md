# 웹 품질·기능별 패턴 리팩토링 검증서

> 문서번호: MP-DOC-040 · 상태: 제품 검증 완료·실서비스 전환 후 인도 검증 · 작성: 개발 담당 · 검토: 독립 리뷰/SEO 감사 담당

## 1. 범위와 기준

사용자가 승인한 접근성·모바일/데스크톱100점 목표·미사용 코드 제거·기능별 패턴 재검토다. 기준11fad44(PR28, PR27 포함)와 운영 앱24fc/V24를 구분한다. 최초 감사 범위에서는 타 프로젝트·메일서버·운영 데이터/키/모델·CI/CD·develop/main 병합을 제외했다. 이후 사용자가 별도로 서비스 전환·기존 MailBox 연동·develop 병합을 승인했으며 해당 인도 결과는 제6절을 따른다. main/CI/CD/타 프로젝트 변경은 계속 제외한다. Lighthouse100은 자동검사 범위의 점수이며 WCAG 전체 적합성·실사용자 성능·검색 순위를 보증하지 않는다.

기준 공개 HTTPS PageSpeed: https://pagespeed.web.dev/analysis/https-175-195-62-165/aqry4fwe5w?form_factor=desktop (2026-11-04 14:08 KST). 모바일/데스크톱 성능·접근성·권장사항·SEO 모두100, CLS0. 모바일 LCP1374ms/데스크톱338ms. 이는 기존 운영본이며 이번 수정 후보의 배포 증거가 아니다.

독립 소스 감사와 별도 axe4.14 실행에서 동적132상태 전 단계의126상태 중19실패를 확인했다. landmark·편집기 글자대비·키보드 스크롤 문제이며 상태별 중복을 포함한다. 정적 Nu만으로 동적 화면 전체를 판정하지 않는다. 최초 audit의 격리DB 마이그레이션 준비 누락으로 JPA 검증 실패가 발생했고, 명시적 Flyway 선행 실행으로 시험 환경을 수정했다. 판정/보호 정책은 약화하지 않는다.

## 2. 체크리스트

- [x] WQ01 전체 HTML/Java/JS/CSS·스크립트 진입점과 기준점검
- [x] WQ02 기사 메타정보의 금지 ARIA 이름을 실제 보조 텍스트로 교체
- [x] WQ03 입력 경계 전용 대비 토큰·편집기 텍스트 대비·언어 영역/표 키보드 접근 보완
- [x] WQ04 클릭/키보드/hash 공통 패널 최초 활성화와 숨은 선조회 제거
- [x] WQ05 원고 작성언어 보존(구자료 und)·이미지의 검증한 치수 저장/표시
- [x] WQ06 세션 대기와 지면 요청을 병렬화하고 기존 서버 계정별 quota 유지
- [x] WQ07 API/편집 검토 메시지 단일 catalog와 own-key 조회
- [x] WQ08 출처 설정의 배치별 불변 Specification 컴파일
- [x] WQ09 원고 Value Object·닫힌 검증/표시 Strategy·기사 Factory 분리
- [x] WQ10 미사용 심볼/모듈/CSS/스크립트 최종 점검 및 제거 검증
- [x] WQ11 전체 기능별 패턴과 유지/변경 이유 확정
- [x] WQ12 전체 unit/PostgreSQL/Playwright/Nu 회귀
- [x] WQ13 최종 JAR/SBOM/이미지·도구 보안 및 current-head 독립 검토
- [x] WQ14 모든 제품 변경 뒤 최종 SEO·모바일/데스크톱 Lighthouse 감사

## 3. 기능별 패턴 선정

| 기능 | 선정/유지 패턴 | 적용과 이유 |
|---|---|---|
| 인증·이메일 | Application Service + Ports/Adapters | 인증번호/복구 트랜잭션과 SMTP 전송 포트를 분리한 구조 유지. 감사 범위에서 보안 로직 재작성하지 않음 |
| 접근·세션 | Security Filter Chain + Policy | 역할·세션·CSRF·입력 경계를 체인으로 유지 |
| 구독 | Entitlement Policy + DB Provider | 매 조회의 최신 권한/회수 확인, 캐시로 권한 고정하지 않음 |
| 사이트·주소 | Transaction Script + Repository + Optimistic Lock | CRUD와 상태변경을 동일 트랜잭션/버전 규약으로 유지 |
| 수집 | 불변 Specification + Lease Worker | CollectionScope를 배치당 한 번 생성. fetch와 저장 경계 각각 검사하되 기사마다 JSON/날짜를 다시 읽지 않음 |
| 기사·요약 | 순수 Domain Service + Presentation Factory | 기존 추출형/태그 규칙 유지, 기사 DOM 생성은 요청·계정 상태 없는 articleCard로 분리 |
| 분류 | Model Port/Adapter + Closed Validator | 기존 허용된 주제/스키마/입력예산 유지, 모델 정책 bytecode 변경하지 않음 |
| 번역 | Model Port/Adapter + Retry State Machine | 검증/재시도·lease·guardrail 유지. 선택한 경량모델/용어표 변경 없음 |
| AI 공통 | Guard/Token/Stream 검증 파이프라인 | 입력과 출력을 hard limit으로 제한하는 경계 유지 |
| 지면·검색 | Query Read Model + 브라우저 Ranking Strategy | 작은 초기 entry에서 후보 API를 먼저 시작하고 동일 Promise를 표시 모듈이 한 번 소비. 서버는 같은 endpoint에서 인증계정 quota를 강제 |
| 개인 관심사 | 로컬 Repository + 순수 Ranking Strategy | 계정별 IndexedDB·무전송·내보내기 경계 유지 |
| 뉴스레터 | Manuscript Value Object + 블록 Strategy | 검증 책임을 서비스에서 분리하고 reader/preview/export는 동일 ownerDocument 기반 renderer 사용 |
| 광고 | Versioned Settings Service + UI State Machine | 닫힌 설정/권한/캐시 유지, loading/saving과 필드 revision으로 늦은 조회·저장 경합 방지 |
| 관리자 UI | Observer 기반 일회 Reveal Lifecycle | 클릭 이벤트에 종속되지 않고 실제 보이는 패널을 한 번 초기화, 기존 편집값 보존 |
| 공통 표시/오류 | UI/Original DOM Factory + Page-scoped Message Catalog | UI 원문 키/속성은 WeakMap 보존, 기사·원고·회원/운영 명칭은 raw 표시. 지면 사전81개만 읽고 기능 사전은 조건부 적재, prototype 키 조회 차단 |
| 서버 캐시 | Versioned Cache Aside + bounded Snapshot | 기존 Caffeine 예산/저장 해시/변경 무효화·권한별 후검사 유지 |
| 발견/SEO | 고정 공개 문서 정책 + ResourceTransformer/응답 Filter | 공개4문서의 최초 HTML/HTTP canonical을 하나의 설정 origin으로 동기화. 네 문서만 mtime 기반 캐시, 나머지 noindex/권한 경계 유지 |

패턴 이름에 맞추기 위해 동작 중인 클래스/테스트를 무조건 분할하지 않았다. 현재 선택을 적합하게 유지하는 기능도 전체 조사 결과에 포함한다.

## 4. 도구·재현 명령

Lighthouse13.6.0/axe-core4.14.0은 테스트 도구다. npm 잠금/무결성·공식 registry와 glob14.0.3/minimatch11.0.4 보안 override를 사용하고 .local/web-audit-tools에만 설치하며 운영 의존성/Node 웹서버/프런트 빌드/CI를 추가하지 않는다. scripts/test/prepare-web-audit.mjs로 준비한다.

- 전체 회귀: sh scripts/build/gradle-local.sh --no-daemon clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest
- 동적 접근성/SEO/성능: sh scripts/build/gradle-local.sh --no-daemon webQualityAudit
- 정적 HTML: sh scripts/test/html-check.sh
- 코드 심볼: modelSmokeClasspath 생성 후 고정 JDK로 scripts/test/JavaUsageAudit.java 실행

webQualityAudit는 폐기 PostgreSQL/합성42기사·실제 Spring HTTP와 합성 API의13페이지·dialog/탭을 사용한다. 운영 계정·기사·메일·외부 검색어를 복사하지 않는다. 실행이 끝나면 테스트 서버/DB를 종료한다. 공개사이트와 격리 후보 결과는 별도로 보고한다.

## 5. 최종 결과

검증 시각: 2026-11-04T07:00:55.066Z. 공개4문서 × 모바일/데스크톱 총8회에서 성능·접근성·권장사항·SEO가 모두100이다. 지면의 최종 모바일 LCP는1893ms, TBT/CLS0이다. 같은 격리 후보의 앞선99점/LCP2206ms와 구분하며 운영 기존100점이 이번 패치로 향상됐다고 주장하지 않는다.

- 전체 clean 계약: 424 PASS(단위190·PostgreSQL148·Playwright86), FAIL/ERROR/SKIP0. 마지막 미사용 JS import 제거 후 check/집중UI9/bootJar/SBOM/securityCheck 재검증도 PASS다. 전체86개 XML은 별도 로컬 실행 증빙에 보존한다.
- axe4.14: 13페이지·상세·탭·대화상자,2언어,390/768/1440px의132상태 실패0. 초기126상태의19개 실패상태를 보완했다. Nu HTML13개 오류/경고0.
- Java141파일/운영 private296선언을 심볼 분석했다. 참조0 후보18개는 인스턴스 생성을 차단하는 의도된 private utility constructor라 유지한다. 미사용 import0. 브라우저45모듈 모두 진입점에서 도달하며 누락0, 보수적 JS 함수/import 후보0이다. 공용 CSS가 한 페이지에서 쓰이지 않는다는 이유로 타 페이지의 규칙을 삭제하지 않는다.
- 제거: 실제 미사용 필드2·호출 wrapper1·import, 폐기 사전2모듈(기능 사전으로 통합), 미사용 문구4·덮어써지던 중복4·중복 원문 표시 할당. 실제 JAR에서 구 사전/원고 inner DTO가 남지 않음을 확인했다.
- JAR SHA256: e03b4ef23d680c45528f2804af32c2fcf1fec73a1a530780f7c1721c1138f998. runtime sha256:d1e49e6d6c1010acc7230be1d7a9247bccb5c2422c78675ebd246c76ac848e5e, builder sha256:ba89ca219bf4e0ced0a61f75b9e07bba9a3ade6f1b15f41e026171edf818e1d4, Linux arm64/UID10001. JAR과 실행 이미지 내부 JAR 일치. 두 이미지 취약점/secret0, JAR/SBOM0, npm감사0. 기존 source JOSE/crypto5건의 정확한 backport/미노출 근거는 유지하고 새 예외는 만들지 않았다. 최종 이미지의 정상 HTTPS/14개 체인 TLS 거부·공식4루트 부재·custom truststore 없음도 확인했다.
- 권한/noindex/no-store/robots/sitemap의13개 실제 HTTP 경계, 최초 HTML/HTTP canonical4개와 루트 대표 URL이 일치한다. 포털 실제 색인/순위·CrUX·보조공학 전체 적합성은 검증하지 않았다.

[실행 증빙](evidence/web-quality-20261104.json)에 시각·메트릭·진단·digest를 기록했다. 점수100이어도 일부 비점수 진단(공용 CSS의 페이지별 미사용·압축/전송 기회 등)은 남으며, 이를 미사용 프로젝트 코드 또는 오류0의 증명으로 바꾸지 않는다. [Lighthouse 점수 설명](https://developer.chrome.com/docs/lighthouse/performance/performance-scoring)과 [W3C 자동 도구의 한계](https://www.w3.org/WAI/test-evaluate/tools/selecting/)를 따른다.

최초 감사 마감 시점에는 실행 서비스/DB V24/MailBox/키/모델을 교체하지 않았고 develop/main 병합도 수행하지 않았다. 외부 MailBox 운영 전환 보류는 별도이며, 이번 후보 검증을 운영 배포로 보고하지 않는다. [PR29](https://github.com/JunSungKang/MoaPan/pull/29)에서 분리된 코드 검토와 SEO 최종 판정 PASS, 코드 head4b6a0ab의 App formal APPROVED를 확인했다. 이후 문서만 마감하며 최종 current-head 승인은 같은 PR의 최신 리뷰 기록을 정본으로 확인한다. 제품 seal·JAR·이미지와 마지막 SEO 결과는 불변이다.


## 6. 승인된 서비스 인도·MailBox 실제 연동

후속 사용자 승인에 따라 동일 검증 runtime/JAR를 실제 web/worker에 반영하고 운영 DB를 V25로 전환했다. src/제품 JAR/이미지는 제5절과 동일하며 새 파일은 SMTP AUTH 클라이언트 overlay와 구성 시험·운영 문서다. 전체 저장소 seal은 배포 스크립트 추가로 달라지므로 최초 감사 seal을 새 저장소 전체의 seal이라고 주장하지 않는다.

암호화 백업→격리 복원→V25→기존/새 이미지 JPA 기동을 검증한 뒤 운영을 전환했다. 중지 구간에 비교한 기존34테이블 건수/행 SHA256 집계가 모두 일치했고 DB/proxy/두 모델의 ID·시작시각·이미지와 원래 볼륨/키를 보존했다. 웹 healthy·worker 실행과 동일 JAR SHA256을 확인했다. 검증 후 기사496·기존계정2·뉴스레터1·기존구독1을 유지하며 합성계정·인증 challenge·대기메일0이다.

외부 MailBox 새 이미지의 취약점/secret0과 필수 STARTTLS·CA/hostname·SMTP AUTH를 확인했다. 실제 운영자 수신함에서 고유 시험 메일을 수신했고 실서비스 HTTPS62개 assertion이 통과했다: 이메일 인증 가입/실제 인증번호 수신·가입 후 권한·CSRF·구독 부여/회수·회수시각 종료·404·복구 인증번호·비밀번호 변경·모든 기존 세션 무효화·새 비밀번호 로그인·코드 재사용 거부·시험 계정 삭제다. 재발송 제한429는 Retry-After를 실제 기다린 뒤 시험했고 한도를 우회하지 않았다.

후속 check/mailDeploymentCheck/securityCheck PASS, 기존 SMTP 구성3모드/12누락 거부와 AUTH 추가3모드/6누락 거부 PASS. 최초 AUTH 시험의 Compose network null 값을 부재로 잘못 판정한 실패는 키 존재 검사로 고쳐 재검증했고 실패를 성공으로 합산하지 않았다. 전체424/axe132/Nu13/8회Lighthouse는 제5절의 동일 후보 증빙을 유지하며 이번 배포에서 전체 회귀를 다시 실행했다고 표시하지 않는다. [서비스 인도 증빙](evidence/web-quality-delivery-20261104.json)과 [현재 재시작 절차](../06-transition/deployment.md)를 따른다.

외부 MailBox master root는 외부 운영 관찰로 기록하고 과거 내장 SMTP 예외를 승인 처리하지 않았다. 이 프로젝트는 SMTP 서버를 추가하지 않았다. 인터넷의 모든 수신자 전달/포털 실제 순위/오프호스트 자동 백업/실제 결제는 검증하지 않았다. GitHub 최종 head 승인·병합은 [PR29의 현재 기록](https://github.com/JunSungKang/MoaPan/pull/29)을 정본으로 확인한다.

최종 실서비스 SEO 독립 재점검(2026-11-04T10:25:29Z): 공개 HTTP13/canonical4/robots/sitemap/404·noindex PASS, 익명 접근성 한·영 ×390/768/1440px의90상태 실패0. 실제 HTTPS 지면 Lighthouse13.6.0을 fresh profile/cache로 각1회 측정해 모바일·데스크톱 성능/접근성/권장사항/SEO 모두100(raw1.0)이다. 모바일 LCP1315.8785ms/TBT68ms/CLS0, 데스크톱 LCP286.029ms/TBT0/CLS0. 점수 선택 재시도·TLS 우회·평가 기준 변경은 없었다. 비점수 진단은 보존했고 실제 포털 순위·CrUX·로그인 화면 전체 WCAG 인증으로 확대하지 않는다. 원본 해시는 서비스 인도 증빙에 기록했다. 이후 제품 코드/이미지를 변경하지 않았다.
