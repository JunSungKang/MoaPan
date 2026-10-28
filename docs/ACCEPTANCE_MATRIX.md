# 인수 추적

CI/CD는 사용자 명시 제외. PASS는 아래 실제 시험 범위의 통과이며 무결점·모든 장애 조합 검증을 뜻하지 않는다. 구현 FS 대응은 FUNCTIONAL_SPEC.md, 코드/시험 상세는 TEST_REPORT.md, 실행 수치·digest는 evidence/verification.json과 연결한다. 외부 배포와 보안 잔여사항이 있으므로 전체 COMPLETE가 아니다.

|AC|REQ|범위|상태|코드·시험·실제 증빙|
|---|---|---|---|---|
|AC-01|10,11|기술·정확한 JDK|PASS|RuntimeSecurityTest; VERSIONS.md; 실행 JAR 일치|
|AC-02|10,11|Gradle 공급망|PASS|Wrapper SHA·lock·strict metadata; 전체 task 실행|
|AC-03|11|산출물 보안|PARTIAL|로컬 scanner HIGH/CRITICAL0; MEDIUM/LOW 잔여 및 공급자 조회 한계: SECURITY_REVIEW.md|
|AC-04|11|truststore|PASS|RuntimeSecurityTest; TrustAudit 호스트/실행 이미지14 TLS 거부|
|AC-05|07|정적 화면|PASS|sourceCheck; BrowserTest; static ES Modules|
|AC-06|03,08|가입·금지필드|PASS|SecurityIntegrationTest: 과다필드·role·Argon2id|
|AC-07|03,08|세션·CSRF·계정|PASS|SecurityIntegrationTest: 실제 JDBC 세션·교체·만료·변경·탈퇴|
|AC-08|08|401·403|PASS|SecurityIntegrationTest; local-smoke 비회원/일반회원|
|AC-09|08|quota·cursor|PASS|SecurityIntegrationTest 경쟁·변조; 실제2 web60회·재로그인429|
|AC-10|08|공개 한계·필드|PASS|명시적 NewsService DTO; 정책 안내·secret 부재|
|AC-11|02,09|사이트 관리|PASS|CatalogService; SecurityIntegrationTest; Admin UI|
|AC-12|02|사이트·주소 분리|PASS|CatalogService; draft 활성화 거부·source/site 관계|
|AC-13|02,08|기술 점검·미리보기|PASS|SecurityIntegrationTest preview 미저장·승인 분리|
|AC-14|02,09|동시 운영|PASS|Worker/CatalogService; 늦은 결과·version409·job 중복|
|AC-15|02,09|보관·권리 철회|PASS|SecurityIntegrationTest; NewsService 조회 시 권리 재검사|
|AC-16|01,06|실제2출처|PASS|local-smoke.mjs; NASA/NOAA 각각10기사; SOURCES.md|
|AC-17|01,10|수집 장애·복구|PASS|fixture429/503/timeout 재시도·중복·변경; 실제 SIGKILL lease 복구|
|AC-18|01,08|SSRF·XXE·크기|PASS|ContentTest 고정 DNS resolver·redirect·주소·XXE·응답크기|
|AC-19|05,06|요약·태그|PASS|ContentTest; SecurityIntegrationTest 수동 잠금·태그 병합|
|AC-20|04,05|로컬 추천|PASS|BrowserTest 고정 clock·계정 namespace·제외 우선|
|AC-21|04|개인정보|PASS|BrowserTest 네트워크·문장 폐기; DB 금지테이블·TTL; 로그 정책|
|AC-22|07|신문 UI|PASS|BrowserTest390/768/1440·긴 제목·로딩·빈결과·오류·키보드·인쇄|
|AC-23|10|Compose·영속·백업|PARTIAL|이전5역할·빈 DB migration·20기사·암호화 복원 PASS; 최종 이미지 재생성 지연은 PROGRESS.md|
|AC-24|12|외부 HTTPS|BLOCKED|승인된 외부 서버·도메인 미제공|
|AC-25|12|GitHub 전달|PASS|guarded 사람 commit/push; 7a852eb32da6cba41f97215ea0415b456946d319 원격 일치. 후속 변경은 다시 확인|
