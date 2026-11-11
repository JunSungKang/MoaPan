# 진척현황

최신: 계정별 구독 권한/V23·서비스 반영·독립 코드 재리뷰 APPROVE 완료.362시험(171/122/69)·Nu12페이지0·최종보안/이미지/trust/HTTPS통과, 기존300/2/1보존·구독자동부여없음. 최종 App current-head 승인 상태는 PR #27이정본이며develop/main병합은별도승인. MP-DOC-035/036참조.

- 7단계 산출물 분류, 등록26개, 관리계획/프로그램목록/이식설계서/경로대장 적용.
- Java34클래스와 JS19모듈 조사·재배치, 모델 포트/순수정책 분리.
- 시험191 PASS와 보안 gate PASS. [시험결과서](../05-test/test-report.md) 참조.
- 운영 모듈은 Spring/PostgreSQL 어댑터와 스키마를 함께 이식해야 한다.
- [과거 진척](sprints/pre-domain-refactor.md)은 당시 상태다.

회색 신문지 스타일: 바탕 질감·기사 조각 색·그림자 조정, E2E23 및 보안 gate 통과. 현재 디자인 브랜치에서 검토/전달하며 develop 병합과 구분한다.

참고 신문 지면 반영: 중앙 큰 제호/머리기사4열+보조2열/기사선, 모바일1열로 교체. 우측광고자리 아래 검색·언어 배치, 자체이미지/HTTPS목적지 설정 제공. E2E24 및 보안검사 통과.

모바일 기사 목록: 600px 이하 screen에서 본문폭100% 블로그형 카드·동일 제목크기·요약좌측정렬 적용. 데스크톱/print신문유지. E2E24 및 보안 gate검증.

발행시각·모바일메타: 현재 로드한 지면 후보에서 최근 collectedAt 기사 선택 후 publishedAt을 분까지 표시. 미상일 때 수집 기준 명시. 기사별 날짜도 분까지, 모바일 메타 우측세로/상단시간nowrap. E2E25 통과.

로그인 제호/폼/약관 중앙정렬 및 중복제목 제거, 전체9페이지 탭제목/설명/색인정책 점검완료. sitemap/robots/HTTP canonical와민감경로noindex 구현, Nu정적HTML오류경고0/197회귀PASS/보안gatePASS. 실제검색엔진등록·색인결과·순위는 미검증.

로그인 언어 선택: 제호 위에서 약관 링크 아래 중앙 별도행으로 이동. 타 페이지 위치 유지, E2E26/9HTML Nu0 검증.

로그인 언어 선택 최종 배치: 화면 우측상단 페이지 공통도구로 분리. 제호/폼/약관 중심 유지. E2E26 및 Nu/보안 gate통과.

로그인 필수·선택적 자동 로그인 구현: 뉴스 HTML/API인증, 로그인페이지인증시지면이동, opt-in30일SpringSession쿠키/서버절대만료. 해당브라우저logout즉시폐기/다른브라우저유지, password/delete전체폐기 통합검증. 실제HTTPS합성계정smoke검증 후 결과 기록.

실제 HTTPS 검증 완료: 비회원 로그인 이동, 자동 로그인 쿠키 보안속성, 로그아웃한 이전 쿠키 거부, 별도 브라우저 로그인 유지 PASS. 합성 계정 삭제 완료. 증빙: docs/05-test/evidence/remember-https.json.

아이디 저장 추가 Sprint: 1) 독립 opt-in 아이디 보관/해제 2) 자동 로그인 체크 연동/비활성화 3) 새 페이지 복원·비밀번호 미보관·해제 삭제 E2E 검증 4) 이미지 재검사·Compose 반영.

아이디 저장 회귀 검증 완료: opt-in·자동 로그인 강제 체크/비활성·페이지 재진입 복원·해제 삭제·비밀번호 미복원 확인. 단위130/E2E27, Nu0, 보안 gate PASS.

보안 재검토 Sprint: 1) 실제 런타임/도구/DB 식별 및 공식 advisory·KEV 갱신 2) 영향분석과 패치 3) 회귀 및 모든 프로젝트 이미지 검사 4) 실행 digest 대조·잔여위험·GitHub 검토. 외부 다른 프로젝트 컨테이너는 변경하지 않는다.

보안Sprint구현·검증: 앱실제본문16KB제한, 검사보고서/DB최신성gate, 공식4rootcacerts조치완료. truststore후203회귀+정책4PASS, 모든실행image+build스캔, 실제HTTPS와실행digest대조. proxy기존HIGH1조건부비노출·OS미수정경고유지; security-review/evidence참조. Compose서비스적용완료.

화면 간격 Sprint: 1) 로그인 저장 옵션 가로 배치·자동로그인 문구 단순화 2) 신문 헤드라인 영역 아래 여백 추가, 모바일 첫 카드와 다음 카드 간격 추가 3) 390/768/1440 레이아웃·체크연동·영문·인쇄 회귀와 화면 검수 후 이미지 반영.

사용자 추가 승인: 애드센스 계정/ID 없음 → 관리자 등록·연결 준비만 구현. V12 광고 설정과 ADMIN/CSRF/version/API 최소출력, 직접 배너/미리보기/표시중지, 클릭 미추적 구현. 애드센스 실제 요청/SDK는 미연결로 명시.

화면/광고관리 완료: 저장옵션 한줄·자동로그인 문구·신문행 여백, 관리자 직접 배너·AdSense준비 등록. V12 적용·기본광고비활성·실제HTTPS 확인, 합성계정삭제. 205회귀/정책4/Nu0/Helm/최종이미지검사PASS, 검사/실행 JAR일치. 애드센스 실제송출은 사용자 승인범위대로 미구현이며 UI/문서에 명시.

승인된 뉴스레터 Sprint: 회원 목록·상세, 관리자 블록 편집기와 발간 스냅샷 분리, DB 압축 이미지와 V13 구현 중. 이메일 발송 없음.

뉴스레터 구현·적용 완료: 목록/주제/최신호 연결/본문·호수, 관리자 블록 작성·그림·참고 뉴스 연결·초안/발간/철회. 원고와 발간본 분리, 비동기 저장/이미지 원고 전환 경합 수정. V13·Compose·Helm13·DB 백업 연동. 208테스트/정책4/Nu0/보안gate 및 HTTPS 경계 확인. 자동 이메일·원고 자동 번역은 범위 제외다. 현재 발간물은 관리자가 작성해야 하며 fixture를 실발간물로 넣지 않았다.

뉴스레터 편집 지면 재구성 Sprint: 1) 실제 잡지/뉴스레터 사례 조사와 기존 레이아웃 재현 2) 최신호 표지·이전호·본문 위계 분리 3) 원고 사이드바와 집중 편집/미리보기·관련 입력만 표시 4) 합성 원고 포함390/768/1440 시각검수·키보드/필터/저장경합 회귀 5) 산출물 보안 검사·Compose 적용·독립 리뷰·GitHub PR. 초기 태블릿 cover 크기 넘침을 검출하여 수정했고 최종 재검증 중이다.

뉴스레터 지면 재구성 검증·서비스 반영 완료: 최신호 표지/이전 발간물/좁은 읽기 본문, 원고 사이드바와 집중 편집·전환형 미리보기 적용. 단위132/E2E31/Nu0/소스·JAR·SBOM·최종앱이미지 gate PASS. 13장 합성 스크린샷을 검수하고 실제HTTPS 자산 hash·접근경계·실행JAR일치 확인. 현재 앱 sha256:c6eb6ded500d4f2f2887a9f985b2d0d86d28a76c0e47fac924fbb6bca38a23c6. 독립 재리뷰 APPROVE, GitHub PR 절차 진행. develop 병합은 별도 요청 전 수행하지 않는다.

사용자 추가 요청: 뉴스레터 전체 삭제 Sprint. 1) 관리자/CSRF/현재version의 원자 DELETE와 최소감사 2) 확인 취소·충돌 시 원고 유지/성공 시 편집기 정리 3) PostgreSQL·브라우저 회귀/보안 검사/Compose 적용. 독립 리뷰가 지연된 최초 저장(version0)의 삭제 후 부활을 지적하여 저장/삭제 공통 UUID 잠금과 삭제감사 표식으로 차단하고 재전송 테스트를 추가한다. DB 구조 변경 없이 V13을 유지한다.

추가 발간 승인: 이번 주(2026-10-26~30)의 실제 저장 자료를 확인했고 기존 원고0건/발간 시퀀스 미사용 상태다. 공식 arXiv 원문 페이지에서 GistRank/Glance/Tessera/Edge LLM Optimization/Edge-Cloud Orchestration 논문의 제목·제출일·초록을 확인했다. 원문을 영속 복제하지 않고 편집부의 독자적 해설과 근거 링크·자체 그림으로 첫 호를 작성한다. 실험 조건의 결과와 전망을 구분하고2026-001호로 발간한다.

삭제 기능·첫 호 최종 반영: 원고 삭제 확인/ADMIN·CSRF·version/물리 콘텐츠 제거/영속 최소감사/늦은 초기저장 재생성 차단 완료. 211회귀(132/47/32)/Nu0/최종보안gate PASS, 독립 재리뷰 APPROVE. 실제2026-001호 「더 큰 AI보다, 덜 낭비하는 AI」 발간, 본문24블록/자체그림1/근거링크5. UTC시각 누락 수정 후 재발간 없이 기존호수·최초발간시각 유지. 실제HTTPS390/768/1440 독자·관리자 화면 검수 및 임시계정0 확인. 현재실행앱 sha256:cf3e54452b050c43db1096772762ef3d6c75b6c65d6c9ca2a378032df6cc187b. PR8에 추가검토 대상으로 정리하며 develop병합은 별도 요청 전 수행하지 않는다.

2026-001호 R&D 전망 개정 완료: 최신 사용자 조건에 따라 이번 주 실제 수집 자료5편만 사용했다. DB에서 모두2026-10-30수집임을 확인하고 기존 공식 공개 초록·메타데이터를 다시 검토했다. 「AI의 다음 3년, 모델보다 시스템을 연구하라」로 같은ID/호수에 개정발간(version4), 세 전망·조건부연구단계0~3개월/4~12개월/1~3년·전환/중단조건·자체로드맵그림 포함42블록. 기간·정량관문은 원논문성과가 아닌 편집부제안으로명시했다. 외부과거/시장자료를추가근거로사용하지않았다. 독립원고검토APPROVE, 실제HTTPS390/768/1440 제목·호수·42블록·링크5·그림1·가로넘침검증PASS, 임시편집계정/세션삭제및잔존0확인. 앱코드/이미지미변경으로 전체Gradle/이미지스캔은재실행하지않았다. 증빙 ../05-test/evidence/newsletter-rd-rewrite.json. 향후발간기준을AGENTS.md에도명시했다.


공개 수집 뉴스 Sprint 진행: 최신 사용자 요청으로 오늘의 지면·수집 뉴스 검색만 비회원 공개로 변경한다. 뉴스레터/계정/개인 관심사/관리 보호는 유지한다. 공개 API 정확 허용 목록과 계정 quota·익명 전역 한도·만료 세션 익명 전환·대표 관심사/메뉴·SEO 경계를 구현했고 PostgreSQL/브라우저/보안 회귀를 실행 중이다. 서비스 반영과 GitHub 전달은 최종 검사 후 진행하며 현재 성공으로 보고하지 않는다.

독립 검토에서 세션 만료 뒤 뉴스레터 소개의 잔존/늦은 응답 삽입을 발견해 인증 해제 시 제거와 세대 검사를 추가했다. 최초214회귀/이미지검사 후 실제HTTPS smoke가 로그인 이동의 HTTP 절대주소를 검출하여 수정했다. 전체검사·이미지검사·실제HTTPS 검증을 다시 진행 중이며 실패를 최종PASS로 합산하지 않는다.

공개 수집 뉴스 변경 완료: 비회원 오늘의지면42개/기사검색140개·대표관심사, 뉴스레터/설정/계정/관리보호유지. 독립검토의인증해제소개잔존/늦은응답과실제HTTPS검출HTTP리다이렉트를수정했다. 최종214회귀(132/48/34)·정책4·Nu0·보안gate PASS, 이미지 sha256:47b30aaf13c58138e0e46324c0f0ab328c34316300accf1768694b4db3d529c3를Composeweb/worker에동일digest적용. 실제HTTPS24검증/실브라우저기사표시·보호페이지HTTPS로그인전환PASS. JAR 102506508af4a808c0ce7eb07cc2d48c12f17db7471ae82e8a3df10948a1e5ac 실행일치. DB마이그레이션/회원정보변경/기사삭제없음. 증빙 ../05-test/evidence/public-newspaper.json. GitHub commit/push/현재head독립리뷰를진행하며develop병합은별도요청전수행하지않는다.


나머지 사업화 기반 계획 구현 승인 수신. 앞선3번 수정 요청을 구현 승인으로 잘못 해석한 사실과 이후 명시 승인 선행 규칙을AGENTS/계획에기록했다. 주간근거/전망/발간gate·출처포함출력·기업업무양식·페르소나익명집계·구독혜택분리를구현중이다. 실제결제/가격/참가자조사/성공확률은생성하지않는다. 문서028에목표24→68의조건과미실행구분/후속안/모집과제를정리했다.

나머지승인계획의개발·로컬운영반영완료: 주간근거/사실·전망/연구관문/개정·재게재권리확인, 출처복사/그림포함HTML/회의·우선순위·실험양식, 개인30명·기업15개사페르소나계획/익명로컬집계, 구독혜택port(무료preview)를반영했다. 실제고객평가/점수상승/유료결제/조직SaaS/가격은미구현·미실행범위를분리했다. 전체229회귀(140/50/39)/정책4/Nu0/문서28/최종앱검사PASS, 이미지 sha256:db82be6087ffea3ffa11b38a2dbb6930833734b7cab1b047d2199380d6ae9b98를Compose에적용했고실제HTTPS/브라우저/암호화V14복원확인. 기존2026-001본문유지·근거5/전망3·개정2, 최초발간시각기존증빙복원. 임시계정/복원DB/키/백업잔존0. 앞으로계획만제시한단계에서는명시구현승인전작업하지않는다. GitHub전달/현재head독립리뷰를마무리하며병합은별도승인전진행하지않는다.

최종독립HTML출력보완: UTF-8/모바일viewport를명시하고기존출처/CSP구성을유지했다. 전체229회귀/최종JAR·SBOM·이미지검사재실행PASS. 최종앱 sha256:433f4af4bfaceecb8c74059c5ea3a12a4e1e0bf43c690e1788c4680394038011, JAR 6d216668118b46b78da963622288eca4543de82c85aea08254f274294b577865; Compose동일digest와추가HTTPS5경계·실제output모듈hash를확인했다. 앞선실인증브라우저/다운로드/복원증빙은직전이미지의전체시나리오이며최종변경은head메타데이터만으로분리기록했다.


사용자 정정에 따른 평가 기능 제거 진행: 재평가 요청을 서비스 기능으로 오해해 추가했던 화면·모듈·메뉴·접근 설정·번역·스타일·전용 테스트를 제거했다. 현행 명세는 분석 문서상의 재평가로 정정했다. 뉴스레터 발간 근거·재게재·연구 양식 및 DB/발간본은 유지한다. 아래 과거 평가 화면 검증 기록은 당시 사실이며 이 제거 작업으로 대체된다. clean 전체 검증과 최종 이미지/HTTPS 반영 후 결과를 추가한다.

평가 기능 제거 완료: 평가 HTML/JS3개·메뉴·보안 경로·전용 스타일/번역/테스트를 제거했고, 원래 없던 DB 테이블/데이터를 새로 만들거나 변경하지 않았다. 뉴스레터 연구/발간/재게재/양식은 보존했다. 공통 예외 처리기의 정적 파일 부재503 오류를404로 바로잡고 일반 없는 파일 회귀를 추가했다. 최종 clean 전체230건(단위141/PG51/브라우저38) PASS, 실패/오류/skip0, Nu0/정책4/문서28 PASS. 최종 앱 sha256:0e5fb08c841e7adddf8b5084fa9be617614c43f5feab99e6435a1e724431ac3a를 web/worker에 적용, JAR 7821ef88de0c0a118a4a4d06a38c42dca7b5c32a3341d85882c69953490c9058 일치, 삭제3주소404·뉴스200·보호302/401·최신CSS/번역hash 확인. 자세한 [제거 증빙](../05-test/evidence/newsletter-evaluator-removal.json). 독립 리뷰 후 GitHub 브랜치로 전달하며 develop/main 병합은 별도 승인 대상이다.

고정 보안 패치 착수:20개 ID/기준 동결, 공식 advisory/KEV/Trivy 공급망 사고와 실제5종이미지 baseline 조사. baseline DB갱신성공. go-jose/v3 공식 패치 없음과 OS 잔여를 완료로 세지 않는다. 인증 세션/계정 변경 경합 수정을 진행하며 신규 비밀번호15자 정책 충돌은 사용자 확인 대기.

## 보안·AI·OSS 후속 상태 — 2026-10-31
보안 전용 브랜치와 기존 뉴스레터 기능 유지 조합을 분리했다. 단독218/조합240 테스트·실모델2회·최종Compose/HTTPS19개 검증을 완료했다. 원래20항목 정의 해시는 유지하며 S05/S06 공급자 미패치, S07 사용자 유지 정책을 미완료로 둔다. A01~A05 확인, A06은 보안 단독 PR·현재 head 독립 리뷰 전달을 남겼다. CI/CD·main/develop 병합·이미지 외부 공개 배포는 하지 않았다.

2026-10-31 후속 승인: 전역 github-app-contract.mjs의 기준 갱신 교착을 명시 승인 범위에서 수정, 회귀8개 통과. 전체 server guard 후 develop 0ce47ba를 기존 보안 브랜치에 통합(37db8a7). G01~G11 구현/검증 진행 중, 완료로 합산하지 않음.

G01~G11 코드 작성 및 152단위/64PG 통합 회귀 진행. 첫 UI 회귀는 새 번역 버튼 추가로 기존 광범위 locator가 모호해 실패하여 기존 저장 버튼의 정확한 이름으로 수정했다. 독립 리뷰의 구정책 category검색/공유PVC 배치/자정 정산3건 수정. 실모델 정상2건/공격6개 보류 확인. 실제 취소 시험은 비스트리밍 지속 계산을 발견하여 SSE로 수정하고 재검증 중. worker는 합성 모델 시험 동안만 중지 중이며 web/DB/기존 데이터는 유지. 최종 검사/이미지 재빌드/Compose/PR 갱신 미완료.

실모델 G06 검증: SSE 변경 후 실제 CPU 번역 추론 시작을 관찰하고 HTTP 취소 후 슬롯이 유휴로 돌아오는 것을 확인. 정상 번역/분류2건·공격6건 보류·취소1건 PASS. 기존 worker 재시작 완료. 2차 독립 리뷰 지적(Helm immutable selector, 객체형 SSE function_call) 수정 및 회귀 추가. 최종 gate는 마지막 코드 변경 후 다시 실행 예정.

G01~G11 최종 회귀255건(153/63/39), JAR/SBOM/source/렌더Helm 및5종runtime image 검사, 실모델 정상2+실제취소1/공격6보류, HTTPS20검사 통과. V16 실제 적용과 기사300/계정2/뉴스레터1 보존, 실행JAR SHA 일치 확인. worker/web 정상 실행. 원래 S05/S06 공식 미패치 및 S07 사용자유지 정책은 미완료. 최종 독립 리뷰와 전달·develop 통합 상태는 [PR9](https://github.com/JunSungKang/MoaPan/pull/9)의 최신 head 기준으로 확인한다.

S06 후속: 공식 Alpine3.24.3(main/community 지원2028-06)와 같은 Temurin27.0.1.0.2+1의 공식 musl amd64/arm64 배포를 확인했다. 별도 security-runtime 브랜치/정상 guard 후 Ubuntu 구성요소 제거 후보를 검증 중. 현재 운영 이미지는 PR9 검증 digest를 유지하며 후보 검증 전 승격하지 않는다.

S05/S06 후보 구현: 공식 v4 JOSE 백포트와 원본5개 패키지 회귀, 미패치 공개 API 공격5개 실패/패치 후 통과, 8개 이미지의 OS 발견0, 실제 OS/JDK 루트 점검 진행. 독립 리뷰에서 공개 API 시험 누락·패키지 갱신 후 루트 복원 가능성을 검출해 수정했다. 최종 실모델 취소가 4초 기준에 실패하여 운영 승격 보류 중이다. 인터럽트 비반응 InputStream 재현→실패→감시 close 수정 후 성공; native prefill batch64로 조정해 실제 취소 재검증 중. 원래 기준을 완화하거나 실패를 PASS로 합산하지 않는다. 운영 기사300/계정2/뉴스레터1 유지.

런타임 후속 운영 반영 완료: 최종256회귀/Node5/JOSE5패키지/실모델 정상2·공격6보류·4초내실제취소1/8이미지OS발견0/최종HTTPS21 PASS. 암호화 격리 복원과 실제DB300/2/1 보존, 실행6개 digest 일치 확인. 임시Compose·DBvolume·복원DB·백업키·암호화백업 제거. 현재 app sha256:85e6653e28a167c02bca78f8b76725dd0fb7328ca74760552df99c81a10f46d6, JAR27a6da342d9fe66920ab2caec3074d5bdda11498b1ca26ab8abf19117fa75e81. S05공식수정백포트/S06취약OS제거 검증 완료, S07사용자정책유지, S20후속GitHub전달대기. 현재18/20이며 정의/기준을 바꾸지 않았다.

PR10 guarded 사람 commit/push·생성 및 1db7303 독립 리뷰 APPROVE 완료. 최종 점검에서 패치의 필수 문맥 공백이 Git 공백 경고로 나타나 zero-context 형식으로 정리했다. 실제6개 수정 소스·Caddy 실행 바이너리는 동일하며 새 proxy/runtime-build digest 재검사와 운영 proxy 교체·HTTPS21 재검증을 완료했다. 원래20개 정의 동일, S05/S06/S20 근거 완료로19/20·S07 사용자정책 미체크. 현재-head App formal 승인과 develop 병합·브랜치 정리는 PR10 실제 상태 및 종료 보고를 따른다. 앱/JAR·DB·모델 실행 digest는 앞선 검증값을 유지한다.

2026-11-01 전페이지 디자인·SEO Sprint 착수. clean develop b602611 기준. 메인의 .content-menu가 뉴스레터 전용CSS에만 의존하여 inline 글자·간격 누락이 발생함을 확인했다.11페이지의 공통 UI를 중앙 스타일로 통합하며 별도SEO에이전트가 읽기전용감사중이다. 제품코드 변경 승인=현재 사용자의 통일성 유지 지시, SEO는 검사/보고 범위. 기존회원/기사/뉴스레터·모델·인증경계·CI/CD 제외 유지.

전수 화면 검증 중간: 초기72상태/기존 기능 회귀 통과. Nu가 제목 H1 정리 후4개 페이지의 H1→H3 건너뛰기를 검출해 하위 제목을 H2로 바로잡았고11개 최종 메시지0. 시각 검수에서 모바일영문 검색 문구 줄바꿈·상세 복귀링크의 한글잔존을 추가 수정했다. 인증푸터의 옛12px 선언도 제거해공통13px에맞추고최종72상태/전체E2E재검증중이다. 별도SEO감사는canonical원본0/4·뒷면기사발견성·공개검색표본자체URL0건을확인했으며실제포털색인은UNKNOWN으로남긴다. SEO정책은자동변경하지않는다.

전 페이지 디자인 통일·SEO 감사 로컬 검증 및 서비스 반영 완료:258회귀(154/63/41),72화면·Nu11페이지 메시지0, 최종 앱/빌드 이미지 검사 PASS. 공통14px 메뉴·간격 최소16px·13px푸터·보조헤더를 적용했고 신문 본문·간결한 인증 폼·잡지 편집 배치는 보존했다. 검증 앱 d4d31075e0ac를 web/worker에 적용, 최종JAR0845c6587131 일치·HTTPS자산11개일치·공개/보호경계 확인. DB300/2/1·proxy/모델이미지 유지. 독립SEO의 메타11/11·공개4/4·원본절대canonical0/4 및 포털색인UNKNOWN은 [감사 결과서](../05-test/seo-web-audit.md)에 정리했다. [증빙](../05-test/evidence/design-seo-20261101.json). GitHub 현재head 독립리뷰/PR 전달 상태는 해당 브랜치 PR을 기준으로 확인하며 develop/main 병합은 이번 작업에 포함하지 않는다.

관리 화면 추가 정리 착수: 사용자 요청으로6개 주메뉴와 수집/주제의 하위탭으로 작업영역을 분리한다. 기존 PR11에 후속 변경을 쌓으며 DOM/입력값을 유지하고 기존 API/ADMIN/CSRF/version을 보존한다. 이전현재head승인은후속push로무효화되므로최종head독립리뷰를다시진행한다.

관리자 탭 사전 검토에서 수집 결과가 접힘 안에 숨는 회귀를 확인했다. 새 브라우저 시험으로 FAIL을 재현하고 source-result를 접힘 밖으로 옮겼다. 미리보기/202접수 문구·작업ID가 보이는지와 기존 메뉴/저장 전체를 재검증한다.

관리자 재구성 최종 로컬 검증·서비스 반영 완료:6개 주메뉴·수집3/주제2 하위탭, 메뉴전환 입력 유지·키보드·해시/뒤로가기·늦은응답 비침범. 263회귀(154/63/46)/관리54화면/전체72화면/Nu0/최종 앱·빌드 이미지 gate PASS. 결과 숨김을 FAIL로 재현한 뒤 수정·전체재검증했고 앱af492f8866e4와 JARb5c4e9dae080을 Compose web/worker에 적용·실행일치 확인했다. HTTPS자산15개일치/권한경계/DB300·2·1 보존. [증빙](../05-test/evidence/admin-navigation-20261101.json). 후속커밋push뒤 PR11 현재head의 독립리뷰를 다시 받으며 develop/main병합은 포함하지 않는다.

기사 검토 가독성 개선 착수:100개 편집폼 세로 나열을 한 화면 한 기사·이전/다음·제목선택으로 바꾼다. 기사별 DOM 캐시와 명확한 경계·입력 구획, 저장/새로고침 시 수정 유실 방지를 구현한다. 기존 API/DB/ADMIN/모델/공개지면 정책은 유지하며 PR11에 이어간다.

기사 검토 사전 독립리뷰에서 번역 PUT 뒤 늦은 GET이 새 입력을 덮는 기존 경합을 발견했다. 지연 GET 시험으로 FAIL을 재현하고 입력칸별 revision으로 늦은 응답의 덮어쓰기를 막았다. 응답의 최신 version/contentHash는 갱신하여 보존 초안의 다음 저장에 사용한다. 관련 브라우저4건 PASS, 최종 전체회귀/산출물검사 진행 중이며 운영에는 앞선 검증 앱을 유지한다.

기사 검토 최종 검증·서비스 반영 완료:한 화면 한 기사/이전다음/제목선택·번호·명확한 경계/입력 구획·기사별초안/저장중차단/새목록확인. 늦은 번역 GET 회귀 FAIL→수정→PASS,266회귀(154/63/49)·기사18화면·전체72/관리54·Nu0·최종 앱/빌드 검사 PASS. 앱5f8cb0c07239/JAR7009f5d4fdd7을 web/worker에 적용·실행일치/HTTPS17자산일치·DB300/2/1 보존. [증빙](../05-test/evidence/article-review-20261101.json). PR11 후속 current-head 독립리뷰와 전달을 진행하며 develop/main병합은 포함하지 않는다.

날짜별 기사검토·운영입력 개선 착수. UUID수동입력은기사태그/주제수정·별칭·병합/작업조회에있으며제목/이름/시각기반선택으로대체한다. 뉴스레터근거실패시ID노출도정리한다. 날짜필터는서버에서100건제한전에적용하고정렬은허용목록으로제한한다. 명명된4개관리API/서비스만Java변경을승인범위에추가했고인증/DB/모델은유지한다.


관리자 수집일·입력 개선 로컬검증/서비스반영 완료: KST수집일 범위·3정렬을 LIMIT100 앞에서 적용, 드롭다운에 시각/제목/출처 표시, 주제목록과 자동분류기준의 목적/단계/예시·이름체크선택, UUID/slug직접입력 제거·서버자동코드, 최근50작업이름선택. 주제병합의 수동잠금/분류설정 보존과55P03충돌409, 외부관리자주제재조회·미해결저장차단·미리보기설명/발행시각복원을검증했다.275회귀(154/67/54),새18화면/기존72·54행렬/Nu0/최종앱·빌드gate PASS. 앱sha256:5ad3538359a78b7744d26f49c0d878081f29d3d660daa181c211e0036dacd373·JAR6951754b48b478653305376d353aa15c2538dde296daf6056f6fd6e0664186cc를 web/worker에 적용하고 HTTPS23자산·권한경계/DB300·2·1보존확인. [증빙](../05-test/evidence/admin-usability-20261101.json). PR11최종커밋/푸시/현재head독립리뷰를마무리하며 이번요청의develop/main병합은포함하지않는다.


서비스 전체 명명 최종 반영: 사용자 정정으로 전체 서비스를 담고담고(DamGoX2), 의미 여러 소식을 담고 펼치는 판으로 통일했다.11페이지·한영/메뉴/메타/약관/개인정보/출처/다운로드/D파비콘/UA/Spring표시명·현행문서를맞췄다. 발간1호의자체본문·그림도동일연구내용/호수/시각/링크를보존해교체했고기사300·계정2·발간1유지.275회귀/72·54화면상태/Nu0/최종산출물·2이미지gatePASS. 앱sha256:ef8e07a5671fb70e1d05f9cb4d2ade803108e4c0728327fc2b9cfacf029a76ee를web/worker에적용해실행JAR/HTTPS19자산일치와경계를확인했다. [증빙](../05-test/evidence/damgox2-brand-20261101.json). PR11현재head전달·독립리뷰를진행하며develop/main병합은포함하지않는다.

<!-- DOMAIN-PR-REORGANIZATION -->
도메인별 이력 재구성 검증 단계: publication-brand. 검토·병합 결과는 [재구성 증빙](../05-test/evidence/domain-pr-reorganization-20261101.json)과 각 PR의 GitHub 최종 상태를 확인한다. 실행 중인 서비스와 데이터는 변경하지 않는다.


## 공통 코드·브라우저 성능 개선 (2026-11-01)
진행 중: 전수 조사 및 실제 Spring/Chromium 기준 측정. 제품 변경 후 동일 조건으로 재측정한다. 현재 운영 서비스·DB는 수정하지 않는다. 결과는 [성능 검증서](../05-test/web-performance.md)에 기록한다.

성능 개선 검증 완료:282 PASS(155/68/59), Nu0, 소스/JAR/SBOM와 새 web/proxy 이미지 보안 게이트 PASS. 로그인 재접속 assets126123→4200byte, 지면131505→5100byte(동일 조건3회 중앙값). 상세는 성능 검증서 참조. 운영은 그대로이며 이번 PR의 병합·배포 승인 대기.

2026-11-01 사용자 지시로 기존 HTTPS 스킬3개는 수정·이동·삭제·stage·커밋 없이 보존한다. 최초 SHA-256 동일·미추적 상태를 검증했으며 현재 작업 계약에 보존 조건만 명시해 정상 server guard를 통과했다. 제품 변경은 앞선282 PASS 대상과 동일하고 성능 개선 PR 전달을 진행한다. 서버 캐시는 별도 도메인 PR 범위이며 아직 제품 구현·운영 반영하지 않았다.

## 서버 메모리 캐시 기반 (2026-11-01)
브라우저 최적화 PR16은9e680057b347443083c402ff7b11cf333e1c9fd3으로 푸시·독립 검토·App current-head APPROVED 완료, 미병합/운영 미반영이다. HTTPS 스킬3개는 원본 해시 동일·미추적 보존하며 커밋하지 않았다.
별도 server-cache-foundation 브랜치에서 Caffeine 기반·고정 region 예산·60분 TTL·snapshot 적재·저장 해시 V17을 작성했다. 신규 단위9/격리 PostgreSQL3건 통과. 현재 도메인 API에 연결하지 않았으며 전체 회귀·최종 JAR/SBOM 검사·독립 리뷰·PR 전달을 진행한다.

서버 캐시 기반 최종 검증:288 PASS(163/71/54), strict lock/verification·license·문서·소스/JAR/SBOM·격리 runtime 이미지 gate PASS. 이미지sha256:cb2aac91bb8f54a140c672cf7229135f919477c43008dcc1279c9f846d89cee4, JAR6622df757f348c27a51994afa0dbd6d0f71156edbb9fe1dd3b711175f453a836 일치. DB 재시작 시험의 임시 포트 변경 실패를 고쳐 동일 데이터/지문 보존·전체PG71 재검증을 통과했다. 사전 독립 리뷰 차단 결함 없음. 도메인 API 적용·운영 migration/배포·develop/main 병합은 아직 하지 않았다. [기반 증빙](../05-test/evidence/server-cache-foundation-20261101.json).

## 실제 서버 캐시·서비스 우선 반영 완료
기존 브라우저 호출이 도메인별 공통 캐시를 실제 사용한다. 통합330 PASS(164/107/59)·Nu0·JAR/SBOM/소스/최종이미지 gate PASS 후 기존환경 web/worker/proxy에 반영했다. V22/300기사·2계정·1발간물 보존·암호화 격리복원·HTTPS 공개/보호경계·liveEntry9/카드/발간객체·자산304·합성계정삭제 확인. 모든 source150파일과 실행JAR113f46dcf605가 일치한다. 기능PR16~22는 서비스 검증 뒤 독립current-head/App승인을 받고 develop 병합·정확한 head의 remote/local/tracking 부재를 확인했다. Helm기본대기16 오류도22로 고치고 최신SQL검사를 추가했다. 최종 HTTP/문서 포함 전체330건 재검증·Nu0·최종builder613c25b98d65 및 실행앱/proxy 재검사도 통과했다. 이 증빙 commit의 독립current-head 승인·병합 상태는 GitHub PR을 기준으로 확인하며 main은 그대로다. [실행증빙](../05-test/evidence/cache-service-delivery-20261101.json)과 [캐시검증서](../05-test/server-cache.md)를 따른다. 기존 HTTPS스킬3개는 원본해시 동일·미추적·미커밋이다.

2026-11-02 전체 죽은 코드 조사 착수. 기준develop a716c1d, 기존미추적HTTPS스킬3개 보존. 삭제전 Java compiler symbol/JS import·HTML/DOM/운영진입점의 참조를 확인하고 삭제후 서비스·테스트·보안·develop전달까지 검증한다.

전체 미사용 코드 정리 실행·서비스 검증 완료. 중복 조회/도달 불가 migrate 분기와 Bean/읽지 않는 내부필드, JS유령모듈·미사용값·옛 로컬검색,16토큰37CSS분기,obsolete도구2개를 제거했다. 보안parser/프레임워크/운영진입점·22SQL·기존데이터 보존. 모델정책클래스10개byte일치. 최종330PASS(164/107/59)/Nu0/72합성화면/소스·JAR·SBOM·실제앱·빌드gate PASS. 중간UI준비대기2실패는기준완화없이수정후재검증. 실제새앱ec89cdad8d05·JAR4ece479b47d3가web/worker에서일치,4역할·HTTPS·삭제모듈404·캐시·300/2/1보존·합성계정삭제확인. 독립current-head/GitHub전달마감근거는 [미사용 코드 검증서](../05-test/dead-code-audit.md)와 해당PR상태를따른다. HTTPS스킬3개·전역Codex·main은보존한다.


## PageSpeed 후속 착수 — 2026-11-02
제공 보고서와 현재 배포본 재측정에서 데스크톱88/100/96/100, CLS0.244를 확인했다. footer 위치 이동과 익명 account 조회401이 원인이다. pagespeed-desktop-20261102 계약으로 구현·전체 회귀·서비스 반영·PSI 재검증·죽은 코드 무백업 삭제·develop 병합을 진행한다. 이전 HTTPS 스킬3파일은 변경·커밋하지 않는다.


## PageSpeed·최종 코드 점검 검증 완료 — 2026-11-02
336시험 PASS/실패·skip0, Nu11페이지 오류·경고0, source/JAR/SBOM/3이미지 gate PASS. 비회원401 탐지 경로를 정상204세션 조회로 분리하고 정적 nav·화면 공간·공통CSS·modulepreload·공개문서압축·HEAD 헤더·COOP/Trusted Types를 반영했다. 최종 측정 모바일/데스크톱4분야100·CLS0, agentic2/2. 입증된 더 불러오기 잔존 코드·기존CSS파일·중복선언을 백업/stash 없이 삭제했다. 현재 서비스6컨테이너 정상, DB300/2/1·V22/ID해시 보존·합성 계정 삭제·모델 정책10클래스 동일. [검증서](../05-test/pagespeed-desktop.md)·[증빙](../05-test/evidence/pagespeed-desktop-20261102.json) 참조. develop 통합은 승인된 guarded human/App 리뷰 흐름으로 추적하며 main/CI/CD/전역HTTPS스킬은 변경하지 않는다.


## 공통화 재검수 착수 — 2026-11-02
CSS 파일 요청과 선언 중복을 혼용한 설명을 정정했다. PR16은 CSS5개를 유지했으며 PR25의 파일병합 뒤에도 동일조건 상쇄선언48개가 남았다. RF01~12체크리스트와 제품변경 전150상태 computed style기준을 마련했다. 도메인별 JSON재생성/별칭regex컴파일·전체진입점·실행증거까지 재검수한다.


계정별 구독 권한 승인 Sprint 진행: V23·DB-backed grants·HTML/API 공통404·관리자 구독 탭·메뉴/teaser 권한 연결 구현. 전체 회귀 진행 중, 아직 완료/반영/병합으로 표시하지 않음. develop 병합은 별도 승인.


## 구독 계정 목록 후속 — 2026-11-02
기존 PR27 승인 head 위에서 사용자 요청을 반영한다. 조회를 누르기 전 목록이 비어 있던 원인과 기본20/아이디정렬을 확인했다. 변경 전 PG 시험2건에서 기본10 대신20, 미지원sort400을 실제 재현했다. 첫 탭 진입 자동조회/최근 구독·연장순10/만료임박순/10·30·50·100선택/조건변경시첫페이지/늦은응답취소를 구현했다. 범위 PG13+브라우저8 시험을 통과했다. 전체 회귀·최종 산출물/이미지 검사·서비스 반영·현재 head 독립리뷰는 진행 중이며 기존362 결과와 새 결과를 혼합하지 않는다. 기존 계정 권한과 스키마는 변경하지 않고 develop/main 병합은 별도 승인으로 유지한다.

구독 목록 후속 구현·서비스 반영 검증 완료: 기본최근구독·연장순10명 자동표시/만료임박순/10·30·50·100명/검색과페이지초기화.365 PASS(171/124/70),Nu0,최종runtime/builder·JAR/SBOM·trust PASS. 합성resize캡처안정화후구독8건추가재검증. 실제HTTPS 정렬/인원·권한검사와기존기사347/계정2/발간1/구독1·V23해시보존확인,합성계정각3개삭제. 현재PR27 후속commit·push·독립current-head/App승인마감중. develop/main은병합하지않는다. [후속증빙](../05-test/evidence/subscription-list-20261102.json).


## 회수 시각과 이용 종료 일치 후속 — 2026-11-02
사용자 요청에 따라 종료일을남기는동작을PG3실패로재현했다. 서버시각확정/종료·updated_at·감사일치/중복멱등/시작전취소를구현했고V24에서기존REVOKED만보정+version증가하며ACTIVE기간제약을유지한다. PG구독16+마이그레이션1검증을통과했다. 화면이수신종료일을한국시간으로즉시반영하는회귀를보강했다. 전체시험/새JAR·이미지·trust/실제V24/서비스/원격리뷰는진행중이다. 기존권한이나원고·개인설정은변경하지않고현재PR27에전달하며develop/main병합은별도승인대상이다.

회수 종료 시각 후속 구현·서비스 반영 검증 완료:369 PASS(171/128/70), Nu0, 최종 runtime/builder·JAR/SBOM·trust gate 통과. V23→24 migration과 web/worker 재시작, 실제 HTTPS의 종료/변경/감사 시각 일치·중복 보존·시작 전 취소·구 version409·열람404 확인. 기존 실제 보정 대상은0건, 기사/계정/발간/구독은 사전 예상 해시와 일치하며 합성2계정은 삭제했다. 현재 PR27 후속 commit/push·현재-head 독립/App 리뷰 마감 중이며 develop/main 병합은 별도 승인이다. [회수 증빙](../05-test/evidence/subscription-revocation-20261102.json).

## 2026-11-03 필수 수신 이메일 구현 진행
승인 범위: signup 필수 이메일, 인증/복구/계정 등록·변경, 자체 SMTP와 목적 제한 고지. 새 브랜치 codex/identity-email-20261103, PR27 구독 변경을 의존성으로 보존한다. V25는 테스트 DB만 적용했고 운영 V24/기존 이미지는 유지한다. 1차 이메일8+구독1 통합9 PASS. 전체 회귀·SMTP·보안·서비스 반영·PR은 아직 미완료다. 형제 mail-box는 읽기 전용, 전역 스킬 변경 없음. 결제/CI/병합 제외.

## 2026-11-03 이메일 기능 최종 인도 준비
브랜치 codex/identity-email-20261103. 이메일 필수 가입/가입 인증/비밀번호 찾기/기존 회원 이메일 등록·변경/용도 제한 UI·정책·고정 발송 템플릿 구현. 최종396 PASS(179/145/72), Nu 메시지0, SMTP 실제 합성 전달과 read-only/메모리 전용 큐 검증. PR27 의존성 보존, 운영V24/기존 이미지·계정/구독 유지. root DS-0002 HIGH 위험 수용과 운영 발신 주소/DNS 질문의 답변을 대기한다. 보안 게이트 실패를 PASS로 처리하거나 서비스/DB를 교체하지 않았다. 새 SMTP Helm 옵션은 준비·렌더링만 했다. 커밋/PR/정식리뷰 결과는 후속 기록을 따른다. 기존 미추적 HTTPS 스킬은 보존하며 커밋에서 제외한다.

이메일 기능 구현 커밋 `6ffbdebd291ece109b8860bc0ebaf370491471a3`을 guarded human push했고 [PR #28](https://github.com/JunSungKang/MoaPan/pull/28)을 develop 대상으로 생성했다(사람 author/assignee). PR27은 의존성으로 유지하며 두 PR 모두 이번 작업에서 병합하지 않았다. 396개 기능 시험과 SMTP 합성 전달은 PASS지만 Postfix root DS-0002 HIGH가 남아 securityCheck/acceptanceTest 및 운영 전환은 보류한다. root 실행 방식과 실제 발신 주소/DNS 질문은 아직 승인·확정으로 처리하지 않는다. 정식 현재 HEAD 독립/App 리뷰 결과는 PR을 진실 원천으로 확인한다. 기존 운영 이미지/V24와 회원·구독·뉴스 데이터는 교체하지 않았다. 임시 SMTP 컨테이너/볼륨/네트워크를 정리했고 기존6개 서비스만 실행 중이다.

이메일 목표 추가 인수 감사: V24에 합성 계정/실제 Argon2 해시/권한/활성/버전/약관/세션 bytes/구독을 저장한 뒤 V25로 올리는 독립 PostgreSQL 시험을 추가했다. 행 동일성·해시 일치·이메일 추정 미입력·부분 상태 거부·반복 마이그레이션 멱등 PASS. 전체397 PASS(179/146/72), FAIL/SKIP0. runtime 코드/JAR 미변경 및 이미지와 JAR SHA256 일치 확인. 운영 DB도 회원 필드를 읽지 않는 schema-only 조회로 V24 유지 확인. 시험결과서를 현재 판정 중심으로 정리했고 root 위험수용/운영 발신정보 대기는 동일하다.

## 2026-11-03 회원가입 약관 팝업 후속
약관 전문 링크/직접 체크를 native dialog와 명시적 동의·동의하지 않음으로 전환했다. 끝까지 스크롤해야 동의 가능, 재열기/닫기/Escape는 미동의, 가입 입력/키보드 포커스를 보존한다. 정적 약관을 단일 원본으로 GET /api/v1/auth/terms의 불변 텍스트 트리로 읽고 안전한 DOM API로 표시하며 기존 CSP/Trusted Types를 유지한다. 번역은 열 때만 적재하고 독립 리뷰의 실패 Promise 재사용 문제는 실제 실패→재열기 시험으로 수정·검증했다. 404개(181/147/76) 기능 시험, Nu0, CSS/문서 검사 PASS. 새 JAR dff536cc7cb8532f65b1a1164ddbeea98c558dfc38312edb7f5690f4f3c51b2b와 SBOM/소스를 재검사하며 기존 SMTP root 경고는 예외 처리하지 않는다. 이미지 새 빌드/서비스 반영/SMTP 활성화/DB 변경/병합은 미실행이며 기존 운영 V24를 유지한다. 실수신 검증은 사용자 담당으로 인계했으며 현재 PR28에 후속 변경을 전달한다. 상세 최신 결과는 MP-DOC-038 제7절과 PR 현재 HEAD 리뷰를 따른다.

## 2026-11-03 약관 스크롤 안내·체크박스 연동
사용자 요청으로 아래로 읽기 화살표/스크롤바 스타일과 실제 필수 약관 체크박스를 추가했다. 팝업 동의만 체크 허용, 직접 해제는 철회, 미동의 클릭/Space는 검토를 열며 거절/닫기/재열기는 미동의다. 집중 브라우저7/Nu0/CSS 검사 PASS. 캡처에서 본문 중첩을 확인해 화살표를 별도 행으로 분리했고 최종 전체 UI77·단위181 재검증, 앞 PostgreSQL147 포함405 PASS/FAIL·ERROR·SKIP0을 확인했다. 최종 JAR b99a4f25867099685bac3745ef569d4bcf808d29b5cbd1323f1c6b720a1a7df9와 SBOM 스캔 취약점0이나 전체 보안 게이트는 기존 Postfix root 경고 때문에 실패 상태를 유지한다. 서버/DB/메일/서비스/병합은 변경하지 않는다.

## 2026-11-03 오른쪽 약관 스크롤바 정정
버튼 대신 스크롤바를 원한 사용자 정정을 반영한다. 아래로 읽기 버튼/코드/번역을 제거하고 항상 보이는 오른쪽 트랙·손잡이와 실제 드래그/트랙 클릭/키보드 조작을 연결했다. 본문 끝 동의·체크박스 동기화 유지. 집중 UI7/Nu0/CSS PASS, 최종 단위181·전체UI77(FAIL·ERROR·SKIP0), 최신 JAR 2e5023dc707b4064d8d2302a3aa0c58fca28d888aca1058cd08550d193e87b6b·SBOM 스캔 취약점0을 확인했다. Java/DB 코드 불변으로 이전PG147 결과를 유지한다. 전체 securityCheck는 기존 Postfix root 경고 때문에 실패하며 예외를 추가하지 않았다. 서버/DB/메일/실행 서비스/병합은 그대로다.

## 2026-11-03 약관 모달 디자인 정돈
닫기 버튼 박스·굵은 경계를 줄이고 안쪽 읽기 패널/여백·UI 글꼴·주/보조 버튼·비활성 색을 정돈했다. 상시 스크롤바/체크/동의/CSP 동작은 그대로다. 집중UI7 PASS와 초기/완료 390·768·1440 합성 캡처를 검수했고 최종 단위181/UI77(FAIL·ERROR·SKIP0), Nu0/CSS/docs 및 JAR/SBOM 취약점0을 확인했다. 새 JAR 684f13607aaeae9216f8ac373d35f82fff48ea7968e182208390c6978daef6fb이며 기존 Postfix root 보안 게이트 실패는 유지한다. 메일/서비스/DB/병합은 변경하지 않는다.

## 2026-11-04 Postfix root 실행 예외 미승인 확정
사용자가 해당 SMTP 이미지의 DS-0002 HIGH 예외를 명시적으로 미승인하고 구현 보존·운영 전환 보류를 선택했다. 이전의 답변 대기는 이 결정으로 대체한다. 코드·검사 기준·ignore/예외·SMTP·이미지·운영 DB를 변경하지 않으며 root 경고를 미해결로 유지한다. 실제 메일 수신 검증의 사용자 인계는 위험 수용/운영 승인과 별개다. 기존 web 이미지 sha256:24fc8045a50cc78f44a57d0bdae30b12ed3d8bb52fcde7a1fa5e2042fafbf082와 SMTP 컨테이너 미실행을 읽기 전용으로 확인했다. PR28/구현을 보존하고 develop/main 병합은 수행하지 않는다. 이번 변경은 결정 문서화뿐이며 기존 시험을 재실행했다고 표시하지 않는다.


## 2026-11-04 사용자가 기동한 MailBox 실제 연결 재점검
MailBox SMTP/IMAP/Webmail 3개 컨테이너 실행을 확인했다. SMTP strict TLS NOOP 시험은 exit64/STARTTLS not supported로 실패했으며 인증번호·실제 메일은 전송하지 않았다. TLS 인증서/STARTTLS/SMTP AUTH 미설정, 사설망 전체 mynetworks와 공개25번, root 실행을 확인했다. 실제 SMTP 이미지594e743418aa의 Trivy 검사는 HIGH33/CRITICAL1을 보고하고 게이트 exit1이다. 이는 중복 패키지 검출을 포함한 원시 결과이며 전부 악용 가능한 취약점으로 확정하지 않는다. Debian 공식 자료에서 Postfix CVE-2026-43964의 설치 버전 영향과 zlib CVE-2023-45853의 MiniZip 미빌드 예외를 구분했다. 원래 root 위험 미승인/전체 보안 게이트를 유지한다. 형제 mail-box는 현재 읽기 전용이며 TLS·이미지 수정용 작업 경계 확대를 요청했다.
대기 중 최신 약관/이메일 코드의 runtime 이미지를 새로 빌드했다. 이미지sha256:dcbd1540948a855db099880321384086e9f090d767dd52cb81e6f5c0538f5d86, UID10001, Temurin27.0.1.0.2+1, 실행JAR684f13607aaeae9216f8ac373d35f82fff48ea7968e182208390c6978daef6fb와 호스트 일치를 검증했다. 이 앱 이미지의 취약점/secret gate는 PASS이나 SMTP/전체 게이트를 통과한 것은 아니다. 운영 web/worker24fc8045a50c·V24를 바꾸지 않았으며 외부HTTPS 로그인200/healthy를 확인했다. [재점검 결과](../05-test/email-identity.md#11-사용자-기동-mailbox-실제-연결-재점검)를 따른다.


## 2026-11-04 STARTTLS 적용 후 실제 합성 전달 확인
사용자 MailBox 변경 후 명시적 operator CA로 chain/호스트/유효기간 검증을 통과했다. 실제 SmtpMailTransport로 합성1건을 기존 운영자 Maildir에 발송·수신 확인하고, 검사된 앱 이미지UID10001의 기존 MailBox Docker 네트워크/TLS NOOP도 통과했다. 오래된 시험classpath의 준비 실패는 수정 후 재시험했으며 전송 성공과 구분한다. 외부 인터넷 수신함/실제 회원 인증·복구 운영 전환은 수행하지 않았다.
기존 MailBox에 직접 연결하는 compose-mail-external.yaml을 준비하고 내장 SMTP 미생성/worker 기존 모델 네트워크 보존/web backend 유지/필수호스트 누락 거부/공개CA·별도키 secret을 검증했다. 최신 scanner DB 동기화 후 앱 이미지dcbd1540948a는 PASS, 실제 SMTP594e743418aa는 HIGH33/CRITICAL1 원시 검출로 FAIL이며 전체 securityCheck도 기존 DS-0002 때문에 exit1이다. root 예외 미승인과 작업 폴더 경계를 유지하며 실제서비스24fc8045a50c·V24/.env/키/MailBox 원본을 바꾸지 않는다. [실제 결과](../05-test/email-identity.md#12-starttls-적용-후-실제-전달-재검증).


## 2026-11-04 이 프로젝트 SMTP root 구성 제거 진행
사용자 지시 순서대로 외부 MailBox 조치 프롬프트 MP-DOC-039를 먼저 작성·제시했다. 중복 내장 Postfix11파일과 빌드/Compose/Helm/준비·시험 진입점을 제거하고 외부 SMTP 클라이언트/outbox/인증·복구만 유지한다. 키 준비 CLI와 경계·보존 검증, Java STARTTLS 양성/CA·호스트 거부, 외부 Helm/Compose 검증을 보강했다. Compose 시험의 최초 합성 도메인/IP fixture 오류를 고친 뒤3모드·12누락 거부를 통과했다. focused SMTP7개와 Helm STARTTLS/SMTPS·거부 검증을 확인하고 전체 회귀·보안·새 이미지 검사 중이다. scanner 제외/예외를 추가하지 않으며 타 프로젝트·기존 서비스/DB/.env/키를 변경하지 않는다.


SMTP 책임 분리·미사용 서버 정리 최종 로컬 검증: clean 전체 계약 PASS/408(184·147·77) FAIL·ERROR·SKIP0, 키7/Compose3모드12거부/Helm 두TLS·4거부/Nu0/최종 runtime058f45121034·builder23bd0e0bcc55 gate PASS. 첫 clean 시험 경로 준비 실패는 고쳐 같은 전체 명령으로 재검증했다. source DS-0002 0/새 예외·scanner 약화 없음. 중복 SMTP11tracked+7생성파일/이미지1개, 미사용 Mac런타임116파일·소스압축1개, 과거 프로젝트 이미지59태그 제거(실제 미사용52ID 삭제·실행5ID의 옛 별칭만 해제). 현재6서비스·완료형필수job3/DB V24·기존키·모델보존, 타프로젝트 미변경. Java85파일·브라우저42모듈 추가 참조 점검에서 미사용 후보/누락0. 실제 운영 전환은 외부 MailBox 차단을 유지한다. [증빙](../05-test/evidence/smtp-boundary-cleanup-20261104.json). 현재head 독립 리뷰/PR 전달은 마감 단계이며 develop/main은 병합하지 않는다.

마지막 legacy moapan-* 이미지 재검수에서 실제 구성·문서화된 빌드 태그와 전체 컨테이너의 정확한 이미지ID/태그를 대조했다. 총59태그/미사용52ID 정리 후 기존 모든 컨테이너 ID·이미지·상태가 동일함을 확인했다. 실행5ID의 오래된 별칭만 해제했으며 현재 이름·필수 완료job 이미지·최종 검증 runtime/builder와 빌드 도구는 유지한다. 이 후속은 로컬 이미지 정리 및 결과 문서뿐이며 제품 소스/JAR/검증 이미지 digest는 동일하다.

2026-11-04 웹 품질·도메인 리팩토링 Sprint 착수. 기준11fad44(PR28)·운영앱24fc/V24를 구분하고 사용자 명시 승인에 따라 접근성/성능100목표·죽은코드·기능별 패턴을 감사한다. 최종 제품검증 뒤 SEO감사로 마무리한다. 아직 새 시험/패치/배포/병합 성공으로 표시하지 않는다.

웹 품질 후속 중간 검증: 동적132상태의 axe 실패0, 공개13경계/canonical4개 검사와 격리 지면의 모바일·데스크톱4범주100점(preflight)을 확인했다. 최종 확정은 모든 수정 뒤 다시 측정한다. 실제 원고 언어/이미지 치수, canonical 최초 응답, 지면 병렬 로딩, 일회 패널 활성화, 메시지/수집 Specification/원고 Strategy를 보완했다. 회귀가 광고 늦은 응답 덮어쓰기, 사전 분리의 역변환 충돌과 공백 observer 루프, 원문 필드의 UI 변환을 발견해 실제 수정·집중 검증했다. 코드/도구 기준을 낮추지 않았으며 중단/실패 시험은 PASS에 합산하지 않는다. 원문 Factory 전환과 관련 집중 시험은 통과했으며 전체 clean 회귀·최종 산출물/이미지·마지막 SEO는 마감 단계다. 운영 기존6서비스/V24·MailBox·키·DB·전역 스킬은 변경하지 않는다.

웹 품질 최종 제품 검증 완료: 전체 clean424(190/148/86) PASS·FAIL/ERROR/SKIP0, 마지막 미사용import 제거 후 집중UI9/단위190/산출물 보안 재확인. 공개4문서×2기기 Lighthouse 네 범주100, axe132상태0, Nu13문서0, HTTP13경계/canonical4일치. 코드 seal 고정 뒤 SEO를 마지막 제품 검증으로 실행했다. [MP-DOC-040](../05-test/web-quality.md)과 [증빙](../05-test/evidence/web-quality-20261104.json)에 digest·진단·한계를 기록했다. 현재 정식 PR/remote current-head 검토 마감 중이며 운영 서비스·DB·MailBox·develop/main 병합은 미변경이다.

PR29 신규 변경을 수집/뉴스레터/웹 품질3커밋으로 푸시하고4b6a0ab에서 별도 코드 검토APPROVE/SEO감사PASS와 App formal APPROVED를 확인했다. WQ14항목을 실제 증빙으로 마감한다. 이어지는 커밋은 승인 기록 문서화뿐이며 제품 seal·JAR·이미지·마지막 SEO 결과는 동일하다. 문서 마감 head도 재리뷰·재승인 후 원격 SHA 일치를 확인한다. 서비스/DB/MailBox/develop/main은 변경하지 않는다.

사용자가 서비스 교체·develop 병합과 기존 MailBox의 이메일 인증/복구 연동을 승인했다. 현재 SMTP는 이전과 다른 a2392bb2 이미지이며 새 원시 검사 vuln/secret0, 인증 전용2587/STARTTLS 강제/SASL AUTH 설정과 서비스명 SAN을 확인했다. 타 프로젝트는 읽기 전용으로 유지한다. 새 인도 범위 계약/계획을 갱신하고 백업·격리이행·실제연동·서비스교체를 준비한다. 아직 운영성공/병합완료로 표시하지 않는다.


## 2026-11-04 서비스 교체·실제 이메일 인증/복구 완료

사용자 최신 승인에 따라 검증 runtime d1e49e6d6c10/JAR e03b4ef23d68를 web/worker에 반영했다. V24 암호화 백업/격리 복원→V25→구·신 앱 JPA 기동을 검증했고 운영 migration 전후 기존34테이블 건수/행 SHA256 모두 일치했다. DB/proxy/두 모델 ID·시작시각·이미지, 원래 volume/key와 기존 계정2·구독1·뉴스레터1을 보존했다. 기사496, 합성 계정·challenge·대기 메일0.

독립 MailBox 최신 a2392bb2ab62 취약점/secret0, 필수 STARTTLS/CA/hostname/SMTP AUTH·실수신을 확인했다. 파일 secret을 사용하는 최소 AUTH overlay와 3모드/6누락 거부 시험을 추가했고 check/mailDeploymentCheck/securityCheck PASS. 실제 HTTPS62 assertion에서 인증 가입/인증번호 수신/구독 회수 및404/복구/모든 세션 무효화/새 비밀번호/단회 코드/탈퇴를 확인했다. 외부 서버 수정·내장 SMTP/위험 suppression·CI/CD/main 병합은 없다. 최신 원본은 [서비스 인도 증빙](../05-test/evidence/web-quality-delivery-20261104.json), 절차는 MP-DOC-016 최신 운영 절이다.

마지막 공개 SEO 감사·현재head 독립/App 검토·guarded develop 병합을 마감한다. GitHub 최종 승인·병합 SHA와 branch 정리는 PR29 현재 상태를 정본으로 확인하며 실행 전 성공으로 선기록하지 않는다. 기존 미추적 HTTPS 스킬3파일은 그대로 보존하며 commit에서 제외한다.

최종 실서비스 SEO 독립 재점검(2026-11-04T10:25:29Z): 공개 HTTP13/canonical4/robots/sitemap/404·noindex PASS, 익명 접근성 한·영 ×390/768/1440px의90상태 실패0. 실제 HTTPS 지면 Lighthouse13.6.0을 fresh profile/cache로 각1회 측정해 모바일·데스크톱 성능/접근성/권장사항/SEO 모두100(raw1.0)이다. 모바일 LCP1315.8785ms/TBT68ms/CLS0, 데스크톱 LCP286.029ms/TBT0/CLS0. 점수 선택 재시도·TLS 우회·평가 기준 변경은 없었다. 비점수 진단은 보존했고 실제 포털 순위·CrUX·로그인 화면 전체 WCAG 인증으로 확대하지 않는다. 원본 해시는 서비스 인도 증빙에 기록했다. 이후 제품 코드/이미지를 변경하지 않았다.

## 2026-11-04 남은 PR 순차 병합 — 구독 완료·이메일 통합

사용자가 PR27·28·29 전체 develop 병합을 승인했다. PR27은 head a1a566e50a09d5617541f47ffcccd1027f54f115의 App current-head 승인을 확인하고 guarded human squash로 병합했다. merge SHA ea1c088cb0b195ab203436b9c02d2e08c8b929e0이며 정확한 PR head의 원격·추적·로컬 ref를 삭제/부재 확인했다. 첫 두 병합 요청은 GitHub502로 실패했고 develop 미변경을 확인한 뒤 재시도하여 성공했다.

PR28은 새 develop을 공식 절차로 통합했다. 새 develop의 전체 tree가 이전 의존 head a1a566e와 동일하고 해당 head가 승인본11fad449의 조상임을 확인했다. 중복 squash 충돌21개를 승인본 내용으로 해결한 통합 commit999bd494ad49814e92de6aae35e5a5e527c2ab49의 전체 tracked tree는11fad449와 완전히 동일했다. 분리된 독립 검토에서도 부모·tree 동일성과 기존 시험 증빙 재사용의 타당성을 확인했다. 이후 이 두 관리 문서만 보완하며 제품 코드·설정은 바꾸지 않는다. 새 시험을 실행했다고 보고하지 않는다.

999bd494는 canonical 원격에 push했으나 현재 PR28 API는 이전11fad449를 반환한다. PR29도 Git194ef330과 API87abf5a가 불일치한다. GitHub 공식 장애가 지속되는 동안 새 head가 PR에 반영되고 READ_COMPLETE/독립 승인·App formal 승인을 받아야 후속 병합할 수 있다. 아직 PR28/29 병합 완료라고 보고하지 않는다. 기존에 PR29 후보로 전환한 V25 서비스/실제 MailBox 인증·복구 검증 결과는 유지하며 이번 병합 작업으로 서비스·DB·메일서버·키·모델을 다시 교체하지 않았다.

웹 품질 PR29도 현재 develop(ea1c088)을 통합 준비했고, 관리 기록 추가 전의 전체 tracked tree가 이미 배포·검증한194ef330과 동일함을 확인했다. 최신 관리 기록은 이메일 PR과 일치하도록 보완하며 제품 코드·JAR·실행 이미지는 바꾸지 않는다. PR28 새 canonical head e2317fd와 PR29 후속 통합 head는 PR API 반영 및 현재-head 승인 후에만 병합한다.

PR 참조 후속 진단: 공식 장애는2026-11-04T11:50:06Z에 종료됐다. canonical Git/REST ref와 PR head/base가 달라 최신 원격 두 브랜치와 develop의 실제 merge-tree를 재계산했다. 최신 PR28/29는 모두 충돌0이며, PR API의 이전 snapshot은 각각21/31파일 충돌이었다. 동일 PR의 제한된 close/reopen을 검증된 사람 전용 wrapper로 수행해 PR28 e2317fd/base ea1c088과 PR29 a40d94a/base ea1c088을 정상화했다. branch·보호·승인 조건은 바꾸지 않았다.

PR28 e2317fd는 현재-head READ_COMPLETE·분리된 독립 APPROVE와 App formal APPROVED를 받았다. 실제 병합은 GitHub405의 “마지막 pusher를 확인할 수 없어 추가 승인 필요”로 거절됐고 미병합 상태를 확인했다. 제품 코드 변경 없이 이 관리 기록을 사람 계정으로 새 commit/push하여 푸시 신원 정보 갱신을 시도한다. 새 head의 독립/App 승인은 다시 받으며, 현재까지 미완료인 병합을 완료로 선기록하지 않는다.

## 2026-11-04 병합 참조 정상화·이메일 병합 완료

PR28은 참조 갱신 후에도 마지막 pusher 미확정405로 차단됐다. 제품 변경 없이 사람 계정의 정상 새 commit/push(a09f509) 후 PR head가 자동 갱신됐고 READ_COMPLETE/독립 APPROVE/App current-head APPROVED를 새로 받았다. 보호 조건을 바꾸지 않은 guarded human squash 병합이 성공했다. merge SHA74b982ad20038ff27b36eaca05bde8871c800b08, 실제 source heada09f5097dc3362d175b74e8d2bcf9cbee7b87320, 원격·추적·로컬 head 삭제/부재까지 확인했다. 따라서 PR27·28은 실제 병합 완료다.

PR29는 최신 develop74b982a를 통합했다. target tree가a09f509와 같고 a09f509는 원래 승인 identity11fad449에서 관리 문서2개만 다름을 검증했다. PR29는11fad449의 후속이므로 이미 배포·검증한 제품 파일194ef330/a40d94a를 그대로 보존하고 두 관리 문서의 최신 기록을 합쳤다. 최종 실행코드/정적 자산/의존성/컨테이너/모델/키는 바꾸지 않는다. 기존 시험·이미지/SEO 증빙을 새로 실행한 결과처럼 합산하지 않는다. 새 current-head 독립 검토/App 승인·병합/branch 정리는 PR29의 실제 원격 기록을 정본으로 확인한다.

## 2026-11-06 damgox2 인프라 명칭 이관 착수
사용자가 Docker 이미지·컨테이너·Compose·Helm 잔여 이름의 전면 변경, 데이터 보존, 서비스 반영, 커밋/푸시/develop 병합을 명시 승인했다. 기준 develop ffedf357과 원격 일치, App 선택·사람 ADMIN·기존 protection/현재 계약 guard를 확인했다. 운영6서비스·완료job3·프로젝트볼륨8을 조사했다. 현재 구성/실행 환경 명칭을 변경 중이며 아직 시험·데이터 이관·서비스 교체·병합을 완료로 표시하지 않는다. 기존 미추적 .agents 스킬3파일은 원본 SHA를 보존하고 커밋에서 제외한다.

이관 준비 중간 검증: Compose 모든 overlay와 Helm lint/template·부적절 값 거부, 오프라인 이관 사전조건4시험을 통과했다. 새 앱/DB/프록시/번역/분류 이미지를 빌드했고 암호화 PostgreSQL 백업과37테이블 격리 복원을 확인했다. 전체 Gradle 회귀 및 최신 DB 이미지 스캔을 진행하며 실제서비스/원본 볼륨은 아직 바꾸지 않았다. 프록시 backport 고지 경로 변경에 맞춰6파일 원본/수정 체크섬과5패키지 실제 시험을 재검증했다.

실서비스 이관 완료: 8볼륨 파일/권한 지문·37테이블 행 지문·DB역할 OID/암호/권한 일치, 최종 암호화 백업의 격리 복원37테이블 일치, 새6서비스/공개HTTPS13경계/canonical4개·STARTTLS/AUTH 무발송 연결·두 모델 실제 추론/취소 시험을 확인했다. 원래moapan9컨테이너/8볼륨/4망/9태그를 제거했고 타프로젝트11컨테이너·비밀5파일이 동일하다. 현재 전체 회귀424(190/148/86), FAIL/ERROR/SKIP0과 추가 이관4시험을 통과했다. 최종 이미지/문서 및 독립 PR검토·병합을 마감하며 실행 전 병합 성공으로 표시하지 않는다.

최종 명칭 전수 점검에서 active Docker/Compose/Helm/모델 파일의 대소문자 moapan 잔여0을 확인했다(전용 이관 도구의 과거 source 이름은 필수 입력으로 유지). 프록시의 로컬 Go marker/시험 이름까지 변경하고5패키지·6source hash·최종proxy3이미지 scan을 재검증해 같은 공개proxy ID를 서비스에 반영했다. sourceCheck/check/securityCheck 후속 PASS, 공개HTTPS13경계/canonical4개 재검증. 기존 .env 비밀값 유지 확인 후 작업용 env사본·임시source/SMTP probe/일회 이관 runner를 삭제했다. 최종 산출물은 MP-DOC-041/042 및 비밀값 없는 증빙 JSON으로 등록했다. 이후에는 독립 리뷰·현재-head App 승인·guarded develop 병합/branch 정리 결과를 PR에서 확인한다.

독립 리뷰 e465ff4에서 F1(AI 클래스 해시 변경으로 기존 결과 무효화)·F2(이관 증빙 부모 symlink 경계)를 발견해 병합을 보류했다. worker/web 중지 후272→9로 감소한 번역과 두 job/태그4테이블을 frozen 암호화 백업에서 복구했고 각 전체 행 SHA가 원래와 같다. 신규 기사·권리/주제·운영자 변경 부재를 확인하고 trigger를 살린 단일 DELETE/COPY를 사용했다. 첫 복구 실패는 롤백 후 search_path를 명시해 해결했다. 예산/계정/세션/인증 quota는 되돌리지 않았다. 정책14class bytes·POLICY/PROVIDER를 유지하는 내부 property 매핑으로 수정하고 실제 모델 서버의 legacy wire 호환을 검증했다. 최종 전체 clean428(192/150/86) FAIL/ERROR/SKIP0·이관5·최종8이미지 gate·실제 모델/HTTPS와 worker 재개 후272번역/502태그 지문 보존 PASS. 새 head 독립 재리뷰·App 승인·병합을 진행한다.

## 2026-11-09 Ubuntu25 플랫폼 전환 착수
공통 계약/이미지 부재는 최신 사용자 지시로 프로젝트 자체 fallback 제작으로 해결한다. 실제 desktop-linux/aarch64와 사람/App 원격 권한을 확인했다. 기존 dirty 파일을 workspace-local 사본/지문으로 보존했으며 feature branch codex/platform-ubuntu25-20261109에서 작업한다. 운영 변경·공통 PG 이관은 아직 미실행이다.

현재 Java25 단위197·통합158·브라우저90은 첫 회귀에서 통과했다. 보안 task는 새 Dockerfile이 이전 직접 build 명령의 base 이름을 해석하지 못해 실패했으며, 검증을 끄지 않고 동일 Bake DAG의 proxy-check 대상으로 수정 중이다. 실제 운영은 아직 교체하지 않았다. Ubuntu native AI/proxy 이미지 scan HIGH/CRITICAL0을 확인했다.

## 2026-11-09 Ubuntu25 플랫폼 운영 전환 완료
Temurin25.0.5+8/Ubuntu26.04 앱·proxy·공유AI 이미지로 platform-damgox2-*5개 서비스를 교체했다. 실제 flock 아래 암호화 백업·39개 테이블 복원 대조,14개 정책 class 대조와282개 번역/각544개 작업 보존, 모델 실제 추론, MailBox STARTTLS/hostname/AUTH235, worker672ms 종료·재시작, HTTPS/API를 확인했다. 기존PG는 중지/변경하지 않았고 타 운영8개도 동일ID/시각을 유지했다. 445개 테스트·securityCheck/acceptanceTest·최종3이미지 HIGH/CRITICAL0/secret0 PASS. 상세 MP-DOC-049/050. 공통PG 이관·AMD64·수신함 배달·merge는 미실행이다. 기존 사업/UI/스킬 변경은 보존하고 플랫폼 및 보안·스키마 호환 선행 변경만 커밋 대상으로 분리했다.

## 2026-11-09 Ubuntu25 플랫폼 develop 병합 승인
후속 사용자 지시로 기존 codex/platform-ubuntu25-20261109의 develop 병합이 명시적으로 승인됐다. 사람 PR/assignee → 독립된 현재 head 코드 리뷰 → App formal APPROVED → 사람 wrapper 병합과 정확한 head의 원격/추적/로컬 ref 삭제를 수행한다. 기존 미커밋 사업/UI/로컬 스킬은 보존한다. 이번 추가 범위는 병합 절차이며 실행 서비스·DB를 다시 변경하지 않는다. 실제 PR·검토·병합 결과는 GitHub 상태와 workspace-local 병합 증빙으로 확인한다.

## 2026-11-09 PR31 독립 리뷰 보완·운영 재검증
초기40a7 head에서 독립 리뷰가 unknown 번역/분류 보존, 복원 child 종료, 정책 commit 응답 유실, 쓰기 전 경로 검증5건을 발견해 App CHANGES_REQUESTED로 기록했다. 재현3건 실패 확인 후 수정했고, 단위197/통합163/E2E90=450 및 복구/경로8건, securityCheck/acceptanceTest·이미지 검사·V28 비관리자 격리검증을 통과했다. 동일 flock 아래 암호화39테이블 복원 후 nullable model_policy/캐시 지문V28을 적용하고 웹·worker를 검사한99d428ea 앱으로 교체했다. 기존36테이블과 분류 작업의 이전 필드를 보존했고 DB·proxy·AI·다른10개 운영컨테이너를 중지/재시작하지 않았다. worker717ms 종료/재시작·HTTPS/API 경계를 통과했다.14개class와 POLICY/PROVIDER는 이전25 이미지와 같다. 복구 이미지f0daf152를 원본JAR로 비밀값 없이 보관·검사했다. 현재 head의 독립 재리뷰·develop 병합과 정확한 head branch 삭제 결과는 PR #31과 workspace-local 인계 증빙으로 확인한다. MP-DOC-051 참조.

## 2026-11-09 PR31 F4 부분 작업 원복 판정 보완
5317 재리뷰의 F4를 실제 shared SQL/PostgreSQL 회귀로 재현한 뒤 번역 job의 policy_version도 원복 안전 판정에 포함했다. 다른 미확인 부분 작업이 있으면 정상 결과의 역전환이 끝나도 구 worker를 시작하지 않는다. 단위197/통합164/E2E90=451 및 복구8건의 성공 증빙을 갱신했다. 마지막 변경은 배포 도구·시험이므로 정상 운영 중인99d428ea JAR/이미지는 바뀌지 않았다. 현재-head 독립 재승인과 develop 병합 결과는 PR31을 따른다.

## 2026-11-09 기본 운영 점검의 번역문 포장 표시 수정
지면·기사 검색에서 일부 번역문이 source_text JSON으로 노출되는 문제를 표시 계층에서 수정했다. 원문/번역 저장값과 AI 정책14개 class는 변경하지 않는다. Node 회귀4개와 공개 HTTPS 자산/화면 검사를 통과했다. 기존 운영 JAR의 해당 정적 파일만 바꾼 r2 이미지로 공통 flock 안에서 웹만 교체했다. 다른 컨테이너의 ID/시각·영속 마운트를 보존했다. 미커밋 사업·뉴스레터 작업은 이 커밋과 배포에서 제외했다. docs/05-test/article-translation-wrapper.md 참조.


## 2026-11-11 메모리·성능 Sprint

Sprint Delivery로 별도 feature branch와 계약을 만들고 기존 미커밋79개 파일을 보존했다. 납품 소스는 develop71ecb39와 이번 변경만 내보내 빌드했다. 웹/worker 합산RSS640.4→478.9MiB(-25.2%), p95평균89.28→88.28ms, 처리량231.8→224.3건/초(-3.2%)이며 오류0이다. AI는 모델·native binary·정책·4096 context를 유지하고300초 유휴 해제를 추가했다. 두 모델 각각 두 번의 sleep/wake·출력 일치·health200·인증401·재시작0을 검증했다. RSS와 cgroup/파일 캐시는 구분한다. 기능439개와 Node 추가 시험, fresh DB 보안 게이트·최종 앱/AI 이미지·메일STARTTLS/AUTH·14 TLS 부정 체인 검증을 완료했다. 기존 CA 정책과14개 AI 클래스 지문을 보존했다. 운영 교체·독립 리뷰·develop 병합을 진행하며, 최종 결과는 [검증서](../05-test/memory-performance.md)와 PR 상태로 확인한다.
