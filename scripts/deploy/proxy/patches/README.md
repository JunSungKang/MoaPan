# go-jose v3 보안 백포트

공식 v3.0.5 모듈의 Go checksum 검증 후 원본 6개 파일의 `original.sha256`을 확인하고, fuzz 없이 패치를 적용하며 `modified.sha256`으로 결과를 확인한다. 공급자 정식 v3 릴리스가 아닌 MOAPAN의 보안 백포트다. 원래 Apache-2.0 저작권·라이선스와 변경 표시는 유지한다. 최종 프록시에 수정 소스·패치·체크섬·고지가 포함된다.

공식 근거:

- [CVE-2026-116587](https://github.com/go-jose/go-jose/security/advisories/GHSA-mgcc-cww5-fp2q): RSA 크기·소수곱 검증, private JWK/EPK 헤더 거부와 헤더 병합 수정. [v4.1.6 수정](https://github.com/go-jose/go-jose/commit/a0111f82742fa92bceec9ede40845e73319e6230).
- [CVE-2026-116585](https://github.com/go-jose/go-jose/security/advisories/GHSA-4w3g-jfj2-cw2q): CBC-HMAC 키 길이·빈 패딩 입력 검증. [공식 수정 PR](https://github.com/go-jose/go-jose/pull/292).
- [CVE-2026-116586](https://github.com/go-jose/go-jose/security/advisories/GHSA-vvm8-gf78-28g4): JSON 중첩 10,000 제한. 공식 v4.1.6 scanner의 동일 제한 이식.
- [CEK 길이 수정](https://github.com/go-jose/go-jose/commit/25b55feb059b8b08e16a73c601c8b80571fa5208): 복호화 전에 정확한 키 길이 확인.

v3 공개 API를 유지한다. 빌드는 공식 v3 전체 테스트와 이 디렉터리의 공격/정상 회귀를 실행한다. testify v1.12.1은 빌드 테스트 의존성이며 실행 프록시에 링크되지 않는다. 새로운 v3 공급자 릴리스 또는 Caddy 의존성 전환이 나오면 이 백포트를 대체하고 전체 재검증한다. 수정된 로컬 모듈을 scanner가 미인식할 수 있으므로 탐지 0개를 수정 증거로 삼지 않고 소스 체크섬·회귀 결과·최종 바이너리 정보를 별도로 보관한다.

프록시 진입점은 백포트의 고유 상수를 참조하므로 이 패치 없이 upstream v3만 사용하는 실수로 `go build`하면 컴파일이 실패한다. 상수 자체를 패치 검증으로 취급하지 않으며 여섯 수정 소스의 체크섬과 전체 회귀도 반드시 통과해야 한다.
