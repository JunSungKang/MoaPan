# 프로그램목록

전체 기존 운영 Java 클래스 33개 조사 결과. 새 ArticleInput/ArticleFingerprint/TopicTaxonomy와 모델 포트를 함께 이식한다.

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
- `article-language.js` → `src/main/resources/static/assets/js/article/article-language.js`
- `article-text.js` → `src/main/resources/static/assets/js/article/article-text.js`
- `admin.js` → `src/main/resources/static/assets/js/administration/admin.js`
- `api.js` → `src/main/resources/static/assets/js/shared/api.js`
- `common.js` → `src/main/resources/static/assets/js/shared/common.js`
- `config.js` → `src/main/resources/static/assets/js/shared/config.js`
- `i18n.js` → `src/main/resources/static/assets/js/shared/i18n.js`
- `i18n-en.js` → `src/main/resources/static/assets/js/shared/i18n-en.js`
- `i18n-pages.js` → `src/main/resources/static/assets/js/shared/i18n-pages.js`
- `i18n-status.js` → `src/main/resources/static/assets/js/shared/i18n-status.js`

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
- VersionedReadCacheTest/ReadCacheIntegrationTest: TTL/용량/동시성/롤백/실제DB재시작. 도메인 API 어댑터는 후속 PR이며 현재 연결하지 않았다.
