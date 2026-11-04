# 프로그램목록

초기 도메인 이관 당시 운영 Java 클래스33개의 이동 이력이다. 현재 전체 코드 수와 신규 구성은 최신 웹 품질 검증서 및 아래 후속 목록을 따른다. 새 ArticleInput/ArticleFingerprint/TopicTaxonomy와 모델 포트를 함께 이식한다.

| 이전 클래스 | 현재 클래스 |
|---|---|
| com.moapan.auth.AuthController | com.moapan.identity.api.AuthController |
| com.moapan.auth.Account | com.moapan.identity.infrastructure.Account |
| com.moapan.auth.Accounts | com.moapan.identity.infrastructure.Accounts |
| com.moapan.security.SecurityConfig | com.moapan.access.infrastructure.SecurityConfig |
| com.moapan.security.DatabaseAvailabilityFilter | com.moapan.access.infrastructure.DatabaseAvailabilityFilter |
| com.moapan.security.Limits | com.moapan.access.infrastructure.Limits |
| com.moapan.site.AdminController | com.moapan.catalog.api.AdminController |
| com.moapan.site.CatalogService | com.moapan.catalog.application.CatalogService |
| com.moapan.site.CatalogRepository | com.moapan.catalog.infrastructure.CatalogRepository |
| com.moapan.source.SafeFetch | com.moapan.collection.infrastructure.SafeFetch |
| com.moapan.source.ArxivGate | com.moapan.collection.infrastructure.ArxivGate |
| com.moapan.source.CollectionFilter | com.moapan.collection.application.CollectionFilter |
| com.moapan.jobs.Worker | com.moapan.collection.application.Worker |
| com.moapan.news.ContentProcessor | com.moapan.article.domain.ContentProcessor |
| com.moapan.news.EditorialService | com.moapan.article.application.EditorialService |
| com.moapan.news.EditorialController | com.moapan.article.api.EditorialController |
| com.moapan.news.NewsService | com.moapan.edition.application.NewsService |
| com.moapan.news.NewsSearch | com.moapan.edition.application.NewsSearch |
| com.moapan.news.RepresentativeInterests | com.moapan.edition.application.RepresentativeInterests |
| com.moapan.news.NewsController | com.moapan.edition.api.NewsController |
| com.moapan.news.TranslationValidator | com.moapan.translation.domain.TranslationValidator |
| com.moapan.news.TranslationGlossary | com.moapan.translation.domain.TranslationGlossary |
| com.moapan.news.TranslationFailure | com.moapan.translation.domain.TranslationFailure |
| com.moapan.news.LocalTranslator | com.moapan.translation.infrastructure.LocalTranslator |
| com.moapan.news.ArticleTranslations | com.moapan.translation.infrastructure.ArticleTranslations |
| com.moapan.jobs.TranslationWorker | com.moapan.translation.application.TranslationWorker |
| com.moapan.news.TranslationStatusController | com.moapan.translation.api.TranslationStatusController |
| com.moapan.classification.TopicCatalog | com.moapan.classification.application.TopicCatalog |
| com.moapan.classification.ClassificationWorker | com.moapan.classification.application.ClassificationWorker |
| com.moapan.classification.LocalClassifier | com.moapan.classification.infrastructure.LocalClassifier |
| com.moapan.classification.ClassificationController | com.moapan.classification.api.ClassificationController |
| com.moapan.ApiError | com.moapan.shared.api.ApiError |
| com.moapan.AdminCommand | com.moapan.bootstrap.AdminCommand |

## 브라우저 프로그램

- `account.js` → `src/main/resources/static/assets/js/identity/account.js`
- `auth.js` → `src/main/resources/static/assets/js/identity/auth.js`
- `preferences.js` → `src/main/resources/static/assets/js/personalization/preferences.js`
- `preferences-transfer.js` → `src/main/resources/static/assets/js/personalization/preferences-transfer.js`
- `preferences-transfer-ui.js` → `src/main/resources/static/assets/js/personalization/preferences-transfer-ui.js`
- `ranking.js` → `src/main/resources/static/assets/js/personalization/ranking.js`
- `settings.js` → `src/main/resources/static/assets/js/personalization/settings.js`
- `news.js` → `src/main/resources/static/assets/js/edition/news.js`
- `slides.js` → `src/main/resources/static/assets/js/edition/slides.js`
- `article-text.js` → `src/main/resources/static/assets/js/article/article-text.js`
- `admin.js` → `src/main/resources/static/assets/js/administration/admin.js`
- `api.js` → `src/main/resources/static/assets/js/shared/api.js`
- `common.js` → `src/main/resources/static/assets/js/shared/common.js`
- `config.js` → `src/main/resources/static/assets/js/shared/config.js`
- `i18n.js` → `src/main/resources/static/assets/js/shared/i18n.js`
- `i18n-en.js` → `src/main/resources/static/assets/js/shared/i18n-en.js`
- `i18n-pages.js` → `src/main/resources/static/assets/js/shared/i18n-pages.js`
- `i18n-features.js` → `src/main/resources/static/assets/js/shared/i18n-features.js` (공통/지면 이외 기능 문구)
- `initial-data.js` → `src/main/resources/static/assets/js/edition/initial-data.js` (제한된 첫 조회의 단일 Promise)
- `card.js` → `src/main/resources/static/assets/js/article/card.js` (기사 DOM Factory)
- `reveal.js` → `src/main/resources/static/assets/js/administration/reveal.js` (실제 패널 공개 시 일회 초기화)
- `request-errors.js` → `src/main/resources/static/assets/js/shared/request-errors.js` (공통 오류 catalog)

뉴스레터사업화기반: PublicationResearch, PublicationEvidence, ContentAccess, EntitlementProvider, EntitlementPolicy, PreviewEntitlements 및 정적research-editor/reader-tools/publication-output 모듈. V14 dossier/최초발간/개정snapshot. 명세는MP-DOC-028 및API/도메인설계서를따른다.

### AI 경계 후속 프로그램
`ai/domain`의 ArticleGuard/AiPolicy는 비신뢰 입력 보류와 실행 정책 식별을 담당한다. `ai/application`의 InferenceControl/InferenceBudget은 취소와 출처 집계 정책이다. `ai/infrastructure`의 ModelTokenBudget/ModelStream/ModelJson은 고정 로컬 모델의 토큰·전송·출력 경계다. 번역 편집 API/정적 모듈은 translation에 유지한다. 새로운 외부 모델·서비스·개인정보 저장소는 추가하지 않았다.


### 공통 Presentation 성능 구성
| 구성 | 책임 |
|---|---|
| presentation/infrastructure/StaticAssetsConfiguration | 정적 classpath assets 전용 재검증·ETag |
| assets/js/shared/dom.js | 페이지 초기화 없는 공통 DOM·상태 표시 |
| assets/js/shared/format.js | 서울 시간의 제한된 Intl 형식 재사용 |
| SharedPresentationTest / WebPerformanceTest | 수명·언어·초안 회귀와 실제 HTTP 전후 측정 |

## 서버 읽기 캐시 기반
- shared/infrastructure/cache/VersionedReadCache: 제한된 Caffeine·60분 TTL·반복 읽기 snapshot·commit 이후 게시·활성 트랜잭션 우회.
- shared/infrastructure/cache/SharedReadCaches: 도메인별 고정 region 예산 배분·중복 할당 방지.
- V17__read_cache_fingerprints.sql: 공개 읽기 모델의 저장 해시 및 작성 트랜잭션 함수.
- VersionedReadCacheTest/ReadCacheIntegrationTest: TTL/용량/동시성/롤백/실제DB재시작. 초기 기반 도입 당시 도메인 API 연결은 후속 작업이었으며, 현재 연결 결과는 바로 아래 도메인 프로그램과 시험을 따른다.

서버 캐시 도메인 프로그램:classification.application.TagDictionary(불변 태그 사전), TopicCatalog(snapshot 캐시), edition.application.EditionReadModels(후보ID/요청 카드/출처), RepresentativeInterests(운영 공통 기본값), advertising.application.AdvertisingSettings(표시 projection), newsletter.PublicationReadModels(발간 원고/목록). 기존 Controller→Service 호출에서 바로 사용한다. BrowserReadCacheHttpTest와 각 도메인 CacheIntegrationTest, cache-service-smoke.mjs가 실제 연결을 검증한다. V17~22가 저장 해시와 trigger를 제공한다.

2026-11-02 미사용 정리:실제뉴스언어는article-text.js만사용하며기사별구언어버튼모듈은제거했다. 서버태그는TagDictionary,기사목록은날짜/정렬조회만유지한다. migrate는Spring context없이직접Flyway실행,admin-cli는try-with-resources안의AdminCommand실행으로단일화했다. deprecated Mac nativelauncher와일회성과거증빙assembler는제거했으며현행Compose/Helm/보안/백업도구와측정기록은보존한다. 상세분류는 [미사용검증서](../05-test/dead-code-audit.md)를따른다.

## 2026-11-04 웹 품질 후속 구성

- collection/domain/CollectionScope: 배치별 불변 Predicate/Specification, 날짜·주제 조건을 한 번 컴파일한다.
- newsletter/domain/Manuscript 및 application/ManuscriptValidation: API/저장 원고 값과 닫힌 블록별 검증 전략. 알려진 작성 언어·검증한 이미지 치수를 보존한다.
- discovery/PublicDocumentMetadata: 공개4문서의 고정 origin canonical을 ResourceTransformer로 동기화한다. 계정/기사를 HTML에 삽입하지 않는다.
- shared/dom: UI용 el과 원문용 originalEl을 분리한다. i18n은 UI 원문 키/속성을 WeakMap에 기억하고 문구 사전을 페이지별로 읽는다. 구 i18n-product/status 모듈은 features로 통합해 제거했다.
- WebQualityRegressionTest 및 WebQualityAudit: 실제 회귀와 별도의132동적상태/공개문서 Lighthouse/13HTTP 접근·색인 감사. 세부 패턴17개와 실제 증빙은 [MP-DOC-040](../05-test/web-quality.md)을 따른다.
