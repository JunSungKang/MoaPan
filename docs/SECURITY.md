# 보안 운영

기본 명령: ./gradlew --no-daemon clean check integrationTest e2eTest bootJar sbom securityCheck acceptanceTest. 호스트에서는 scripts/gradle-local.sh로 workspace JDK·캐시·임시 경로를 고정한다. CI/CD는 구성하지 않는다.

비회원 뉴스401·일반회원 admin403·CSRF403·권한 service 검사·no-store·CSP·no-referrer·안전 DOM·TLS 검증을 유지한다. 계정의 허용 응답 복사와 다중계정 분산 수집을 완전히 막는다고 주장하지 않는다.

서명 cursor는 계정·edition·위치·만료·size를 검증한다. quota는 DB 원자 업데이트로 분60·일6000·동시3 제한. 카운터 확인 실패는 허용하지 않는다. 로그인 식별자는 keyed HMAC만, global limiter로 임의 식별자 폭증을 제한한다.

공식 JDK/Gradle/Spring/파서/DB/Caddy 공지와 CISA KEV를 다시 조회한다. Trivy 실제 JAR/SBOM·최종 이미지·소스/설정/secret 검사와 DB 최신성을 기록한다. scanner 오류·고위험 UNKNOWN·미해결 영향 HIGH/CRITICAL·알려진 악용은 외부 배포 차단이다. 다운로드 파일·lock checksum 자동생성만으로 신뢰 검증을 완료했다고 표시하지 않는다.
