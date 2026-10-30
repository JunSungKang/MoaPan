# 도메인 상세설계서

## 개발 단위
identity(계정), access(인증/제한), catalog(사이트/출처 관리), collection(수집), article(콘텐츠 정책/편집), edition(지면/검색), translation(번역), classification(주제 분류), bootstrap(실행 조립), shared(공통 API 오류)로 구분한다. api는 HTTP 진입, application은 유스케이스·트랜잭션, infrastructure는 DB·HTTP 모델·JPA를 담당한다. domain은 업무 정책과 값 계약이다.

## 이식 계약
| 단위 | 이식 대상 | 필요한 외부 계약 |
|---|---|---|
| 요약/텍스트 매칭 | article.domain | Java 표준 API. ArticleInput 구현체로 일시 입력 제공 |
| 번역 검증 | translation.domain + article.domain | Java 및 ArticleFingerprint의 Jackson JSON 의존성 |
| 분류 주제 값 | classification.domain | Java UUID/List |
| 모델 교체 | TranslationModel / ClassificationModel | application 포트 구현체를 Spring 조립에 제공 |
| 운영 유스케이스 | 각 application + infrastructure | Spring TX/JDBC/Security, PostgreSQL 마이그레이션, role/env/secret |

모든 클래스를 파일 하나만 복사해 무설정 실행할 수 있다고 보장하지 않는다. 이식 단위는 도메인 정책 또는 명시한 어댑터를 포함한 모듈이다. DB 작업은 기존 잠금 순서/lease/fencing/권리 확인을 유지하며 운영 스키마와 함께 이식한다.

## 기존 결합 제거
분류의 번역 저장 클래스 해시 호출을 article.domain.ArticleFingerprint로 이동했다. JSON 직렬화와 SHA256 바이트 계약을 그대로 유지한다. 수집 입력은 ArticleInput 포트로 받고 요약 정책은 HTTP 파서에 의존하지 않는다. 모델 호출은 application 포트를 통해 주입한다.

## 제약과 후속 확장
공유 PostgreSQL 스키마를 사용하는 모듈형 모놀리스다. 수집 유스케이스는 catalog 운영 저장소/상태 규약을 사용한다. 다른 도메인은 JdbcClient를 직접 주입받고 번역 저장은 지면 DTO 대신 자체 Original 입력을 받는다. SQL 유스케이스를 독립 저장소 포트로 추가 분리하는 것은 후속 대상이며 독립 배포 가능한 마이크로서비스로 표현하지 않는다. 클래스 이동만으로 데이터 소유권이 분리되었다고 하지 않는다. 비동기 처리와 보안 규칙을 한 번에 다시 쓰지 않고 회귀 증거를 유지한다.

## 뉴스레터 상품·검증 경계
newsletter/domain/PublicationResearch는프레임워크없는주간/기준시각·근거·전망·검토·정정판정정책이다. application/PublicationEvidence는PostgreSQL메타데이터조회·source/article잠금어댑터다. NewsletterService는원고/근거snapshot·version·발간권한을조합한다. 정책검사는기계적으로범위와필수값을검사하며의미정확성/법적권리보증은아니다.
subscription/domain/EntitlementPolicy는혜택과시작/만료만판정한다. application/EntitlementProvider port와ContentAccess가현재인증UUID의혜택을확인한다. infrastructure/PreviewEntitlements는현재모든회원에게무료혜택을제공하며미지원paid설정으로시작하지않는다. 운영ADMIN역할이상품혜택을우회하는분기를두지않았다. 결제/조직DB와의연동은추후provider구현으로교체한다.
정적newsletter모듈은research-editor(편집근거),reader-tools(회원출력),publication-output(DOM안전한HTML·출처·업무텍스트),evaluation-policy(순수익명집계/준비도),evaluation(ADMIN화면)로분리했다. UI집계는메모리만사용하고다운로드는명시적사용자조작으로만실행한다.
