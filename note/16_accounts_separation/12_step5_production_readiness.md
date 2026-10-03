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

## 4. 트러블슈팅

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

## 5. 다음
5-2 MySQL과 Flyway.
