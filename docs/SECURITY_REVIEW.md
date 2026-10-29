# 보안 검토

확인일2026-10-28. 최종 이미지·JAR 식별값과 검사 시각은 [증빙](evidence/verification.json), 발견별 패키지·설치/수정 버전·근거는 [발견 목록](evidence/security-findings.md)에 기록한다. 검사 DB 다운로드 오류·JAR 미인식·빈 SBOM은 성공으로 처리하지 않는다. HIGH/CRITICAL·알려진 악용 영향·고위험 UNKNOWN이 남으면 외부 배포를 차단한다. CI/CD나 정기 자동 검사는 없다.

## 범위·도구

Trivy0.76.1 공식 archive와 executable SHA256을 대조하고 공급자 security advisories를 조회했다. 소스·Gradle lock의 build/test 의존성, Go infrastructure module lock, Docker 설정·secret, 실제 runtime CycloneDX SBOM100컴포넌트, 실행 bootJar91인식 패키지, app/proxy/DB 및 Java/Go build 이미지5종을 검사한다. 실제 도구 바이너리 Trivy·age·age-keygen도 별도로 검사했다. raw report는 build/reports/security, 도구 report는 .local/evidence/tool-scan.json에 있다. 전역 ignore·suppression을 사용하지 않았다.

Vulnerability DB는 schema2, 마지막 다운로드2026-10-28T01:04:00.046418Z, 생성2026-10-27T19:17:44.708952708Z였다. 최종 검증은24시간 이내다. Java DB도 실제 동기화해 JAR 분석에 사용했다. 다음 배포 검토 때에는 새로 동기화·재검사한다. CISA 공식 kev-data catalog2026.10.27(2026-10-27T19:00:02.2307Z)을 내려받아 발견 ID와 대조한다. KEV 미등재나 scanner0건은 취약점 부재 보장이 아니다.

## 긴급 JDK·truststore 검증

[Oracle CVE-2026-91403](https://www.oracle.com/security-alerts/alert-cve-2026-91403.html)은2026-10-21, [후속 CVE-2026-114820](https://www.oracle.com/security-alerts/alert-cve-2026-114820.html)은10월24일 공지다. 유출된 중간 CA를 통한 TLS 위장이 공격 조건이며 JSSE를 사용하는 worker·Gradle의 HTTPS가 관련된다. Oracle 버전 문자열을 Temurin에 그대로 적용하지 않았다.

선정 Temurin27.0.1.0.2+1은 [공급자10월27일 공지](https://adoptium.net/news/2026/10/temurin-october-2026-emudhra/)가 명시한 수정 빌드다. Temurin은 Mozilla NSS3.131.1 기반 truststore를 사용하며 compromised chain의 G3/AAA를 신뢰하지 않는다. upstream CheckCerts의 emSign G1 경고를 확인했지만, 공급자는 G1을 손상된 intermediate와 구별하며 Mozilla 분석에 따라 유지한다고 설명한다. G1을 이름만 보고 임의 삭제하지 않았다.

호스트/실제 app 이미지에서 기본 truststore118인증서, G3·AAA 부재, custom javax.net.ssl.trustStore 미설정을 확인했다. `scripts/TrustAudit.java`로 일반 NASA HTTPS 성공과 공급자가 연결한14개 TLS test chain 모두 **SSLHandshakeException** 거부를 확인했다. 단순 timeout을 인증서 거부로 합산하지 않는다. JDK 전체를 공급자 원본으로 교체했으며 오래된 볼륨 cacerts를 덮어쓰지 않았다. 이 TLS 시험은 Java signed-code intermediate 처리의 별도 보증은 아니다. 서비스는 외부 Java 코드를 로드·실행하지 않는다. TLS 검증 해제·TrustAll·curl -k는 사용하지 않았다.

## 발견·수정

- 초기 Temurin 컨테이너27-jdk tag는27+35여서 채택하지 않았다. 정확한 수정 tar와 SHA256으로 전체 JDK를 구성했다.
- Gradle9.8.2는 [9.8.1의 세 보안 수정](https://docs.gradle.org/9.8.2/release-notes.html)을 포함한다. 공식 ZIP/Wrapper 각각 SHA256 일치, strict metadata·lock을 유지했다.
- Spring Security7.1.2는 [CVE-2026-41707](https://spring.io/security/cve-2026-41707/), [47841](https://spring.io/security/cve-2026-47841/), [47877](https://spring.io/security/cve-2026-47877/)의 수정7.1.1 이후다. DPoP·WebAuthn·Authorization Server 자체도 사용하지 않는다. Boot BOM/runtime 전이 의존성은 실제 JAR/SBOM과 검사했다.
- 공식 Caddy2.11.7 이미지의 Go1.26.8/x-net0.59.0과 OS 패키지에서 HIGH 발견. Go1.27.3/x-net0.60.0으로 표준 Caddy를 재빌드하고 base를 검증한 Ubuntu24.04로 교체했다. chi의 CVE-2026-114437 MEDIUM도5.4.0으로 수정했다. Caddy 본체의 GHSA-8qx7-j2v2-x6q5 수정은2.11.5 이후이며 선택2.11.7에 포함된다.
- 공식 PostgreSQL18.6 Debian OS/gosu의 고위험을 그대로 허용하지 않았다. 공식 Alpine digest에 package 보안 업데이트, Go1.27.3 재빌드 gosu1.19와 moby/sys/user0.4.1(CVE-2026-61801 수정)을 반영했다. 실제 DB18.6·빈 초기화·복원·영속성을 다시 검증했다.
- age1.3.2 원본 executable의 오래된 Go1.27.0/x-crypto에서 발견한 HIGH/MEDIUM을 Go1.27.3/x-crypto0.57.0 재빌드로 수정했다. 출력 버전은 v1.3.2+moapan-security.1이며 source/lock과 공식 원본을 구별한다. 수정된 도구로 암호화 백업·격리 복원을 다시 실행했다.
- DB 장애 때 세션·quota를 무제한 허용하지 않도록503+Retry-After를 검증했다. 요청 body·쿠키·암호를 오류에 넣지 않았다.

## 영향 검토와 남은 위험

GO-2026-5932는 x-crypto 전체의 취약점이 아니라 유지보수 중단된 **openpgp 하위 패키지**에 대한 [공식 경고](https://vuln.go.dev/ID/GO-2026-5932.json)다. Caddy/gosu/age의 실제 `go list -deps`에 해당 하위 패키지가 없고 Trivy/age/age-keygen executable에도 해당 패키지 심볼이 없다. 이 서비스와 사용한 scan 명령은 OpenPGP를 제공하지 않는다. 해당 경고는 NOT_AFFECTED(취약 코드 미포함)로 근거를 남기며 scanner 출력에서 숨기지 않는다. 향후 도구/모듈 변경 시 다시 확인한다.

Ubuntu24.04의 잔여 MEDIUM/LOW는 발견 목록에 모두 유지한다. 공급자 패치가 아직 없는 항목을 임의 소스 패치나 전역 ignore로 지우지 않았다. dash는 로컬 shell pattern/printf 입력, PCRE2는 native pattern parser, GnuTLS/SASL은 native TLS/인증 경로, tar는 악성 archive 추출, systemd는 journald, shadow는 하위 UID 분배가 조건이다. 앱은 Java JSSE·Java regex를 사용하고 비신뢰 뉴스/사용자 입력을 shell·native parser·archive로 전달하지 않는다. curl의 TLS backend는 OpenSSL이며 실행 이미지 curl은 고정 loopback healthcheck에 사용한다. glibc18374의 ccs mode를 사용자에게 열지 않고,89092의 nscd daemon은 실행하지 않는다. 각 패키지가 존재한다는 사실과 관련 기능의 노출을 구분하되 base OS 위험이0이라고 선언하지 않는다. 패치가 나오면 base/package 갱신 후 같은 검사·회귀를 반복해야 한다.

2026-10-28T05:25Z HTML 페이지 timeout을 공식 JSON endpoint 재조회로 해소했다. 남은 Ubuntu CVE22개 모두 noble 상태와 공격 전제를 확인했으며 needed/deferred로 수정 미완료 상태다. [공급자별 재확인](evidence/ubuntu-review.md)에 항목별 노출 경로와 후속 조치를 기록했다. 현재 구성은 해당 native parser·인증·서비스 공격 전제를 외부에 노출하지 않으며, 고위험 UNKNOWN은 확인되지 않았다. 경고를 삭제하거나 패치 완료로 표시하지 않는다. 구성 변경 또는 새로운 악용 근거가 나오면 재검토한다.

사용자 승인 로컬 Mac 공개 접속은 IP 인증서·HTTPS·Secure 쿠키·public proxy 이미지 재검사와 실행 digest 일치·휴대폰 데이터망 확인까지 완료했다(evidence/public-access.json). 별도 호스팅 사업자의 환경/로그 검증은 현재 범위에서 제외하며 보증하지 않는다. 미공개 취약점 탐지나 무결점 보안을 보장하지 않는다. 최종 로컬 scanner 게이트와 전체 배포 승인 판정은 구분한다.

## Docker Desktop 호스트 런타임 추가 점검 — 2026-10-28T04:17Z
공식 릴리스 문서 https://docs.docker.com/desktop/release-notes/#4970 를 재조회했다. 기존4.91.0에서 공식 제품 내 업데이트로4.97.0(244584)에 교체했고 CLI와 UI 버전을 확인했다. CVE-2026-114409는 이전 버전의 로그/진단에 타사 API/OAuth 값이 기록될 수 있는 문제로4.97.0에서 수정·기존 host 로그 정리가 제공된다. 이 작업의 로컬 진단 묶음은 외부 업로드하지 않았고 삭제했다. CVE-2026-99999는 VM 네트워크 스택 손상 관련 수정이다. 업데이트 후 권한 서비스 응답 정체를 별도 해결했고 Engine29.9.2 정상 시작과 Compose health 검사를 확인했다(ENV-001/002). 애플리케이션 이미지 스캔만으로 호스트 Docker 보안을 확인했다고 보지 않는다.

## 인증 화면·약관 변경 재검증
2026-10-28: 동의 검증/최소 기록 추가 후 전체 Java35개 및 소스/JAR/SBOM 검사 성공. 변경된 build/runtime image를 재검사하고 실행 이미지 ID 및 JAR SHA256 일치를 확인했다. HIGH/CRITICAL 없음; 기존 MEDIUM/LOW 검토와 운영자 연락처 미정은 계속 유효하다. 정확한 시각·digest는 evidence/verification.json.

사용자 정책 변경: 비밀번호 최소6자를 명시 요청하고 아이디1~128자를 승인했다. 비밀번호 단독 인증15자 기준 대비 단문 추측 위험 증가를 설명한 후 반영하며 해당 NIST 길이 기준을 충족한다고 주장하지 않는다. 해시·CSRF·실패 제한은 유지하고 가입/로그인/변경의 경계값을 검증한다.

## 관심사 파일 이전 검토 (2026-10-28)
새 서버 API/DB 저장/의존성 없음. WebCrypto AES-GCM 인증 암호화와 고정 PBKDF2 작업량, 무작위 salt/IV, 비추출 CryptoKey, 크기·필드·ID·가중치 제한, 계정 비교와 명시적 적용을 검증했다. 잘못된 암호·변조·타 계정·손상 입력은 기존 IndexedDB 변경 없이 거부한다. 파일/파일 비밀번호 네트워크 전송 없음을 두 독립 Chromium context로 검사했다. 강한 파일 비밀번호 사용과 별도 전달이 필요하며 파일 자체의 오프라인 추측 공격, 악성 브라우저/확장/XSS, 사용자가 남긴 파일 복사본은 암호화만으로 제거할 수 없다. 로그인 비밀번호를 암호화 키로 사용하지 않는다. 최신 소스/JAR/SBOM 및 변경된 build/runtime 이미지 검사를 실행했고 고위험 게이트 통과. 기존 OS MEDIUM/LOW 발견의 공급자 검토는 유지한다. 정확한 digest/report SHA는 evidence/verification.json.

## 제한 공개 지면·대표 관심사 개정
사용자 명시 요청으로 최대30개 공개 기사 endpoint를 추가했다. 비회원에게 제공한 뉴스는 복사를 막을 수 없고, 분당600 전역 한도는 IP 추적 없이 적용하므로 공유 한도의 가용성 제한이 있다. 회원 cursor·quota·개인 계정·관리 API 보호는 유지한다. 공개 DTO는 기존 권리/숨김 재검사를 사용하며 무제한 query/cursor는 거부한다. 대표 관심사는 공통 운영 설정으로 ADMIN+CSRF+서비스 권한+version 조건+감사로 변경한다. 보호 HTML3개만 로그인 이동하고 API는 .html 경로도 JSON401 유지. 검색 조건은 네트워크로 보내지 않는다. 새로운 의존성·truststore 변경 없음. 최종 JAR/SBOM/변경 build·runtime 이미지 스캔과 실행 digest 대조는 verification.json 참조.

## 최초판·스크랩 반영 2026-10-28T12:49:33.292Z
JDK·의존성·truststore·네트워크 권한 변경 없음. 대표 문구는 로컬 정적 설정과 textContent, 스크랩은 자체 CSS이며 외부 폰트/이미지/추적 요청을 추가하지 않았다. 동의판 변경은 신규 가입에만 적용하며 기존 회원 동의 기록은 수정하지 않았다. source/JAR/SBOM과 최종 runtime/build 이미지 재검사 게이트 통과, 실행 JAR SHA·이미지 digest 일치. runtime sha256:4c289863a4d7f26ec532c4500c9698cdcce206426b0cebb5b68b5f85706e3fa6. HIGH/CRITICAL0, 기존 MEDIUM/LOW 및 도구의 근거 있는 NOT_AFFECTED 검토는 유지한다. DB 동기화 시각/개별 발견/원본 보고서 SHA는 evidence/verification.json.

CSS 상호작용/모바일 정렬 변경 검증 2026-10-28T13:00:18.186Z: JDK/의존성/truststore 불변. source/JAR/SBOM·변경 runtime/build 이미지 검사 통과, 실행 JAR와 검사 산출물 일치. runtime sha256:10fb33088f67df15ac84f45c5d16651031a558264c67df199d598525b1edd3c5. 기존 MEDIUM/LOW 발견은 유지하며 HIGH/CRITICAL0. 공개 전달 CSS SHA는 evidence/header-layout.json.

## 2026-10-29 수집 기능 변경 전 재검사
최초 새 DB 검사에서 CVE-2026-116587 (go-jose v3/v4) HIGH2건으로 securityCheck 실패했다. 공급자 https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q 확인: v4 4.1.6 수정, v3 패치 없음. v4를4.1.6으로 갱신하고 x/net0.62.0(공식 Go GO-2026-6860/6866) 패치를 적용한다. smallstep crypto0.91.1은 v3 문제를 해결하지 않아 불필요한 전이 의존성 변경을 피하고 기존0.91.0을 유지했다. v3는 PKI 경로에 남으며 현재 reverse proxy/외부ACME 구성에서 비신뢰 JWK를 처리하는 PKI 서버/관리 API가 노출되지 않아 배포별 NOT_AFFECTED 판정. docs/evidence/jose-v3-applicability.json에 정확한 패키지/버전/설정 SHA 및2026-11-05 만료를 고정하고 설정 변경·다른CVE·기간 경과 시 다시 차단한다. 패치 완료라고 주장하지 않는다. 공식 JDK 최신 릴리스는27.0.1.0.2+1 유지 확인; Spring/Oracle advisory 페이지 재조회. 최종 검사 결과는 후속 증빙에 기록한다.

최종 JAR SHA5241b8627fe698af11cb25e6f0e8be5aed7a3bcb566fc17e4fc3df2948d3e4bf는 실행 web 내부와 호스트 산출물이 일치한다. 최초 태그 기반 스캔에는 이전 runtime이 잡혀 최종 실행 digest37e99bc7…를 별도로 재검사했다. 앱·프록시 실행 digest와 최종 이미지 보고서가 일치한다. 프록시 실제 adapt 결과는 http/tls만, admin disabled, 외부 acme issuer만이며 PKI/acme_server 없음. V3 HIGH1은 삭제/무시가 아니라 위 한정된 NOT_AFFECTED 판정으로 보고서에 그대로 남는다. KEV2026.10.27 대조에서 이번3개CVE 일치 없음(안전 보증 아님). 정확한 시각·Trivy/DB·보고서해시: evidence/topic-security.json.

## 카드 개편 산출물
새 runtime 이미지 `sha256:5990e938c68381287ab8142afb6fff98484b472dd30f4d1371e09eeb15f018cc` Trivy 이미지 gate 통과 후 배포. 최종 JAR·SBOM·소스 검사 통과. Java 의존성/프록시 이미지는 변경하지 않았으며 기존 proxy go-jose/v3 조건부 적용성 검토와 만료 조건은 계속 유지한다. 카드 작성자는 안전한 textContent로 표시하며 빈 발행일을 수집일로 위장하지 않는다.

기존 피드 밖 요약 재선정까지 포함한 최종 runtime 이미지: `sha256:44337419a19909216493a472268e46758c97ce8d9aa7d66302206ed59e872f56`. 이 정확한 이미지로 Trivy gate를 다시 통과한 후 서비스에 반영했다. 앞선 5990 이미지는 중간 이미지다.

## 검색·슬라이드 산출물 재검증
2026-10-28T23:21:13.826Z: 소스/비밀값/설정·SBOM·JAR·최종 이미지 Trivy gate 통과. JAR91패키지/SBOM100컴포넌트 인식, advisory DB 동기화2026-10-28T21:57:31.453891Z. 실행 web/worker 이미지 모두 `sha256:a031afba57f6eb2e5ce0d71df7a3c2705b2c13d7ac14d8de6cb32c1210d7568a`로 스캔 대상과 일치한다. 이미지 manifest 내 config digest를 별도 이미지로 조회한 시도는 Docker No such image로 검사되지 않았고, 실제 실행 image ID는 정상 스캔한 manifest ID와 동일함을 확인했다. 미검사를 성공으로 세지 않는다. 프록시/의존성 변경 없음; 기존 go-jose/v3 HIGH1의 한정된 NOT_AFFECTED 근거·만료 조건을 그대로 유지하며 패치 완료로 표시하지 않는다. 검색 조건 SQL 파라미터화·문자 와일드카드 escape·CSRF·quota·no-store·로그 미기록을 검증했다.

## 2026-10-29 로컬 번역 적용
Index2BQ8 공식 SHA 및 upstream b11960 공식 archive SHA 검증, runtime advisory 근거는 evidence/translation-runtime-security.json. 네이티브 런타임은 JAR Trivy 검사 범위에 포함되지 않으므로 검사0건으로 대체하지 않는다. loopback/API key/offline/무도구/무RPC/무webUI, worker만 연결. 원문일치 용어표와 숫자/CVE 검증은 완전한 의미정확성 보장이 아니다.
최종앱이미지 sha256:e4b05e4e6828a6076d8c82548460cc14a1bb50bc8664a9cbb27f2139307061ae Trivy gate 통과, source/bootJar/SBOM gate 통과. 기존 proxy 조건부 영향없음 판정은 유지되며 전체환경 취약점0 주장하지 않는다. 정확한 실행 evidence/translation-delivery.json.
