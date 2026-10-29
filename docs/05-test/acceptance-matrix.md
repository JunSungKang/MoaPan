# 인수 추적

> 문서번호: MP-DOC-012 · 기준: 저장소 현재 리비전 · 상태: 유지관리 · 작성: 개발 담당 · 검토: 독립 리뷰 담당

CI/CD는 사용자 명시 제외. PASS는 아래 실제 시험 범위의 통과이며 무결점·모든 장애 조합 검증을 뜻하지 않는다. 구현 FS 대응은 FUNCTIONAL_SPEC.md, 코드/시험 상세는 TEST_REPORT.md, 실행 수치·digest는 evidence/verification.json과 연결한다. 사용자 최신 기준은 별도 호스팅 배포 대신 로컬 Mac 서비스의 외부 접속이다. 휴대폰 데이터망 접속 확인으로 이 기준까지 COMPLETE. 보안 잔여사항은 유지한다.

|AC|REQ|범위|상태|코드·시험·실제 증빙|
|---|---|---|---|---|
|AC-01|10,11|기술·정확한 JDK|PASS|RuntimeSecurityTest; VERSIONS.md; 실행 JAR 일치|
|AC-02|10,11|Gradle 공급망|PASS|Wrapper SHA·lock·strict metadata; 전체 task 실행|
|AC-03|11|산출물 보안|PASS|로컬 scanner HIGH/CRITICAL0·KEV 대조·공급자 CVE22건 재조회/노출 검토; MEDIUM/LOW 잔여 유지: SECURITY_REVIEW.md|
|AC-04|11|truststore|PASS|RuntimeSecurityTest; TrustAudit 호스트/실행 이미지14 TLS 거부|
|AC-05|07|정적 화면|PASS|sourceCheck; BrowserTest; static ES Modules|
|AC-06|03,08|가입·금지필드|PASS|SecurityIntegrationTest: 과다필드·role·Argon2id|
|AC-07|03,08|세션·CSRF·계정|PASS|SecurityIntegrationTest: 실제 JDBC 세션·교체·만료·변경·탈퇴|
|AC-08|08|회원 전용401·관리자403·제한 공개200|PASS|SecurityIntegrationTest; local-smoke 비회원/일반회원|
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
|AC-23|10|Compose·영속·백업|PASS|최종 실행/scan digest 일치·HTTP health·재생성22기사 ID 보존; 빈 DB migration·암호화 격리 복원|
|AC-24|12|로컬 서비스 외부 HTTPS 접속(사용자 변경)|PASS|공인 IP 인증서·HTTPS200·회원 전용401·Secure 쿠키·휴대폰 데이터망 사용자 확인; evidence/public-access.json|
|AC-25|12|GitHub 전달|PASS|guarded 사람 commit/push; 복구 커밋767f2b409e534a1f74ceb3630050a556b6e88eb9 원격 일치; PR#1 open, merge 미수행|

인증 UX/약관 보완: AC-06·07·21·22에 SecurityIntegrationTest.signupRequiresCurrentExplicitConsent 및 BrowserTest.simpleAuthFormsRequireConsentAndShowReadableTerms 추가. 전체 Java35 PASS/FAIL0/SKIP0. 약관 동의 버전·시각 외 회원 프로필이나 로컬 관심사 전송은 추가하지 않았다.

로그인 제한 안내 보완: AC-07·09·22에 loginWarningsAndRetryAfterFollowStoredExpiry 및 loginWarningCountdownAndUnlockAreVisible 검증 추가. Java37 PASS, 공개 HTTPS 실제 응답과 합성 계정 정리 PASS(evidence/login-countdown.json).

사용자 승인 입력 길이 변경: AC-06·07·22에 accountLengthBoundariesApplyToSignupLoginAndChange, usernameAndPasswordLengthsMatchBrowserAndAccountMenu 검증 추가. Java39 PASS/FAIL0/SKIP0. 비밀번호 최소6자·아이디1~128자가 최신 제품 요구사항이며 기존 최소15자·아이디4~32자를 대체한다.

화면 분리: AC-20·21·22에 interestsAndAccountPagesKeepSeparateResponsibilities 검증 추가.40개 Java 테스트 PASS. 관심사 저장 스키마 변경 없음, 계정 화면 관심사 API 미요청, 탈퇴 시 해당 계정 설정만 삭제.

기기 간 이전 추가: REQ-04/05 → FS-21 → preferences-transfer.js/preferences-transfer-ui.js/settings.html → BrowserTest의 encryptedPreferencesTransferBetweenIndependentBrowsers 및 portableSettingsRejectTamperingOtherAccountsAndInvalidSchemas PASS. AC-20/21/22 회귀 포함. 이는 서버 저장 없는 수동 이전이며 자동 동기화 인수로 간주하지 않는다.

공개 지면 변경(최신 사용자 요청): AC-08의 비회원 뉴스 일괄 차단은 회원 전용 API에 유지하고 별도 공개 edition은30개/no cursor로 허용한다. publicEditionIsBoundedAndProtectedPagesStayPrivate, representativeInterestsRequireAdminCsrfAndCurrentVersion, guestMenusProtectedPagesAndSearchDialog, memberAndAdminMenusAndRepresentativeEditor, defaultInterestsAndPolicyEditionsAreExplicit로 검증한다. 기존 판별 기준을 몰래 완화하지 않고 명시적 정책 변경으로 기록한다.

최초판·스크랩 사용자 변경: FS-23 → config.js/common.js/index.html/news.js/newspaper.css → BrowserTest.editorialScrapGalleryAndInitialPolicies 및 기존 popup/메뉴 테스트 PASS. FS-25/AC-06 → terms/privacy/signup/AuthController → signupRequiresCurrentExplicitConsent/simpleAuthFormsRequireConsentAndShowReadableTerms PASS. 기존 정책 이력 제공 요구는 이번 명시적 삭제 요청으로 대체했다. AC-22의4/2/1단·키보드·인쇄·검색·넘침 검증 유지. 최신 총48 PASS, 공개 실행 evidence/scrap-layout.json.

FS-23/AC-22 상단 보완: 검색 버튼 채움 제거·모바일 구분선/메뉴 정리·푸터 우측 정렬. 기존 BrowserTest14개 회귀 PASS 및390/768/1440px 스크린샷 확인. 수집/인증/개인화 동작 변경 없음.

2026-10-29 확장: 날짜/주제 경계는 CollectionFilterTest·SecurityIntegrationTest(collectionScopeIsEnforcedBeforeDatabaseWrite/arxivAdmissionIsSharedAndCoolsDown), 실제6수집주소130건은 evidence/topic-collection.json 참조. NIST RSS0건은 실제 수집 건수에 포함하지 않는다.

카드 개정: 작성일/작성자 미제공 표시·태그 주제·여섯 항목만 노출(Playwright), Atom/JSON 작성자와 R&D 추출 원문 일치(단위), 기존 요약 재처리·수동 잠금 보존(PostgreSQL) PASS. 별도 인용문 블록 없음. 번역 실제 처리/배포는 별개 미완료이며 이 카드 개정의 완료와 혼동하지 않는다.

서버 검색 후속: AC-09/20/21/22에 NewsSearch·SecurityIntegrationTest.serverSearchIsBoundedLiteralPrivateAndRightsAware·BrowserTest.serverSearchUsesPostOnlyOnSubmitAndLoadsOneSlideAtATime을 연결. 검색어 전송 금지는 사용자가 승인한 일시 처리 범위로 변경했으며 검색 이력 미저장·관심사 비전송은 유지한다. 위 실제 테스트63개/HTTPS 증빙 통과. 기사 제목·요약 실제 번역은 아직 미완료.


### Linux 자동 번역 추가 인수
- Linux ARM64 CPU 실제 모델 실행/네트워크 격리/모델 checksum/모델 프로세스 종료 자동 복구: PASS.
- 규칙 검증·항목별 checkpoint·최대3회 품질 재시도·인프라 장애 분리·lease heartbeat·권리철회: 단위/통합 PASS, TEST_REPORT 참조.
- 원격 Linux·AMD64·GPU·전체 기존 기사 번역 완료: 미검증 또는 진행 중.


### 중간 정리/Helm 추가
README 최신화·스크립트 분류·실행 참조·Compose 기동/데이터 보존: PASS. Helm lint/render/3가지 부정입력: PASS. Kubernetes 실제 설치·이미지pull·PVC·Ingress TLS·egress 운영 제한: NOT_RUN/추가 구성 필요.
