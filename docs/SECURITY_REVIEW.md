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
