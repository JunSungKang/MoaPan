# V1 전달 결과

전체 상태: COMPLETE — 사용자 변경 기준인 로컬 실행+외부 휴대폰 접속을 충족했다. https://175.195.62.165/ 를 사용자가 데이터망에서 확인했다. 별도 호스팅 배포·main 병합·사업 개시는 포함하지 않는다. CI/CD는 제외했다.

- 구현: 최소 계정·세션·CSRF·권한, 사이트/주소/권리 관리, 실제 worker 수집·요약·태그, 공통 edition·cursor·quota, 신문형 정적 UI·계정별 로컬 관심사·관리자·정책 화면.
- 실제 JDK: Eclipse Temurin27.0.1.0.2+1. Boot4.1.2, Gradle9.8.2, PostgreSQL18.6. 공급자 버전 근거는 VERSIONS.md.
- 증빙 확인: 2026-10-28T06:13:19.100Z; 외부 접속 evidence/public-access.json.
- 실제 출처: NASA News Releases / NOAA News, 2026-10-28T05:28:47.291Z 두 수집 SUCCEEDED. 주소·최근 실행은 evidence/verification.json, 권리 범위는 SOURCES.md.
- Compose: db/web/proxy healthy, worker 실행, migrate exit0. 재생성22기사 보존. 시작 명령: docker compose -f compose.yaml -f scripts/compose-public.yaml up -d --no-build --wait. 접속 URL https://175.195.62.165/ — 휴대폰 데이터망 확인 완료. 공개 모드 실행 명령은 DEPLOYMENT.md.
- 테스트: Java33 PASS/FAIL0/SKIP0, bootstrap9 PASS, 시작 제한 시간3 PASS. 실제2 web quota·worker lease 복구·암호화 격리 복원 PASS. 검증 한계는 TEST_REPORT.md.
- 보안: 소스/JAR/SBOM/이미지5종/도구 검사 HIGH·CRITICAL0. 잔여 MEDIUM·LOW 및 OpenPGP 미포함 근거를 유지. 알려진 취약점0 또는 미공개 취약점 부재를 보장하지 않는다.
- 최종 앱 이미지: sha256:f4ce240bae0958f4b6776046ae8597acb694adfc7cfee132d385afce9e12091d. 다른 이미지와 report SHA는 evidence/verification.json.
- GitHub: https://github.com/JunSungKang/MoaPan / codex/moapan-v1 / PR#1. 최종 커밋 후 guarded push와 원격 SHA 대조로 전달한다.
- 운영 조건: Mac/Docker 실행, 현재 공인 IP와80/443 유지. Caddy 인증서 자동 갱신. 별도 서버·도메인 구매 없음.
- 사업 개시 전: 운영자 정보·상표·콘텐츠 이용계약/범위·사업자 및 언론 관련 등록 여부·법률 검토 별도 확인.
