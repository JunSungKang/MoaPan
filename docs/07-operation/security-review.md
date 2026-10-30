# 보안 검토

> 문서번호: MP-DOC-020 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

확인일2026-10-28. 최종 이미지·JAR 식별값과 검사 시각은 [증빙](../05-test/evidence/verification.json), 발견별 패키지·설치/수정 버전·근거는 [발견 목록](../05-test/evidence/security-findings.md)에 기록한다. 검사 DB 다운로드 오류·JAR 미인식·빈 SBOM은 성공으로 처리하지 않는다. HIGH/CRITICAL·알려진 악용 영향·고위험 UNKNOWN이 남으면 외부 배포를 차단한다. CI/CD나 정기 자동 검사는 없다.

## 범위·도구

Trivy0.76.1 공식 archive와 executable SHA256을 대조하고 공급자 security advisories를 조회했다. 소스·Gradle lock의 build/test 의존성, Go infrastructure module lock, Docker 설정·secret, 실제 runtime CycloneDX SBOM100컴포넌트, 실행 bootJar91인식 패키지, app/proxy/DB 및 Java/Go build 이미지5종을 검사한다. 실제 도구 바이너리 Trivy·age·age-keygen도 별도로 검사했다. raw report는 build/reports/security, 도구 report는 .local/evidence/tool-scan.json에 있다. 전역 ignore·suppression을 사용하지 않았다.

Vulnerability DB는 schema2, 마지막 다운로드2026-10-28T01:04:00.046418Z, 생성2026-10-27T19:17:44.708952708Z였다. 최종 검증은24시간 이내다. Java DB도 실제 동기화해 JAR 분석에 사용했다. 다음 배포 검토 때에는 새로 동기화·재검사한다. CISA 공식 kev-data catalog2026.10.27(2026-10-27T19:00:02.2307Z)을 내려받아 발견 ID와 대조한다. KEV 미등재나 scanner0건은 취약점 부재 보장이 아니다.

## 긴급 JDK·truststore 검증

[Oracle CVE-2026-91403](https://www.oracle.com/security-alerts/alert-cve-2026-91403.html)은2026-10-21, [후속 CVE-2026-114820](https://www.oracle.com/security-alerts/alert-cve-2026-114820.html)은10월24일 공지다. 유출된 중간 CA를 통한 TLS 위장이 공격 조건이며 JSSE를 사용하는 worker·Gradle의 HTTPS가 관련된다. Oracle 버전 문자열을 Temurin에 그대로 적용하지 않았다.

선정 Temurin27.0.1.0.2+1은 [공급자10월27일 공지](https://adoptium.net/news/2026/10/temurin-october-2026-emudhra/)가 명시한 수정 빌드다. Temurin은 Mozilla NSS3.131.1 기반 truststore를 사용하며 compromised chain의 G3/AAA를 신뢰하지 않는다. upstream CheckCerts의 emSign G1 경고를 확인했지만, 공급자는 G1을 손상된 intermediate와 구별하며 Mozilla 분석에 따라 유지한다고 설명한다. G1을 이름만 보고 임의 삭제하지 않았다.

호스트/실제 app 이미지에서 기본 truststore118인증서, G3·AAA 부재, custom javax.net.ssl.trustStore 미설정을 확인했다. `scripts/security/TrustAudit.java`로 일반 NASA HTTPS 성공과 공급자가 연결한14개 TLS test chain 모두 **SSLHandshakeException** 거부를 확인했다. 단순 timeout을 인증서 거부로 합산하지 않는다. JDK 전체를 공급자 원본으로 교체했으며 오래된 볼륨 cacerts를 덮어쓰지 않았다. 이 TLS 시험은 Java signed-code intermediate 처리의 별도 보증은 아니다. 서비스는 외부 Java 코드를 로드·실행하지 않는다. TLS 검증 해제·TrustAll·curl -k는 사용하지 않았다.

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

2026-10-28T05:25Z HTML 페이지 timeout을 공식 JSON endpoint 재조회로 해소했다. 남은 Ubuntu CVE22개 모두 noble 상태와 공격 전제를 확인했으며 needed/deferred로 수정 미완료 상태다. [공급자별 재확인](../05-test/evidence/ubuntu-review.md)에 항목별 노출 경로와 후속 조치를 기록했다. 현재 구성은 해당 native parser·인증·서비스 공격 전제를 외부에 노출하지 않으며, 고위험 UNKNOWN은 확인되지 않았다. 경고를 삭제하거나 패치 완료로 표시하지 않는다. 구성 변경 또는 새로운 악용 근거가 나오면 재검토한다.

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
최초 새 DB 검사에서 CVE-2026-116587 (go-jose v3/v4) HIGH2건으로 securityCheck 실패했다. 공급자 https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q 확인: v4 4.1.6 수정, v3 패치 없음. v4를4.1.6으로 갱신하고 x/net0.62.0(공식 Go GO-2026-6860/6866) 패치를 적용한다. smallstep crypto0.91.1은 v3 문제를 해결하지 않아 불필요한 전이 의존성 변경을 피하고 기존0.91.0을 유지했다. v3는 PKI 경로에 남으며 현재 reverse proxy/외부ACME 구성에서 비신뢰 JWK를 처리하는 PKI 서버/관리 API가 노출되지 않아 배포별 NOT_AFFECTED 판정. docs/05-test/evidence/jose-v3-applicability.json에 정확한 패키지/버전/설정 SHA 및2026-11-05 만료를 고정하고 설정 변경·다른CVE·기간 경과 시 다시 차단한다. 패치 완료라고 주장하지 않는다. 공식 JDK 최신 릴리스는27.0.1.0.2+1 유지 확인; Spring/Oracle advisory 페이지 재조회. 최종 검사 결과는 후속 증빙에 기록한다.

최종 JAR SHA5241b8627fe698af11cb25e6f0e8be5aed7a3bcb566fc17e4fc3df2948d3e4bf는 실행 web 내부와 호스트 산출물이 일치한다. 최초 태그 기반 스캔에는 이전 runtime이 잡혀 최종 실행 digest37e99bc7…를 별도로 재검사했다. 앱·프록시 실행 digest와 최종 이미지 보고서가 일치한다. 프록시 실제 adapt 결과는 http/tls만, admin disabled, 외부 acme issuer만이며 PKI/acme_server 없음. V3 HIGH1은 삭제/무시가 아니라 위 한정된 NOT_AFFECTED 판정으로 보고서에 그대로 남는다. KEV2026.10.27 대조에서 이번3개CVE 일치 없음(안전 보증 아님). 정확한 시각·Trivy/DB·보고서해시: evidence/topic-security.json.

## 카드 개편 산출물
새 runtime 이미지 `sha256:5990e938c68381287ab8142afb6fff98484b472dd30f4d1371e09eeb15f018cc` Trivy 이미지 gate 통과 후 배포. 최종 JAR·SBOM·소스 검사 통과. Java 의존성/프록시 이미지는 변경하지 않았으며 기존 proxy go-jose/v3 조건부 적용성 검토와 만료 조건은 계속 유지한다. 카드 작성자는 안전한 textContent로 표시하며 빈 발행일을 수집일로 위장하지 않는다.

기존 피드 밖 요약 재선정까지 포함한 최종 runtime 이미지: `sha256:44337419a19909216493a472268e46758c97ce8d9aa7d66302206ed59e872f56`. 이 정확한 이미지로 Trivy gate를 다시 통과한 후 서비스에 반영했다. 앞선 5990 이미지는 중간 이미지다.

## 검색·슬라이드 산출물 재검증
2026-10-28T23:21:13.826Z: 소스/비밀값/설정·SBOM·JAR·최종 이미지 Trivy gate 통과. JAR91패키지/SBOM100컴포넌트 인식, advisory DB 동기화2026-10-28T21:57:31.453891Z. 실행 web/worker 이미지 모두 `sha256:a031afba57f6eb2e5ce0d71df7a3c2705b2c13d7ac14d8de6cb32c1210d7568a`로 스캔 대상과 일치한다. 이미지 manifest 내 config digest를 별도 이미지로 조회한 시도는 Docker No such image로 검사되지 않았고, 실제 실행 image ID는 정상 스캔한 manifest ID와 동일함을 확인했다. 미검사를 성공으로 세지 않는다. 프록시/의존성 변경 없음; 기존 go-jose/v3 HIGH1의 한정된 NOT_AFFECTED 근거·만료 조건을 그대로 유지하며 패치 완료로 표시하지 않는다. 검색 조건 SQL 파라미터화·문자 와일드카드 escape·CSRF·quota·no-store·로그 미기록을 검증했다.

## 2026-10-29 로컬 번역 적용
Index2BQ8 공식 SHA 및 upstream b11960 공식 archive SHA 검증, runtime advisory 근거는 evidence/translation-runtime-security.json. 네이티브 런타임은 JAR Trivy 검사 범위에 포함되지 않으므로 검사0건으로 대체하지 않는다. loopback/API key/offline/무도구/무RPC/무webUI, worker만 연결. 원문일치 용어표와 숫자/CVE 검증은 완전한 의미정확성 보장이 아니다.
최종앱이미지 sha256:e4b05e4e6828a6076d8c82548460cc14a1bb50bc8664a9cbb27f2139307061ae Trivy gate 통과, source/bootJar/SBOM gate 통과. 기존 proxy 조건부 영향없음 판정은 유지되며 전체환경 취약점0 주장하지 않는다. 정확한 실행 evidence/translation-delivery.json.


### Linux 번역 최종 산출물 (2026-10-29)
Trivy0.76.1 최종 앱/translator 이미지 검사 gate 통과. 앱59f4c97c8c7ee18b88ff29ec18b978b78c3602c09b3d1d98c8082147615e40fe, translator2221c46cc144f87d4b371b0264809a3ad3d8fec9642b83b7ab14ee990cec4ee0(sha256). DB 마지막 동기화2026-10-28T21:57:31.453891Z. 소스·JAR·SBOM 검사도 실행. 모델 runtime은 비루트/read-only/cap_drop/internal network/no host port/CPU 자원 제한. 모델-init만 다운로드 접근. native C++ 구성요소의 scanner 탐지 범위는 OS 패키지 검사와 같지 않으며 CVE 미검출을 무취약 보증으로 해석하지 않는다. 기존 Caddy 조건부 VEX와 보안 잔여 위험은 유지한다.


### Helm 설정 검사 경계
실행 가능한 구성은 scripts/deploy/helm/moapan. Trivy 렌더링 검사 HIGH0/CRITICAL0/MEDIUM11/LOW2. synthetic registry 검사10건은 실제 승인registry 지정 후 재검사 필요. ConfigMap DATABASE_URL/USER는 비밀값을 포함하지 않는다(credential은 기존Secret에서 주입). PostgreSQL alpine UID/GID70은 비루트이나 scanner >10000 권고와 차이가 있다. 경고를 전역 ignore하지 않았다. 최초 readOnlyRootFilesystem 누락2건은 수정·재검증했다. 모델 다운로드 init와 실행 Pod의 egress 공유 한계 때문에 실클러스터 운영 보안 승인으로 간주하지 않는다. Caddy VEX 참조만 새 경로로 갱신했고 기존4파일 SHA256이 동일함을 확인했으며 만료일/영향 판정은 연장하지 않았다.


### Qwen 분류 최종 산출물 (2026-10-29)
Trivy0.76.1 소스/비밀값/설정·JAR91패키지·SBOM100컴포넌트·최종 앱 및 classifier 이미지 gate PASS. DB sync2026-10-28T21:57:31.453891Z. 실행 앱sha256:53ac07cd61fc5cfa35626b12e1946428a3fa62946d98ec663ec59314b633f41b, classifier sha256:d5f131bce43adfd3fd776ac58e481268ad23594a4bc80b316ded4e930599bdad로 검사 대상과 일치. HF 모델 SHA 검증 및 기존 llama.cpp 고정 소스 보안 검토를 재사용하며 새 의존성 추가 없음. native 모델 의미 정확성을 scanner로 입증했다고 주장하지 않는다.
Helm 기본값은 필수 registry/host/Secret 미입력으로 소스 스캔에서 skip되어 별도로 실제 합성값 렌더링을 검사했다. HIGH0/CRITICAL0/MEDIUM13/LOW2: 합성 registry 허용목록 경고12, 비밀값 없는 DB 연결 ConfigMap1, PostgreSQL UID/GID70 경고2. 실제 배포 registry/클러스터 egress 정책 검증은 남아 있다. 기존 Caddy 조건부 VEX 판단/만료와 기타 잔여 위험을 유지한다. 전체 환경 취약점0으로 선언하지 않는다.

## 2026-10-30 보안 재검토와 패치

공식 Spring·Tomcat·pgJDBC·Oracle/Temurin·Gradle·Caddy·llama.cpp·Trivy 공지와 KEV 2026.10.29를 재조회했다. Temurin27.0.1.0.2+1, Boot4.1.2/Security7.1.2, Gradle9.8.2, Tomcat11.0.27, PostgreSQL18.6/JDBC42.7.14, Caddy2.11.7, llama.cpp v0.8.0/b11960, Trivy0.76.1은 현재 확인한 정식 릴리스/수정 범위다. 의존성 버전을 이유 없이 변경하지 않았다. 자세한 확인시각·보고서 SHA·실제 이미지별 발견은 [security-refresh.json](../05-test/evidence/security-refresh.json).

수정 사항:
- SEC-LOCAL-001: 앱이 Content-Length만 검사해 길이 없는 요청에서는 앱 자체16KB 제한이 보장되지 않았다. 기존 Caddy16KB 제한은 유지하며, CSRF/JSON 파싱 전 실제 바이트16385개까지만 읽고 초과 요청을413/no-store로 종료한다. 길이 없는 입력·정확한16KB경계·전체필터체인 거부를 테스트했다. 새 CVE를 부여하지 않는다.
- SEC-LOCAL-002: 이미지 게이트에 DB/보고서 최신성 검사가 없었다. 소스/JAR/SBOM/이미지 모두 DBschema2, 발행·동기화·검사24시간, 공급자NextUpdate, 현재DB보다 오래된 보고서 여부를 검사한다. 오류·만료·미인식 형식은 실패한다.4개 회귀 테스트 통과.
- TLS 추가 조치: [공식4루트 지침](https://ops.java/emudhra/)과 [공식 검사기](https://cr.openjdk.org/~mr/CheckCerts.java)를 확인하니 기존 TrustAudit는2개만 검사했다. 실제 선택 JDK에는 추가 G1루트1개가 남았다. G1은 공식 문서상 침해된 중간인증서와 직접 관련 없는 추가 eMudhra 루트이며, 이를 침해 확정으로 분류하지 않는다. 공식4루트 목록의 정확한 CN과 자체서명 CA 여부로 식별해 G1(SHA256 40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367)만 제거했다. 공급자 바이너리 버전은 같고 cacerts에 명시적 공식 완화책을 추가했다. workspace JDK 및 Docker build/runtime 공통stage에 적용해117루트/4대상없음/정상HTTPS/문제체인14개TLS거부를 재검증했다. 사용자 truststore나 TLS 우회는 추가하지 않았다.

공식 공지 대조:
- [CVE-2026-91403](https://www.oracle.com/security-alerts/alert-cve-2026-91403.html), [CVE-2026-114820](https://www.oracle.com/security-alerts/alert-cve-2026-114820.html): 버전문자열 대신 위 실제 신뢰 저장소·연결 검증으로 판정.
- [Spring CVE-2026-41707](https://spring.io/security/cve-2026-41707/)·[CVE-2026-47841](https://spring.io/security/cve-2026-47841/):7.1.1에서 수정, 설치7.1.2. DPoP/WebAuthn도 사용하지 않음.
- [pgJDBC CVE-2026-107314](https://jdbc.postgresql.org/security/):42.7.14수정 포함. [PostgreSQL 공식 보안목록](https://www.postgresql.org/support/security/)의18.6수정 포함.
- [Caddy 공지](https://github.com/caddyserver/caddy/security/advisories): 최신 게시된CVE-2026-55458의수정2.11.5를설치2.11.7이포함.
- [Oracle PHP 긴급공지](https://www.oracle.com/security-alerts/alert-cve-2026-109618.html):PHP/영향Oracle제품은 미사용.
- [llama.cpp 공지](https://github.com/ggml-org/llama.cpp/security/advisories):13공개건 범위 재확인, 고정commit은CVE-2026-21869 수정commit보다3052커밋후행. 기존GGUF/RPC수정 검토 유지. CPU실행기두개SHA동일확인, RPC·외부모델다운로드·도구실행미제공.
- [Trivy 공지](https://github.com/aquasecurity/trivy/security/advisories):0.76.1은열거된공급망/DB경로/플러그인/Helm파서수정버전이후. 공식릴리스archive체크섬과설치binarySHA직접일치검증. 최초버퍼초과검사는미완료로처리하고streamSHA로재검증성공.

잔여 위험/배포 판정:
- 앱/DB/번역/분류/앱빌드이미지HIGH0·CRITICAL0. 프록시는기존go-jose/v3 CVE-2026-116587 HIGH1이남는다. 공식v3패치없음. 실제adapt는admin비활성·http/tls만·외부ACME이며JWK/JOSE서버없음을재확인했다. 기존구성SHA와2026-11-05만료VEX를유지하고예외확대·기간연장없음. 패치완료가아니다.
- 프록시go-jose/v3 MEDIUM2는비신뢰JOSE입력이필요하며같은노출제약이적용된다. [GO-2026-5932](https://pkg.go.dev/vuln/GO-2026-5932)는x/crypto/openpgp 전체버전미수정공지다. 현재서비스는OpenPGP를사용하지않고의존소스의해당패키지외부import를찾지못했다. UNKNOWN이라는이유로자동안전판정하지않으며기능추가시재검토.
- Ubuntu기존22CVE의noble공식JSON을모두재조회:needed/deferred유지,격리된임시컨테이너의apt시뮬레이션추가업데이트0. [기존노출검토](../05-test/evidence/ubuntu-review.md)를유지하고미해결MEDIUM/LOW를숨기지않음. 신규공격전제노출없음,현재발견과KEV일치0.
- Helm은필수배포값없음으로원본scanner가skip하므로합성값렌더를별도검사했다.클러스터·실제registry·egress운영검증은미수행. 최초namespace미지정렌더경고와누락필드실패는증빙에서구분한다.
- 검사게이트통과는전체환경무취약보증이아니다. 최종runtime이미지60547f2cb05daf699e1c9b99e64ff7b2fa647d029b498f0eb2c40a3699bd1f6f(sha256)를검사후Composeweb/worker에적용했다.

관리자 광고 추가: 외부 SDK·추적·서버 URL fetch·CSP 변경 없음. ADMIN/서비스권한·CSRF·version·감사, HTTPS 링크와 서버 raster이미지 경로제한, DOM textContent 적용. ADSENSE는 정보 저장만 가능하고 서비스409/DB CHECK로 활성화차단(사용자 승인범위). V12 후 이미지03f8084c9d35ccbe5b25e2a3dd93f89433fce8f43cddb8ef471cf56547a1f0d7 검사를 통과해 같은 digest배포. 기존 proxy한정VEX·OS잔여위험은 그대로 유지한다.

뉴스레터 변경: 기존 JOSE 비노출 VEX는 독립 검토 후 request_body 상한이 바뀐 두 Caddyfile hash만 갱신했다. 만료2026-11-05와 라이브러리 미패치 판정은 유지한다. 실제 실행 Caddy adapt에서 admin disabled, http/tls만 확인했다. 본문1MiB는 관리자 UUID 원고PUT에만 허용하며 일반 API16KiB는 유지한다. 외부 iframe/스크립트/뉴스 원문 수집 추가 없음. 이미지 data URI·형식·크기·메타데이터 치수 검사, textContent, ADMIN/CSRF/version 감사 적용. 앱/프록시 이미지 gate 통과는 기존 OS MEDIUM/LOW와 조건부 proxy HIGH를 제거했다는 뜻이 아니다.

뉴스레터 지면 재구성(2026-10-30): 운영 라이브러리·JDK·proxy 변경 없이 정적 화면만 수정했다. Trivy0.76.1 소스/비밀값/설정/JAR/SBOM 검사 성공, 최종 앱 sha256:c6eb6ded500d4f2f2887a9f985b2d0d86d28a76c0e47fac924fbb6bca38a23c6 이미지 검사 HIGH0/CRITICAL0/MEDIUM23/LOW4. DB DownloadedAt2026-10-29T22:52:43.958399Z, UpdatedAt2026-10-29T19:18:50.96912695Z, NextUpdate2026-10-30T19:18:50.969126577Z; freshness gate통과. 기존 OS 잔여 공지 및 proxy의 hash/만료 제한 JOSE 비노출 검토는 유지하며 proxy를 재빌드하지 않았다. 실제HTTPS와 실행JAR/hash 동일 확인. 신규 취약점이 전혀 없다고 보장하는 판정은 아니다. 자세한 결과는 ../05-test/evidence/newsletter-redesign.json.

뉴스레터 삭제·시각 수정(2026-10-30): 관리자 DELETE는 CSRF와 현재version을 요구하며 단일 트랜잭션으로 콘텐츠 제거/최소감사를 수행한다. 저장/삭제 공통 UUID 잠금과 콘텐츠 없는 영속 삭제 표식으로 초기 PUT 재전송의 부활도 거부한다. 211회귀 및 Trivy0.76.1 소스/비밀값/설정/JAR/SBOM/최종이미지 gate PASS. 최종앱 sha256:cf3e54452b050c43db1096772762ef3d6c75b6c65d6c9ca2a378032df6cc187b, HIGH0/CRITICAL0/MEDIUM23/LOW4. 기존 OS 잔여 공지와 변경하지 않은 proxy의 만료/hash 제한 JOSE 비노출 판정을 유지한다. DB동기화/생성시각은 evidence/newsletter-deletion.json에 기록했고 freshness검사를 통과했다. 실제뉴스레터 발간 임시계정과세션은 제거했다. 백업즉시소거/기수신콘텐츠회수는 보장하지 않는다.


수집 뉴스 공개 (2026-10-30T17:11:40.413Z): 사용자 승인으로GET공개지면/태그/대표관심사, POST공개검색만명시적으로허용했다. 뉴스레터·계정·회원전용API401, 관리는ADMIN을유지하며 CSRF/동일origin/no-store/전역PostgreSQL한도·회원quota를검증했다. 인증해제시회원뉴스레터소개제거/지연응답폐기, 고정상대Location302로HTTPS다운그레이드방지수정. 214회귀/소스·secret·설정/JAR/SBOM/최종앱이미지gate PASS, Trivy0.76.1, 이미지 sha256:47b30aaf13c58138e0e46324c0f0ab328c34316300accf1768694b4db3d529c3, HIGH0/CRITICAL0/MEDIUM23/LOW4. DB동기화/생성시각은 [증빙](../05-test/evidence/public-newspaper.json)에기록했고최신성gate통과. JDK/의존성/proxy/truststore변경은없으며공식advisory전체재검토를이번작업에서수행했다고주장하지않는다. 기존proxy의2026-11-05만료/hash제한JOSE비노출판정과OS잔여위험은유지한다. 실제HTTPS24개접근/CSRF/상한/자산검증과실행JAR·이미지일치확인.


승인된 뉴스레터 제품 기반 (2026-10-30T18:12:31.079Z): 역할과분리한무료preview혜택, ADMIN/CSRF/version/공통UUID잠금·source→article 공유잠금·발간근거현재상태검사·미수집링크/시각제한. 출력은자체원고만DOM으로구성하고CSP/본문크기/로그/회원개인화경계를유지한다. 평가자료는ADMIN의익명집계만로컬메모리,추가프로필·결제·추적테이블없음. 실제기존본문변경없이주간근거연결/권리재검토·개정, 임시계정삭제확인.
Trivy0.76.1 소스/비밀값/설정/JAR/SBOM/최종앱 gate PASS, 이미지 sha256:db82be6087ffea3ffa11b38a2dbb6930833734b7cab1b047d2199380d6ae9b98, HIGH0/CRITICAL0/MEDIUM23/LOW4; scannerDB시각과실행일치는 [증빙](../05-test/evidence/newsletter-product.json). 기존proxy JOSE비노출조건/만료와OS잔여위험을해결했다고주장하지않는다. JDK/직접·전이의존성/truststore/proxy변경없으며이번변경에서공식공지를전부재조사했다고표시하지않는다.
Helm 합성렌더구성검사: HIGH0/CRITICAL0/MEDIUM13/LOW2. KSV-01010은SOURCE_MIN_INTERVAL_SECONDS=300만탐지한비밀값오탐, KSV-0020/21은기존vendor DB 비root UID/GID70 호환성제약, KSV-0125 12건은registry.example.test가실제신뢰registry가아닌준비용표시임을확인했다. 전역ignore추가없음. 실제registry/namespace/PVC/네트워크/배포정책확정전클러스터배포를완료로표시하지않는다.

최종독립HTML출력보완: UTF-8/모바일viewport를명시하고기존출처/CSP구성을유지했다. 전체229회귀/최종JAR·SBOM·이미지검사재실행PASS. 최종앱 sha256:433f4af4bfaceecb8c74059c5ea3a12a4e1e0bf43c690e1788c4680394038011, JAR 6d216668118b46b78da963622288eca4543de82c85aea08254f274294b577865; Compose동일digest와추가HTTPS5경계·실제output모듈hash를확인했다. 앞선실인증브라우저/다운로드/복원증빙은직전이미지의전체시나리오이며최종변경은head메타데이터만으로분리기록했다.
