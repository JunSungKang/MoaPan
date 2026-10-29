# 버전·공급망 확인

착수·최종 확인일: 2026-10-28 UTC/Asia-Seoul. 최종 실행 시각·산출물 SHA256·이미지 digest는 [검증 증빙](evidence/verification.json)을 참조한다. 공식 release API의 release_name과 실제 JVM 전체 문자열을 함께 확인했다. API의 단순 semver 필드는 추가 공급자 patch를 생략하므로 그것만으로 버전을 확정하지 않았다.

|제품|선정 버전·공급자|정식 릴리스·호환 근거|
|---|---|---|
|JDK|Eclipse Adoptium Temurin **27.0.1.0.2+1**, HotSpot|최신 GA 메이저27, 2026-10-24 공급자 패치. [공식 API](https://api.adoptium.net/v3/assets/latest/27/hotspot), [긴급 수정 공지 10월27일](https://adoptium.net/news/2026/10/temurin-october-2026-emudhra/)|
|Gradle|9.8.2|[정식 current API](https://services.gradle.org/versions/current), [Java27 daemon/toolchain 지원·보안 수정](https://docs.gradle.org/9.8.2/release-notes.html), [호환 표](https://docs.gradle.org/current/userguide/compatibility.html)|
|Spring Boot|4.1.2, 2026-10-22|[공식 requirements](https://docs.spring.io/spring-boot/system-requirements.html): Java27 포함·Gradle9 지원, Framework7.0.10 이상|
|Spring Security / Framework / Tomcat|7.1.2 / 7.0.10 / 11.0.27|Boot4.1.2 BOM과 gradle.lockfile, 실제 JAR/SBOM 검사|
|PostgreSQL|18.6, 2026-08-13|[정식 release notes](https://www.postgresql.org/docs/release/18.6/), [지원 표](https://www.postgresql.org/support/versioning/). 19 RC는 선정하지 않음. 실제 SQL server_version=18.6|
|Caddy|2.11.7, 2026-10-03|[공식 정식 릴리스](https://github.com/caddyserver/caddy/releases/tag/v2.11.7). 공식 태그 소스를 보안 수정 Go1.27.3/x-net0.60.0/chi5.4.0으로 표준 모듈 재빌드|
|Go 인프라 빌더|1.27.3|[공식 다운로드 SHA256](https://go.dev/dl/?mode=json), [보안 릴리스](https://go.dev/doc/devel/release). Java 서비스 스택 변경 없이 Caddy/gosu/age 재빌드에만 사용|
|gosu|1.19 태그의6456aaa0f3c8 commit|Go module 고정 pseudo-version, moby/sys/user0.4.1 보안 수정. go.mod/go.sum·모듈 checksum DB 검증|
|Trivy|0.76.1, 2026-10-21|공식 aquasecurity/trivy 정식 배포 archive SHA256 검증, 내장 Go1.27.3 및 도구 바이너리 자체 scan|
|age|공식 소스1.3.2 → v1.3.2+moapan-security.1|공식 배포 원본의 오래된 Go/x-crypto 발견 후 Go1.27.3/x-crypto0.57.0으로 재빌드. scripts/backup-tools go.mod/go.sum·복원 왕복 검증|

JDK27은 LTS와 구분한다. GPLv2 + Classpath Exception, 상업적 사용을 위한 별도 Oracle 구독을 선택하지 않았다. 공급자 [지원 정책](https://adoptium.net/support/)의 JDK27 availability 종료는2027년3월이며, 이 비LTS 릴리스에 무기한 지원을 약속하지 않는다. JDK license/notice를 전체 배포본과 함께 이미지에 보존한다. Gradle·Spring·Caddy·Trivy는 각 배포 라이선스/notice를 유지한다.

## 다운로드 검증

|산출물|실제 일치한 SHA256|
|---|---|
|Temurin macOS aarch64 JDK tar|c2619cb73ff1d296edd0644d92ae8ed94039bc1bdd43282398835b7f88134fb6|
|Temurin Linux x64 JDK tar|30cb129f6cf299c852cec0feca9ea2a596d2ee9f26d8605d3a253951f6c8b20f|
|Gradle9.8.2 ZIP|21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607|
|공식 Wrapper JAR|3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98|
|Trivy darwin arm64 archive|11a9b6cd2752011b54f01ea4bd0dbfa9f0c41dc6538d5a3f9d23955f471bf4e5|
|Trivy 실제 executable|5c03401caf9d3cc04d9e0c7f3abd102e97518e8237465cce702162b605535e8d|
|Go1.27.3 Linux amd64 tar|65efbc80fdc1fa2cd2631cad5b481777a68e8725f34e89f50fc6ac714e379b40|
|Go1.27.3 Linux arm64 tar|a06e99309dbcbdca1a8ccd2282c3ee146e55930886e26b4f1193269587165f49|

기본 Ubuntu24.04 index digest는24737cef17f4e22b1977df7d9a05e77c4e28b7da5b8911f8d8ccda136bf3573c, PostgreSQL18.6-Alpine index digest는10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5다. Dockerfile에서 고정한다. package 보안 업데이트 결과는 실행 이미지별 scanner 패키지 목록과 함께 기록한다. 향후 apt/apk 보안 저장소 갱신으로 재빌드 digest가 달라지면 다시 검사해야 한다. byte 동일 이미지를 무조건 보장하지 않는다.

## 실제 사용 버전

호스트 java/javac·Gradle daemon/toolchain·단위/통합/브라우저 테스트는 workspace Temurin27.0.1.0.2+1을 사용한다. 시스템 기본25.0.3은 사용하지 않는다. Docker runtime 내부 동일 문자열과 truststore를 별도로 검사했고, 호스트 bootJar와 컨테이너 내부 JAR SHA256이 같은지 확인한다. Linux arm64의 선택 패치 미제공으로 app는 명시적 linux/amd64다. 최신이라는 tag만 사용하거나 JDK를 몰래 낮추지 않았다.

Wrapper는 공식 ZIP에서 생성한 JAR을 별도 공식 SHA256과 대조했다. 의존성은 Maven Central/Gradle Plugin Portal, 고정 Boot BOM·lock·strict verification을 사용한다. verification metadata 자동 생성 자체가 모든 공급자의 서명 확인을 뜻하지 않으며, 공식 배포 경로·release/advisory·실제 산출물 검토를 추가했다. build scan 업로드와 CI/CD는 구성하지 않았다.

Docker Desktop: 공식 updater로4.97.0(244584), 2026-10-26 릴리스, 2026-10-28T04:17Z CLI/UI 확인. 근거 https://docs.docker.com/desktop/release-notes/#4970 . Engine29.9.2 정상 기동 확인. VirtioFS 유지 및 권한 서비스 응답 복구는 ENV-001 참조.

## 2026-10-29 보안 재검사에 따른 프록시 의존성 갱신
Caddy2.11.7/Go1.27.3 및 Java27.0.1.0.2+1 유지. go-jose/v4 4.1.6, x/net0.62.0으로 보안 수정하고 필요한 전이 x/crypto0.58.0,x/sync0.24.0,x/sys0.49.0,x/term0.47.0,x/text0.43.0을 go.mod/go.sum에 고정. 공급자 근거: https://github.com/go-jose/go-jose/releases/tag/v4.1.6 , https://pkg.go.dev/vuln/GO-2026-6860 , https://pkg.go.dev/vuln/GO-2026-6866 . v3에는 패치가 없어 현재 설정 비노출 판정과 만료를 SECURITY_REVIEW.md에 기록. 최종 runtime/build/proxy digest와 보고서 SHA는 evidence/topic-security.json 및 topic-collection.json 참조.


### Linux CPU 번역 runtime 추가
llama.cpp b11960 공식 commit 8642541b255bfb5dc39cd351a178be09013a55d6, source archive SHA256 1688a167a8edd9499cdd0c19b8bd044f0fa184b466981977bbf2222d884953a4. 공식 server 문서 https://github.com/ggml-org/llama.cpp/blob/8642541b255bfb5dc39cd351a178be09013a55d6/tools/server/README.md 확인. 모델 revision 3a0eca6ce931d88d00614d427ab3ed8ff0743902/Q8_0, SHA256 a09d287f41d297206947a2e51acc29dd03c8b22356111fdb1e289c0c97c173f3. aarch64 Temurin27.0.1.0.2+1 공식 archive SHA256 5ddacf925244a00b60cac828b61ff2f67a68cd32b8dfe5cee1405e333b4589e5 추가 검증. 실제 Linux ARM64 실행, AMD64 build 경로 미실행.
