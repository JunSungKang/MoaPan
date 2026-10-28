# MOAPAN 진행 상태

방법론: Sprint Delivery. 목표: 로컬 V1 구현·검증·증빙·guarded GitHub 전달. CI/CD·GitHub Actions·정기 자동 검사는 사용자 요청으로 제외한다. 승인된 외부 서버·도메인이 없어 외부 HTTPS는 BLOCKED다. 전체 COMPLETE로 판정하지 않는다.

## 구현 및 검증

- 인증·세션·CSRF·권한·계정 관리, 사이트/주소/권리 관리, Java 수집 worker와 lease 복구, 요약·태그·edition·cursor·원자 quota 구현.
- 정적 신문 화면·계정별 IndexedDB 관심사·로컬 추천·관리자/정책 화면 구현.
- 정확한 Temurin27.0.1.0.2+1·Boot4.1.2·Gradle9.8.2·PostgreSQL18.6.
- Java 단위/통합/브라우저32개 PASS, bootstrap9개 PASS. 실제2 web quota와 worker SIGKILL 후 lease 복구 PASS.
- NASA/NOAA 실제 수집, 기사20건 영속, 암호화 백업/격리 복원 확인. 최종 시각과 검사 digest는 evidence/verification.json.
- 소스·JAR·SBOM·실행/빌드 이미지5종·도구 검사. HIGH/CRITICAL0, MEDIUM/LOW 및 OpenPGP UNKNOWN 영향 검토는 SECURITY_REVIEW.md.
- GitHub main 보호 설치/read-back 및 현재 server guard PASS. main merge는 승인되지 않았으며 수행하지 않는다.

## 남은 단계

1. 실제 실행 DB/proxy까지 최종 스캔 이미지로 일치시킨 후 수집·상태 재검증.
2. 인수 추적·증빙 정리와 비밀값/임시 산출물 점검.
3. 기능 단위 사람 commit·guarded push·원격 SHA 대조. 백엔드4add120·UI a11320a 기능 commit 완료. 인프라/증빙7a852eb까지 guarded push 완료. 원격 codex/moapan-v1과 로컬 SHA 7a852eb32da6cba41f97215ea0415b456946d319 일치를 확인했다. 후속 문서 커밋 SHA는 최종 전달 기록에서 별도 확인한다.
4. 승인된 환경 제공 전 외부 HTTPS 검증은 진행할 수 없다. 상표·콘텐츠 계약·사업 등록·법률 검토는 별도다.

재개 시 현재 git status, goal, 실행 컨테이너 digest, JUnit/scan 결과부터 확인한다. 실패한 명령을 성공으로 기록하거나 보안 게이트를 낮추지 않는다.

## 현재 재검증 장애 (2026-10-28T03:16Z)

최종 이미지 대조에서 DB/proxy 실행 이미지가 최신 scan 대상과 달라 재생성을 시작했다. Docker 엔진 ping과 같은 DB 이미지의 단독 postgres --version은 성공하나 bind mount가 있는 db/proxy start가 대기 중이다. 이전 smoke 성공을 현재 Compose 성공으로 주장하지 않는다. Docker Desktop 재시작은 다른 프로젝트 컨테이너에 영향을 주므로 사용자 확인 대기. 데이터 볼륨은 삭제하지 않았다. 재시작/회복 후 Compose 재실행·실제 smoke·record-evidence 재생성이 필요하다.
