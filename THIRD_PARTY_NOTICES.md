# Third-party notices and source availability

MOAPAN 자체 코드의 라이선스를 이 문서로 새로 부여하지 않습니다. 저장소 공개와 오픈소스 이용허락은 별개이며 프로젝트 라이선스는 소유자가 결정합니다.

## 실제 확인 범위
현재 runtimeClasspath 100개 JAR의 POM 선언, 실제 SHA256이 일치하는 JAR의 LICENSE/NOTICE, 고정 모델 2종, llama.cpp/Caddy 및 실행 이미지 기반 구성요소를 확인했습니다. 전체 법률 적합성에 대한 보증이 아닙니다. 상세 좌표·원본 URL·SHA·내장 고지는 [Java 목록](licenses/java-inventory.json), [모델](licenses/models), [원문 출처](licenses/SOURCES.json)에 있습니다. 의존성 JAR와 JDK/OS의 기존 고지는 삭제하지 않습니다.

## 코드 공개·배포 의무 판정
- 확인된 현재 구성에서 단순한 웹서비스 제공만으로 독립된 MOAPAN 전체 소스를 일반 공개해야 한다는 의무는 확인되지 않았습니다. 조사한 애플리케이션 runtime과 두 모델에 AGPL 선언은 발견하지 않았습니다.
- Java Temurin은 GPL-2.0 WITH Classpath-exception-2.0입니다. 독립 애플리케이션에 예외가 적용되지만 JDK 자체의 GPL 의무는 없어지지 않습니다. JDK/OS GPL 구성요소가 포함된 이미지를 외부에 배포한다면 해당 구성요소와 수정사항(신뢰 저장소 조치 등)의 대응 소스 제공 조건을 충족해야 합니다. 이것을 MOAPAN 전체 소스의 일괄 공개 의무와 혼동하지 않습니다.
- Logback의 EPL/LGPL 선택 중 EPL-2.0을 사용하고 AspectJ도 EPL-2.0을 적용합니다. 이미지/JAR 재배포 시 해당 라이브러리의 소스 접근과 고지를 보장해야 합니다. 정확한 버전의 공식 source JAR를 앱 이미지의 /opt/moapan/third-party-sources에 포함합니다. 원본 저장소·해당 버전도 함께 확인해야 하며, 이들 라이브러리를 수정하면 수정한 부분의 소스 제공 조건을 다시 검토해야 합니다.
- Jakarta API의 EPL/EDL/GPL+Classpath 등 복수 선언은 원본 고지를 보존합니다. 별도 애플리케이션의 결합만으로 자체 코드에 같은 라이선스를 자동 지정하지 않습니다.
- Qwen3.5-4B와 Index-Translate-2B의 현재 고정 GGUF는 모델 카드에서 Apache-2.0으로 표시됩니다. 원본 모델/변환 출처와 라이선스를 이미지 및 모델 볼륨에 보존합니다. 상표·학습 데이터·모델 출력의 권리까지 보장하는 의미가 아닙니다.
- 수정 Trivy는 Apache-2.0 공식 소스를 기반으로 보안 의존성을 갱신했습니다. 원본 commit·패치 recipe·도구 변경 사실을 보존합니다. Caddy/llama.cpp/Go 모듈의 원본 고지도 이미지에 보존합니다.

## 배포 전 준수 조건
현재 동작하는 SaaS와 외부 이미지/실행파일 배포를 구분합니다. 외부 배포 전에는 최종 digest별로 GPL/약한 copyleft의 대응 소스·수정분·고지를 수령자가 실제 얻을 수 있는지 확인해야 합니다. 공급자 URL만 적는 것으로 항상 의무를 충족한다고 주장하지 않습니다. 필요한 GPL 대응 소스나 적법한 제공 방식이 확보되지 않으면 이미지 공개 배포를 보류하세요. 임의의 3년 제공 약속이나 운영자 연락처를 만들지 않았습니다.

## 확인된 메타데이터 불일치
Spring Session4.1.1의 두 POM은 Broadcom Foundation License라고 표시하지만, 실제 JAR의 LICENSE와 공식4.1.1 소스 태그는 Apache-2.0입니다. 원래 POM 선언을 덮어쓰지 않고 정확한 버전의 JAR SHA/고지와 [공식 태그](https://raw.githubusercontent.com/spring-projects/spring-session/4.1.1/LICENSE.txt)를 대조한 내용을 목록에 기록했습니다. 향후 버전에 자동 적용하지 않습니다.

## 주요 원문
[Classpath 예외](https://spdx.org/licenses/Classpath-exception-2.0.html), [EPL FAQ](https://www.eclipse.org/legal/epl-2.0/faq/), [Logback 라이선스](https://logback.qos.ch/license.html), [Apache2.0](https://www.apache.org/licenses/LICENSE-2.0), [현재 Hibernate7 Apache 전환](https://docs.hibernate.org/orm/7.0/migration-guide/).

## Java runtime 선언 목록
|구성요소|버전|선언 및 검토|
|---|---|---|
|ch.qos.logback:logback-classic|1.5.38|EPL-2.0 / LGPL-2.1-only|
|ch.qos.logback:logback-core|1.5.38|EPL-2.0 / LGPL-2.1-only|
|com.fasterxml.jackson.core:jackson-annotations|2.21|The Apache Software License, Version 2.0|
|com.fasterxml:classmate|1.7.3|Apache License, Version 2.0|
|com.rometools:rome-utils|2.1.0|The Apache Software License, Version 2.0|
|com.rometools:rome|2.1.0|The Apache Software License, Version 2.0|
|com.sun.istack:istack-commons-runtime|4.1.2|Eclipse Distribution License - v 1.0|
|com.zaxxer:HikariCP|7.0.2|The Apache Software License, Version 2.0|
|commons-logging:commons-logging|1.4.0|Apache-2.0|
|io.micrometer:micrometer-commons|1.17.2|The Apache Software License, Version 2.0|
|io.micrometer:micrometer-observation|1.17.2|The Apache Software License, Version 2.0|
|jakarta.activation:jakarta.activation-api|2.1.4|EDL 1.0|
|jakarta.annotation:jakarta.annotation-api|3.0.0|EPL 2.0 / GPL2 w/ CPE|
|jakarta.inject:jakarta.inject-api|2.0.1|The Apache Software License, Version 2.0|
|jakarta.persistence:jakarta.persistence-api|3.2.0|Eclipse Public License v. 2.0 / Eclipse Distribution License v. 1.0|
|jakarta.transaction:jakarta.transaction-api|2.0.1|EPL 2.0 / GPL2 w/ CPE|
|jakarta.validation:jakarta.validation-api|3.1.1|Apache License 2.0|
|jakarta.xml.bind:jakarta.xml.bind-api|4.0.5|Eclipse Distribution License - v 1.0|
|net.bytebuddy:byte-buddy|1.18.15|Apache License, Version 2.0|
|org.antlr:antlr4-runtime|4.13.2|BSD-3-Clause|
|org.apache.httpcomponents.client5:httpclient5|5.6.5|Apache License, Version 2.0|
|org.apache.httpcomponents.core5:httpcore5-h2|5.4.4|Apache License, Version 2.0|
|org.apache.httpcomponents.core5:httpcore5|5.4.4|Apache License, Version 2.0|
|org.apache.logging.log4j:log4j-api|2.25.5|Apache-2.0|
|org.apache.logging.log4j:log4j-to-slf4j|2.25.5|Apache-2.0|
|org.apache.tomcat.embed:tomcat-embed-core|11.0.27|Apache License, Version 2.0|
|org.apache.tomcat.embed:tomcat-embed-el|11.0.27|Apache License, Version 2.0|
|org.apache.tomcat.embed:tomcat-embed-websocket|11.0.27|Apache License, Version 2.0|
|org.aspectj:aspectjweaver|1.9.25.1|Eclipse Public License - v 2.0|
|org.bouncycastle:bcprov-jdk18on|1.86|Bouncy Castle Licence|
|org.checkerframework:checker-qual|3.55.1|The MIT License|
|org.eclipse.angus:angus-activation|2.0.3|EDL 1.0|
|org.flywaydb:flyway-core|12.4.0|Apache License, Version 2.0|
|org.flywaydb:flyway-database-postgresql|12.4.0|Apache License, Version 2.0|
|org.glassfish.jaxb:jaxb-core|4.0.9|Eclipse Distribution License - v 1.0|
|org.glassfish.jaxb:jaxb-runtime|4.0.9|Eclipse Distribution License - v 1.0|
|org.glassfish.jaxb:txw2|4.0.9|Eclipse Distribution License - v 1.0|
|org.hibernate.models:hibernate-models|1.1.2|Apache License Version 2.0|
|org.hibernate.orm:hibernate-core|7.4.13.Final|Apache License, Version 2.0|
|org.hibernate.validator:hibernate-validator|9.1.5.Final|Apache License 2.0|
|org.jboss.logging:jboss-logging|3.6.3.Final|Apache License 2.0|
|org.jdom:jdom2|2.0.6.1|Similar to Apache License but with the acknowledgment clause removed|
|org.jsoup:jsoup|1.23.2|The MIT License|
|org.jspecify:jspecify|1.0.1|The Apache License, Version 2.0|
|org.postgresql:postgresql|42.7.14|BSD-2-Clause|
|org.slf4j:jul-to-slf4j|2.0.20|MIT|
|org.slf4j:slf4j-api|2.0.20|MIT|
|org.springframework.boot:spring-boot-autoconfigure|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-data-commons|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-data-jpa|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-hibernate|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-http-converter|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-jackson|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-jdbc|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-jpa|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-persistence|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-security|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-servlet|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-session-jdbc|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-session|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-sql|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-data-jpa|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-jackson|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-jdbc|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-logging|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-security|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-session-jdbc|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-tomcat-runtime|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-tomcat|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-validation|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter-webmvc|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-starter|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-tomcat|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-transaction|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-validation|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-web-server|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot-webmvc|4.1.2|Apache License, Version 2.0|
|org.springframework.boot:spring-boot|4.1.2|Apache License, Version 2.0|
|org.springframework.data:spring-data-commons|4.1.2|Apache License, Version 2.0|
|org.springframework.data:spring-data-jpa|4.1.2|Apache License, Version 2.0|
|org.springframework.security:spring-security-config|7.1.2|Apache License, Version 2.0|
|org.springframework.security:spring-security-core|7.1.2|Apache License, Version 2.0|
|org.springframework.security:spring-security-crypto|7.1.2|Apache License, Version 2.0|
|org.springframework.security:spring-security-web|7.1.2|Apache License, Version 2.0|
|org.springframework.session:spring-session-core|4.1.1|Apache-2.0 (POM 불일치 별도 기록)|
|org.springframework.session:spring-session-jdbc|4.1.1|Apache-2.0 (POM 불일치 별도 기록)|
|org.springframework:spring-aop|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-aspects|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-beans|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-context|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-core|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-expression|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-jdbc|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-orm|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-tx|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-web|7.0.10|Apache License, Version 2.0|
|org.springframework:spring-webmvc|7.0.10|Apache License, Version 2.0|
|org.yaml:snakeyaml|2.6|Apache License, Version 2.0|
|tools.jackson.core:jackson-core|3.1.7|The Apache Software License, Version 2.0|
|tools.jackson.core:jackson-databind|3.1.7|The Apache Software License, Version 2.0|
