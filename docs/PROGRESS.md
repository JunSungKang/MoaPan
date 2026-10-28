# MOAPAN 진행 상태

방법론: Sprint Delivery. CI/CD·GitHub Actions·정기 자동 검사는 사용자 요청으로 제외한다. 사용자가 별도 배포 검증을 제외하고 로컬 실행+외부 접속을 완료 기준으로 변경했다. https://175.195.62.165/ 에서 휴대폰 데이터망 정상 접속을 사용자 확인했으므로 현재 범위 COMPLETE다.

## 구현·실행 검증
- 인증·세션·CSRF·계정 관리, 사이트/주소/권리·Java worker·요약·태그·edition·cursor·원자 quota, 정적 신문·계정별 IndexedDB 개인화·관리자/정책 화면 구현.
- Temurin27.0.1.0.2+1·Boot4.1.2·Gradle9.8.2·PostgreSQL18.6. Java33개, bootstrap9개, 시작 도구 제한 시간3개 PASS. Docker 복구 후 PostgreSQL 통합18개 재실행 PASS, 전체 Gradle 검증 명령 성공.
- 2026-10-28T05:28Z NASA/NOAA 실제 수집 모두 SUCCEEDED. 합성 검증 계정·세션 삭제. 현재 기사22건, 지면 후보12개. 상세 시각은 evidence/verification.json.
- Docker Desktop4.97.0/Engine29.9.2. VirtioFS와 저장된 폴더 권한을 유지하고 사용자 tccd 응답 정체를 재시작으로 해소했다. 상세 증거·해석 한계는 BUGS.md.
- proxy 설정 디렉터리0755/파일0444 수정 및 HTTP healthcheck 추가. 최종 db/web/proxy healthy, worker 실행, migrate exit0. DB/proxy 강제 재생성 후 기존22개 기사 ID 모두 보존(05:20Z).
- 실행 중4개 컨테이너와 검사 이미지 digest 대조, runtime JAR SHA 일치 PASS. 소스·JAR·SBOM·이미지5종·도구 검사 HIGH/CRITICAL0. MEDIUM/LOW 잔여와 공식 JSON으로 확인한 CVE22건 노출 검토는 SECURITY_REVIEW.md. 무결점 보안이나 외부 배포 승인으로 해석하지 않는다.
- 실제2 web quota·worker SIGKILL lease 복구·빈 DB migration·암호화 격리 백업 복원도 별도 검증했다.

## 전달 및 남은 조건
- 기존 guarded commit/push와 PR#1 생성·사람 assignee 설정 완료. 복구·proxy 수정·최신 증빙 커밋767f2b409e534a1f74ceb3630050a556b6e88eb9를 guarded push하고 원격 SHA 일치를 확인했다.
- main merge는 승인되지 않아 수행하지 않는다. 현재 head의 독립 App formal review는 병합 전 별도 gate다.
- 외부 접속 PASS: 로컬 Mac의 Caddy IP 인증서·TLS·Secure 세션으로 서비스한다. 공급자 자료 조회 장애를 해소하고 잔여 MEDIUM/LOW 노출 검토를 완료해 AC-03 로컬 보안 게이트 PASS. 경고와 후속 갱신 의무는 유지.
- 사업 개시 전 상표·콘텐츠 이용 범위·운영자 정보·사업/언론 등록 여부·법률 검토는 별도 확인한다.

재개 시 git status와 실제 컨테이너/검사 digest 및 증빙을 확인한다. 이전 Docker 시작 장애는 해결됐으며 화면 알림 확인이나 공유 방식 변경을 기다리는 상태가 아니다.

## 최종 인수 보완 — 2026-10-28
AUTH-001: 유니코드128 code point 비밀번호 변경 회귀 실패를 먼저 재현한 뒤 수정했다.14/129개 거부와128개 변경·로그인·탈퇴 통합 테스트 PASS. 현재 전체 Java33개 PASS. 소스 변경 후 새 JAR/이미지 재빌드·보안 검사·실행 digest/JAR SHA 대조·실제2출처 수집 모두 PASS. 최종 증빙 시각2026-10-28T05:28:54Z.

## 외부 접속 인수
사용자가 Docker privileged port forwarding 옵션만 명시 승인했다. VirtioFS와 방화벽 전체는 그대로다. 공개 proxy 이미지를 재검사 후 실행하고 Let’s Encrypt 외부 검증·인증서 발급, HTTPS200·비회원 뉴스401·HTTP308·Secure/HttpOnly/SameSite=Strict 쿠키, 합성 계정 로그인/수집/삭제를 확인했다. 휴대폰 데이터망 정상 접근은 사용자가 직접 확인했다. 시작/중지 조건은 DEPLOYMENT.md.

## 인증 화면 마무리 — 구현·실행 검증 완료
로그인·가입 간소화, 약관 전문·개인정보 안내, 서버 동의 검증과 V3 마이그레이션을 구현했다. Java35개 PASS, 변경된 앱/빌드 이미지 보안 검사 PASS, V3 실제 DB 적용 및 공개 HTTPS 가입·로그인·탈퇴 검증 PASS. 실제2출처 수집도 성공했다. Git 전달 SHA는 마지막 보고와 Git 이력으로 확인한다.

## 정책 문구 평이화 — 완료
약관·개인정보 처리방침을 일상어로 개정했다. 수집·보관·삭제 조건은 유지하며 기술 설명은 docs에 남긴다. 새 동의 버전2026-10-28.2, Java35개, 소스/JAR/SBOM·변경 이미지 검사 PASS. 공개 HTTPS 두 정책 페이지·구버전 가입400·신버전 가입/로그인/탈퇴 PASS. 동의 기록이나 수집 범위 확대 없이 문구를 개정했다.
