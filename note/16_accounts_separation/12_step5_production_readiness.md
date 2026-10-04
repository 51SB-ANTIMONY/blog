---
title: accounts 분리하기 - 5단계 운영 준비 (구현, 확인, 트러블슈팅)
date: 2026-10-04
tags: [production, configuration, spring-profile, environment-variable]
---

# accounts 분리하기 - 5단계 운영 준비 (구현, 확인, 트러블슈팅)

> 목적과 계획은 [5단계 계획](./11_step5_production_readiness_plan.md)에, 전체 단계는 [로드맵](./01_roadmap.md)에 있다.
> 세부 단계를 끝낼 때마다 이 노트에 이어서 적는다.

## 1. 커밋
| 단계 | 저장소 | 커밋 | 내용 |
|---|---|---|---|
| 5-1 | accounts | `d15cc70` | 설정을 공통·로컬·운영·테스트 프로필로 나눔 |
| 5-1 | blog | `7c22a65` | 같은 내용 |
| 5-2 | accounts | `c619f6e` | Flyway로 테이블 관리, MySQL 드라이버 |
| 5-2 | blog | `95b6520` | 같은 내용, 예시 글은 로컬 전용 스크립트로 |
| 5-3 | accounts | `0905de3` | 서명 키 파일, 앱 등록·발급 정보를 DB에 |
| 5-4 | accounts | `ea1819e` | 세션을 DB에 (Spring Session JDBC) |
| 5-4 | blog | `7cd3d12` | 같은 내용 |
| 5-6 | accounts | `b1a62af` | `Secure`·`SameSite` 쿠키, 서명 키를 환경 변수(Base64)로도 받기 |
| 5-6 | blog | `9c57714` | `Secure`·`SameSite` 쿠키 |
| 배포 준비 | accounts, blog | `68e4a0b`, `0281d5d` | 헬스 체크 주소 `/actuator/health` |

## 2. 5-1: 설정 정리

### 나눈 결과
| 파일 | 내용 | Git |
|---|---|---|
| `src/main/resources/application.yml` | 공통 설정. 주소는 환경 변수, 없으면 로컬 기본값. 비밀 값은 기본값 없음 | 올린다 |
| `src/main/resources/application-prod.yml` | 운영 설정. DB를 환경 변수로만 받고, H2 콘솔·SQL 출력은 끈다. 필요한 환경 변수 목록을 파일 맨 위에 적었다 | 올린다 |
| `src/test/resources/application-test.yml` | 테스트용 가짜 비밀 값 | 올린다 |
| `backend/application-local.yml` | 로컬 비밀 값, H2 콘솔, SQL 출력 | 제외 (`application-local.yml.example`은 올린다) |

```yaml
# application.yml (accounts)
spring:
  profiles:
    default: local                      # 프로필을 지정하지 않으면 local
  datasource:
    url: ${DB_URL:jdbc:h2:mem:accounts}   # 환경 변수가 없으면 로컬 H2
accounts:
  issuer: ${ACCOUNTS_ISSUER:http://localhost:3001}
  clients:
    blog:
      client-secret: ${BLOG_CLIENT_SECRET}   # 기본값 없음
```

> **`${이름:기본값}`이란?**
> Spring 설정 파일에서 다른 값을 끌어다 쓰는 문법이다. `${ACCOUNTS_ISSUER:http://localhost:3001}`은 `ACCOUNTS_ISSUER` 환경 변수가 있으면 그 값을, 없으면 `:` 뒤의 기본값을 쓴다. 기본값을 쓰지 않은 `${BLOG_CLIENT_SECRET}`은 값이 없으면 서버가 켜지지 않는다. 비밀 값을 빠뜨린 채 운영 서버가 뜨는 일을 막으려고 일부러 기본값을 두지 않았다.

### 프로필이 켜지는 방식
| 실행 | 켜지는 프로필 | 읽는 파일 |
|---|---|---|
| IntelliJ, `./gradlew bootRun` (프로필 지정 없음) | `local` (`spring.profiles.default`) | `application.yml` + `backend/application-local.yml` |
| 운영 서버 (`SPRING_PROFILES_ACTIVE=prod`) | `prod` | `application.yml` + `application-prod.yml` + 환경 변수 |
| `./gradlew test` | `test` (`build.gradle`이 지정) | `application.yml` + `application-test.yml` |

```groovy
// build.gradle
tasks.named('test') {
    useJUnitPlatform()
    systemProperty 'spring.profiles.active', 'test'
}
```

> **`spring.profiles.default`와 `spring.profiles.active`의 차이**
> `active`는 "이 프로필을 켜라"이고, `default`는 "아무 프로필도 켜지 않았을 때 대신 켤 프로필"이다. 운영에서 `prod`를 켜거나 테스트에서 `test`를 켜면 `local`은 켜지지 않는다.

### 로컬 비밀 값 파일을 `backend/` 폴더에 둔 이유
처음에는 `src/main/resources/application-local.yml`에 두었다. 그런데 `src/main/resources`의 파일은 모두 실행 파일(JAR)에 포장된다. Git에서 제외해도, 로컬에서 만든 JAR 안에는 비밀 값이 들어간다(트러블슈팅 2).

Spring Boot는 JAR 안의 설정 말고도 **실행 위치의 `./`, `./config/` 폴더**에 있는 설정 파일을 자동으로 읽는다. 그래서 `backend/application-local.yml`로 옮겼다. `bootRun`과 IntelliJ는 `backend/`에서 실행하므로 그대로 읽히고, JAR에는 들어가지 않는다.

**주의:** IntelliJ 실행 구성의 Working directory가 `backend` 폴더여야 한다. 다른 폴더에서 실행하면 이 파일을 찾지 못해 비밀 값이 없다는 오류로 멈춘다.

## 3. 5-1 확인
두 저장소에서 똑같이 확인했다.

| 확인 | 방법 | 결과 |
|---|---|---|
| 로컬 비밀 값 없이 테스트 | `backend/application-local.yml`을 잠시 치우고 `./gradlew test` | accounts 37개, blog 24개 통과. 다른 컴퓨터와 CI에서도 테스트가 돈다 |
| JAR에 비밀 값이 없는지 | `unzip -l`로 JAR 안의 `application*` 파일 확인 | `application.yml`, `application-prod.yml`만 있음 |
| 로컬 실행 | `backend/`에서 프로필 없이 `java -jar` | 정상 실행. accounts 설정 문서 200, blog 글 목록 200 |
| 운영 실행, 환경 변수 없음 | `SPRING_PROFILES_ACTIVE=prod java -jar` | `prod`가 켜지고 **서버가 켜지지 않음** (종료 코드 1) |

## 4. 트러블슈팅 (5-1)

### 1. H2 콘솔을 끄자 서버가 켜지지 않았다
- **문제**: 테스트 설정에서 H2 콘솔을 끄자(`spring.h2.console.enabled: false`) accounts 테스트 37개 중 26개가 `No qualifying bean of type 'H2ConsoleProperties'`로 실패했다.
- **원인**: 보안 설정이 "H2 콘솔 주소는 보안 검사에서 뺀다"(`web.ignoring().requestMatchers(toH2Console())`)를 항상 등록하고 있었다. `toH2Console()`은 H2 콘솔 설정 객체(`H2ConsoleProperties`)가 있어야 동작하는데, 이 객체는 콘솔이 켜져 있을 때만 만들어진다. 지금까지는 로컬 설정에서 콘솔을 켜 두어서 드러나지 않았다. **콘솔을 끄는 운영(`prod`)에서도 서버가 켜지지 않았을 문제**다.
- **해결**: 이 규칙을 콘솔이 켜져 있을 때만 등록한다. blog도 같은 코드라 함께 고쳤다.
  ```java
  @Bean
  @ConditionalOnBooleanProperty("spring.h2.console.enabled")
  public WebSecurityCustomizer h2ConsoleSecurityCustomizer() {
      return (web) -> web.ignoring().requestMatchers(toH2Console());
  }
  ```
  > **`@ConditionalOnBooleanProperty`란?**
  > 지정한 설정값이 `true`일 때만 이 빈(Spring이 관리하는 객체)을 만들게 하는 어노테이션이다. 설정에 따라 기능을 켜고 끌 때 쓴다.

### 2. 로컬 비밀 값이 JAR 안에 들어가고 있었다
- **문제**: JAR를 만들어 안을 보니 `BOOT-INF/classes/application-local.yml`(로컬 비밀 값)이 들어 있었다.
- **원인**: `src/main/resources`의 파일은 Git 제외 여부와 상관없이 빌드할 때 JAR에 포장된다. 5-1 이전의 `application.yml`(Git 제외, 비밀 값 포함)도 같은 위치였으므로, **그동안 로컬에서 만든 JAR에는 비밀 값이 들어 있었을 것**이다. 아직 JAR를 어디에도 올린 적은 없다.
- **해결**: 로컬 비밀 값 파일을 `backend/application-local.yml`로 옮겼다(2장). 다시 만든 JAR에 들어 있지 않은 것을 확인했다. 운영 JAR는 CI에서 만들 예정이고, CI에는 이 파일이 없다.
  > **Git 제외와 빌드 포함은 다르다**
  > `.gitignore`는 Git이 추적할지만 정한다. 빌드 도구(Gradle)는 `.gitignore`를 보지 않고, `src/main/resources`에 있는 파일을 모두 JAR에 넣는다.

### 3. 운영 설정이 빠졌을 때의 오류 문구가 엉뚱하다
- **문제**: `prod`로 켜고 `DB_URL`을 주지 않으면 서버는 멈추지만, 오류가 "환경 변수가 없다"가 아니라 `'url' must start with "jdbc"`로 나온다.
- **원인**: `@Value`로 읽는 값(예: `${BLOG_CLIENT_SECRET}`)은 풀지 못하면 `Could not resolve placeholder`로 멈춘다. 그런데 DB 설정처럼 Spring Boot가 묶어서 읽는 설정(`spring.datasource.*`)은 풀지 못한 `${DB_URL}`을 글자 그대로 넘긴다. 그래서 DB 연결을 만들 때 "주소가 jdbc로 시작하지 않는다"는 오류가 난다.
- **대응**: 멈춘다는 목적은 이룬다. 운영에서 이 문구가 보이면 `DB_URL`이 빠진 것이다. 필요한 환경 변수 목록은 `application-prod.yml` 맨 위에 적어 두었다.

### 4. 확인용 서버를 끄는 명령이 그 명령을 실행한 셸까지 끊었다
- **문제**: `pkill -f 'accounts.jar'`로 확인용 서버를 끄자, 그 명령을 실행한 셸도 함께 종료됐다.
- **원인**: `pkill -f`는 프로세스의 전체 명령줄에서 문자열을 찾는다. 셸의 명령줄에도 `accounts.jar`라는 글자가 들어 있어서 함께 걸렸다.
- **대응**: `pgrep -f '[b]logapp.jar'`처럼 첫 글자를 대괄호로 감싼다. 정규식으로는 같은 글자에 맞지만, 셸 명령줄에 적힌 글자(`[b]logapp`)와는 맞지 않는다.

## 5. 5-2: MySQL과 Flyway

### Flyway란?
**DB 테이블 구조를 바꾸는 SQL을 버전별 파일로 관리해 주는 도구**다. 서버가 켜질 때 아직 적용하지 않은 SQL만 골라 순서대로 실행한다.

5-1까지는 Hibernate가 엔티티 클래스를 보고 테이블을 자동으로 만들었다(`ddl-auto`). H2 메모리 DB는 서버를 끄면 사라지므로 켤 때마다 새로 만들어도 문제가 없었다.

> **`ddl-auto`란?**
> Hibernate가 엔티티(`@Entity` 클래스)를 보고 DB 테이블을 어떻게 다룰지 정하는 설정이다.
> - `create`: 켤 때마다 지우고 새로 만든다.
> - `update`: 없는 테이블과 컬럼만 더한다.
> - `validate`: 바꾸지 않고, 엔티티와 테이블이 맞는지 검사만 한다.
> - `none`: 아무것도 하지 않는다.
>
> H2 메모리 DB를 쓰면 기본값이 `create-drop`(켤 때 만들고 끌 때 지움)이다.

운영 MySQL은 데이터가 계속 남아야 하므로 이 방식을 쓸 수 없다.
- `create`는 켤 때마다 회원과 글을 지운다.
- `update`는 지우지는 않지만, 컬럼 이름 바꾸기나 기존 데이터 옮기기를 할 수 없다. 무엇을 언제 바꿨는지 기록도 남지 않는다.

Flyway는 테이블을 바꾸는 SQL을 번호가 붙은 파일로 저장해 둔다.
```
src/main/resources/db/migration/
├── V1__init.sql                         첫 테이블
├── V2__oauth2_authorization_server.sql  (accounts) 앱 등록, 발급 정보
└── V3__spring_session.sql               (accounts) 세션. blog는 V2
```
파일 이름은 `V<버전>__<설명>.sql`이다(밑줄 두 개). 서버가 켜지면 Flyway가 이렇게 한다.
1. DB의 `flyway_schema_history` 테이블에서 어디까지 적용했는지 읽는다. 처음이면 이 테이블을 만든다.
2. 아직 적용하지 않은 파일만 번호 순서대로 실행한다.
3. 실행한 파일의 버전, 설명, 시각, 체크섬을 `flyway_schema_history`에 기록한다.
4. 그 다음에 Hibernate와 웹 서버가 켜진다.

> **마이그레이션이란?**
> DB 구조(테이블, 컬럼, 인덱스)를 한 상태에서 다음 상태로 바꾸는 작업이다. Flyway에서는 SQL 파일 하나가 마이그레이션 하나다.

> **체크섬이란?**
> 파일 내용으로 계산한 짧은 숫자다. 한 글자만 달라도 값이 바뀐다. Flyway는 적용한 파일의 체크섬을 기억해 두었다가, 그 파일이 나중에 바뀌었으면 서버를 켜지 않고 알려 준다.

**예:** 3단계에서 글에 카테고리를 더한 일을 운영 DB에 글이 있는 상태에서 했다면, 엔티티만 바꾸지 않고 이런 파일을 새로 만든다.
```sql
-- V2__add_category.sql
ALTER TABLE article ADD COLUMN category VARCHAR(20);
UPDATE article SET category = 'DAILY' WHERE category IS NULL;  -- 기존 글은 일상으로
ALTER TABLE article MODIFY category VARCHAR(20) NOT NULL;
```
배포하면 Flyway가 `V2`만 실행한다. 기존 글은 지워지지 않고 "일상"으로 채워진다.

**지킬 규칙**
| 규칙 | 이유 |
|---|---|
| 이미 적용한 파일은 고치지 않는다. 바꿀 것이 있으면 새 번호의 파일을 만든다 | 운영 DB는 옛 내용으로 이미 바뀌었다. 파일을 고쳐도 다시 실행되지 않고, 체크섬이 달라져 서버가 켜지지 않는다 |
| Hibernate는 `validate`로 둔다 | 테이블은 Flyway가 만들고, Hibernate는 엔티티와 테이블이 맞는지만 검사한다. 엔티티만 고치고 SQL을 빠뜨리면 서버가 켜질 때 바로 알 수 있다 |
| 모든 환경이 같은 파일을 쓴다 | 로컬, 테스트, 운영이 같은 SQL로 같은 테이블을 만든다 |

### Flyway는 로컬에서만 필요한가?
아니다. **가장 필요한 곳은 운영**이다.

| 환경 | DB | Flyway가 필요한가 |
|---|---|---|
| 운영 (`prod`) | MySQL. 데이터가 계속 남는다 | 꼭 필요하다. 데이터를 지우지 않고 테이블을 바꾸는 방법이다 |
| 로컬 (`local`) | H2 메모리 | 없어도 돌아간다 |
| 테스트 (`test`) | H2 메모리 | 없어도 테스트는 돈다 |

그래도 로컬과 테스트에서도 쓴다. **SQL을 운영에 올리기 전에 미리 실행해 보기 위해서**다. 운영에서만 Flyway를 쓰면 스크립트를 처음 실행하는 곳이 운영 서버가 되어, 오타나 엔티티와의 차이가 있으면 배포한 순간 서버가 켜지지 않는다. 모든 환경에서 쓰면 `./gradlew test`를 돌릴 때마다 스크립트가 실행되고 엔티티와 맞는지 검사된다.

운영에서 따로 실행할 필요도 없다. Flyway는 Spring Boot 앱 안에 들어 있어서 **서버가 켜질 때 자동으로** 새 스크립트만 적용하고, 그 다음에 요청을 받기 시작한다.

### 구현
| 할 일 | 방법 |
|---|---|
| 의존성 | `spring-boot-starter-flyway`, `flyway-mysql`, `mysql-connector-j` |
| 로컬·테스트 DB | H2를 MySQL 호환 모드로: `jdbc:h2:mem:accounts;MODE=MySQL`. 운영과 같은 SQL이 돈다 |
| `ddl-auto` | `validate` |
| SQL 작성 기준 | 추측하지 않으려고, Hibernate가 MySQL용으로 만들 DDL을 먼저 뽑아(`jakarta.persistence.schema-generation.scripts`) 기준으로 삼았다 |
| enum 컬럼 | Hibernate는 MySQL에서 `@Enumerated` 컬럼을 MySQL 전용 `enum('DAILY',...)` 타입으로 기대한다. 값을 추가할 때마다 테이블을 바꿔야 해서, `@JdbcTypeCode(SqlTypes.VARCHAR)`로 `VARCHAR`를 쓰게 했다 |
| 시각 | `Instant`는 DB마다 다른 타입으로 기대되어서(H2: 시간대 있는 timestamp, MySQL: datetime), `hibernate.type.preferred_instant_jdbc_type: TIMESTAMP`로 맞췄다. 저장은 UTC(`hibernate.jdbc.time_zone: UTC`) |
| blog 예시 글 | Hibernate의 `import.sql`은 테이블을 자동으로 만들 때만 실행된다. 로컬 프로필에서만 읽는 `db/sample/R__sample_data.sql`로 옮겼다 |

> **`R__`로 시작하는 스크립트란?**
> 반복 실행 스크립트(Repeatable migration)다. 번호가 있는 `V` 스크립트를 모두 적용한 뒤에 실행되고, 내용이 바뀔 때마다 다시 실행된다. 버전 순서와 상관없는 예시 데이터에 쓴다.

**확인:** 테스트에서 `Migrating schema "PUBLIC" to version "1 - init"`, `Successfully applied 1 migration`. 로컬 실행에서 마이그레이션 2개(V1, 예시 데이터)가 적용되고 예시 글 3개가 보인다.

## 6. 5-3: accounts 영구 저장

| 저장할 것 | 방법 |
|---|---|
| 서명 키 | `SigningKeys`: PEM 파일(`SIGNING_KEY_PATH`) 또는 한 줄 Base64(`SIGNING_KEY_BASE64`)에서 읽는다. 둘 다 없으면 로컬은 새로 만들고, 운영(`accounts.signing-key.required: true`)은 켜지지 않는다. 키 id(`kid`)를 공개 키로 계산(JWK thumbprint)해서 같은 키면 언제나 같은 id다 |
| 앱 등록 | `JdbcRegisteredClientRepository`. 서버를 켤 때 `ApplicationRunner`가 설정값으로 blog 앱을 등록하고, 이미 있으면 같은 id로 덮어쓴다 |
| 발급한 코드·토큰, 로그인 사용자 | `JdbcOAuth2AuthorizationService`. 테이블은 Spring Authorization Server가 제공하는 스크립트 그대로(`V2__oauth2_authorization_server.sql`) |
| 동의 | `JdbcOAuth2AuthorizationConsentService` (동의 화면을 생략하므로 지금은 비어 있다) |

### 로그인 사용자를 JSON으로 저장하기
`JdbcOAuth2AuthorizationService`는 발급 정보에 담긴 로그인 사용자(`Authentication`)를 **JSON으로 바꿔** 저장한다. 보안 때문에 Spring Security는 **허용 목록에 있는 클래스만 JSON에서 되살린다.** 아무 클래스나 되살리게 하면, DB에 조작된 JSON이 들어갔을 때 위험한 클래스가 만들어질 수 있기 때문이다.

우리 principal(`AccountUserDetails`, `AccountOAuth2User`)은 목록에 없으므로 `AccountsJackson`에서 두 가지를 더했다.
- 허용 목록: `BasicPolymorphicTypeValidator.Builder`에 두 클래스를 더해 `SecurityJacksonModules.getModules(...)`에 넘긴다.
- 되살리는 방법: 믹스인으로 "필드 값을 저장하고, 이 생성자로 되살린다"를 알려 준다.

> **믹스인(Mixin)이란?**
> 클래스를 고치지 않고 JSON 변환 규칙(어노테이션)을 덧붙이는 Jackson 기능이다. 도메인 클래스에 JSON 어노테이션을 섞지 않으려고 쓴다.

### 로그인 후 비밀번호 해시 지우기
`AccountUserDetails`가 로그인 뒤에도 비밀번호 해시를 들고 있었다. 이 객체는 세션과 발급 정보(JSON)에 그대로 저장되므로, `CredentialsContainer`를 구현해 인증이 끝나면 해시를 지우게 했다. 로그인 상태 유지 쿠키는 만들 때 해시가 필요한데, 해시가 비어 있으면 Spring Security가 DB에서 다시 읽으므로 그대로 동작한다(테스트 통과).

### MySQL에서 주의할 점
Spring Authorization Server의 스크립트 주석에 적혀 있다. MySQL에서 시각을 정확히 저장하려면 JDBC 주소에 옵션을 붙인다.
```
jdbc:mysql://<호스트>:3306/accounts?preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

**확인:** 코드로 토큰을 받은 뒤 `oauth2_authorization`에 행이 있고, 리프레시 토큰으로 새 토큰을 받는다(DB에서 읽고 principal을 되살려야 성공한다). 앱 등록이 `oauth2_registered_client`에 있다. 같은 PEM을 두 번 읽으면 같은 키·같은 `kid`, Base64로 읽어도 파일과 같은 키. principal을 JSON으로 바꿨다 되살리는 단위 테스트.

## 7. 5-4: 세션을 DB에
- `spring-boot-starter-session-jdbc`. 테이블은 Spring Session의 `schema-mysql.sql`에서 MySQL 전용 옵션(`ENGINE=InnoDB ROW_FORMAT=DYNAMIC`)만 뺀 것. MySQL 8은 두 값이 기본값이라 결과가 같다.
- `spring.session.jdbc.initialize-schema: never` (테이블은 Flyway가 만든다).
- 세션 값은 Java 직렬화로 저장된다. principal 클래스는 이미 `Serializable`이다.
- **확인:** 로그인하면 `SPRING_SESSION`에 행이 생기고, 쿠키 이름이 `ACCOUNTS_SESSION`/`BLOG_SESSION`이다.

## 8. 5-6: HTTPS 대비
| 쿠키 | 설정 |
|---|---|
| 세션 | 공통 `http-only: true`, `same-site: lax`. 운영 `secure: true` |
| CSRF(`XSRF-TOKEN`), 로그인 상태 유지 | 요청이 HTTPS면 자동으로 `Secure`. 그러려면 앞단이 `X-Forwarded-Proto: https`를 넘겨야 한다 |

> **SameSite란?**
> 다른 사이트에서 시작된 요청에 쿠키를 보낼지 정하는 속성이다. `Strict`는 보내지 않고, `Lax`는 링크로 이동하는 GET 요청에만 보내고, `None`은 항상 보낸다(`Secure` 필요). `Lax`는 accounts에서 blog로 돌아오는 로그인 리디렉트에는 쿠키를 보내고, 다른 사이트가 몰래 보내는 POST에는 보내지 않는다.

**확인 (로컬, blog Spring + blog Next.js):** Next.js를 거친 요청에 `X-Forwarded-Proto: https`를 붙이면 Spring이 받은 것으로 처리한다. 응답 쿠키 `XSRF-TOKEN=...; Secure`, `BLOG_SESSION=...; Secure; HttpOnly`, 로그인 시작의 `redirect_uri=https://...`. 헤더가 없으면 `Secure`가 붙지 않는다. 즉 **Next.js rewrites는 `X-Forwarded-Proto`를 Spring까지 넘긴다.**

## 9. 헬스 체크 주소
Elastic Beanstalk의 로드밸런서는 몇 초마다 서버의 정해진 주소가 200을 주는지 확인한다(헬스 체크). 기본은 `/`인데, accounts의 `/`는 로그인 화면으로 보내는 302라서 정상인데도 비정상으로 판단된다. 그래서 `spring-boot-starter-actuator`를 더하고 `/actuator/health`만 공개했다(로그인 없이 `{"status":"UP"}`).

> **Actuator란?**
> Spring Boot의 운영용 기능 모음이다. `/actuator/health`는 서버와 DB 연결이 정상인지 알려 준다. 다른 기능(설정값 등)은 기본으로 외부에 공개되지 않는다.

## 10. 트러블슈팅 (5-2 ~ 5-6)

### 5. Spring Session을 쓰자 테스트에서 세션 쿠키 이름이 바뀌었다
- **문제**: 세션을 DB에 저장하게 하자, 테스트에서 `ACCOUNTS_SESSION`, `BLOG_SESSION` 쿠키가 없다고 실패했다.
- **원인**: Spring Session은 쿠키를 자기 방식(`DefaultCookieSerializer`)으로 만든다. Spring Boot 4는 **내장 웹 서버로 실행할 때만** `server.servlet.session.cookie.name`을 이 쿠키에 적용한다(`EmbeddedWebServerConfiguration`). 내장 서버 없이 도는 MockMvc 테스트는 다른 설정(`WarDepoymentConfiguration`)을 타서 이름이 달라졌다.
- **해결**: 두 경우 모두에 적용되는 `DefaultCookieSerializerCustomizer`로 쿠키 이름을 직접 지정했다(`SessionConfig`).

### 6. `Map.of`로 만든 Map은 JSON에서 되살아나지 않았다
- **문제**: principal을 JSON으로 바꿨다 되살리는 단위 테스트가 `missing type id property '@class'`로 실패했다. 실제 흐름(리프레시 토큰 재발급) 테스트는 통과했다.
- **원인**: 테스트에서 `Map.of(...)`로 만든 변경 불가 Map을 저장했는데, 이 Map은 클래스 이름(`@class`)이 저장되지 않아 다시 읽을 수 없었다.
- **해결**: Spring Authorization Server처럼 일반 `HashMap`에 담아 저장하게 테스트를 고쳤다. principal 안의 컬렉션도 `LinkedHashMap`, `ArrayList`에 담는다.

### 7. zsh에서 환경 변수를 변수 하나에 담아 넘기자 프로필 이름이 깨졌다
- **문제**: `E="SPRING_PROFILES_ACTIVE=prod DB_URL=..."` 후 `env $E java -jar ...`로 실행하자, 프로필 이름이 `prodDB_URL=...` 전체로 들어갔다.
- **원인**: bash는 따옴표 없는 `$E`를 공백으로 나누지만, zsh는 나누지 않는다.
- **해결**: 실행 명령을 작은 스크립트 파일로 만들어 환경 변수를 하나씩 적었다.

## 11. 미룬 것
빨리 배포하기 위해 배포 뒤로 미뤘다.
| 미룬 것 | 영향 |
|---|---|
| 5-5 인증번호 발송 횟수 제한 | 같은 이메일로 메일을 많이 보내게 할 수 있다. SMTP 설정은 환경 변수(`SPRING_MAIL_*`)로 이미 받는다 |
| 5-7 로컬 MySQL 리허설, 브라우저 확인 | 실제 배포한 서버에서 확인한다 |
| 운영 DB 계정 분리 | 처음에는 RDS 관리자 계정을 쓴다. 서비스별 계정은 나중에 |

## 12. 다음
6단계 실제 배포: [6단계 계획](./13_step6_deployment_plan.md).
