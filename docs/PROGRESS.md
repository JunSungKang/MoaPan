# MOAPAN 진행 상태

방법론: Sprint Delivery. CI/CD·GitHub Actions·정기 자동 검사는 사용자 요청으로 제외한다. 승인된 외부 서버·도메인이 없어 외부 HTTPS는 BLOCKED다. 전체 COMPLETE로 판정하지 않는다.

## 구현·실행 검증
- 인증·세션·CSRF·계정 관리, 사이트/주소/권리·Java worker·요약·태그·edition·cursor·원자 quota, 정적 신문·계정별 IndexedDB 개인화·관리자/정책 화면 구현.
- Temurin27.0.1.0.2+1·Boot4.1.2·Gradle9.8.2·PostgreSQL18.6. Java32개, bootstrap9개, 시작 도구 제한 시간3개 PASS. Docker 복구 후 PostgreSQL 통합17개 재실행 PASS, 전체 Gradle 검증 명령 성공.
- 2026-10-28T05:19Z NASA/NOAA 실제 수집 모두 SUCCEEDED. 합성 검증 계정·세션 삭제. 현재 기사22건, 지면 후보12개. 상세 시각은 evidence/verification.json.
- Docker Desktop4.97.0/Engine29.9.2. VirtioFS와 저장된 폴더 권한을 유지하고 사용자 tccd 응답 정체를 재시작으로 해소했다. 상세 증거·해석 한계는 BUGS.md.
- proxy 설정 디렉터리0755/파일0444 수정 및 HTTP healthcheck 추가. 최종 db/web/proxy healthy, worker 실행, migrate exit0. DB/proxy 강제 재생성 후 기존22개 기사 ID 모두 보존(05:20Z).
- 실행 중4개 컨테이너와 검사 이미지 digest 대조, runtime JAR SHA 일치 PASS. 소스·JAR·SBOM·이미지5종·도구 검사 HIGH/CRITICAL0. MEDIUM/LOW 잔여와 공급자 확인 한계는 SECURITY_REVIEW.md. 무결점 보안이나 외부 배포 승인으로 해석하지 않는다.
- 실제2 web quota·worker SIGKILL lease 복구·빈 DB migration·암호화 격리 백업 복원도 별도 검증했다.

## 전달 및 남은 조건
- 기존 guarded commit/push와 PR#1 생성·사람 assignee 설정 완료. 복구·proxy 수정·최신 증빙 커밋767f2b409e534a1f74ceb3630050a556b6e88eb9를 guarded push하고 원격 SHA 일치를 확인했다.
- main merge는 승인되지 않아 수행하지 않는다. 현재 head의 독립 App formal review는 병합 전 별도 gate다.
- 외부 HTTPS 검증은 승인된 서버·도메인 미제공으로 BLOCKED. 공급자 보안 검토 일부 불확실성·잔여 MEDIUM/LOW는 인수표 AC-03 PARTIAL 유지.
- 사업 개시 전 상표·콘텐츠 이용 범위·운영자 정보·사업/언론 등록 여부·법률 검토는 별도 확인한다.

재개 시 git status와 실제 컨테이너/검사 digest 및 증빙을 확인한다. 이전 Docker 시작 장애는 해결됐으며 화면 알림 확인이나 공유 방식 변경을 기다리는 상태가 아니다.
