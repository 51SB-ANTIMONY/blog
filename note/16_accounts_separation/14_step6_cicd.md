---
title: accounts 분리하기 - 6단계 CI/CD (GitHub Actions로 Elastic Beanstalk 자동 배포)
date: 2026-10-04
tags: [ci, cd, github-actions, elastic-beanstalk, deployment]
---

# accounts 분리하기 - 6단계 CI/CD (GitHub Actions로 Elastic Beanstalk 자동 배포)

> 배포 구성과 손으로 배포한 과정은 [6단계 배포](./13_step6_deployment_plan.md)에, 전체 단계는 [로드맵](./01_roadmap.md)에 있다.

## 1. 목적
`main` 브랜치에 push하면 **테스트 → 배포 묶음 만들기 → Elastic Beanstalk 배포**가 자동으로 일어나게 한다. accounts와 blog 저장소에 각각 만든다.

지금까지 Spring 서버를 배포할 때는 사람이 직접 아래를 했다.
1. IntelliJ에서 `ebBundle` 실행 → `build/distributions/<이름>-eb.zip` 생성
2. AWS 콘솔 → Elastic Beanstalk → 환경 → "업로드 및 배포" → zip 선택
3. 배포가 끝날 때까지 기다리고 health 확인

이 방식에는 사람이 실수할 자리가 있다. 테스트를 돌리지 않고 올리거나, zip 대신 JAR를 올리거나([6단계 트러블슈팅 3](./13_step6_deployment_plan.md) 참고: JAR만 올리면 HTTPS 설정이 빠진다), 다른 환경에 올릴 수 있다. CI/CD는 이 과정을 파일 하나에 적어 두고, 매번 같은 순서로 실행되게 한다.

## 2. 개념

### CI와 CD
> **CI(Continuous Integration, 지속적 통합)란?**
> 코드를 저장소에 올릴 때마다 자동으로 빌드와 테스트를 실행하는 것이다. 여러 사람이(또는 한 사람이 여러 번) 코드를 합칠 때, 합친 결과가 컴파일되고 테스트를 통과하는지 바로 확인한다. 실패하면 어느 커밋에서 깨졌는지 곧바로 알 수 있다.

> **CD란?**
> 두 가지 뜻으로 쓴다.
> - **Continuous Delivery(지속적 전달)**: CI를 통과한 결과물을 언제든 배포할 수 있는 상태로 만들어 둔다. 실제 배포는 사람이 버튼을 눌러 한다.
> - **Continuous Deployment(지속적 배포)**: CI를 통과하면 사람의 승인 없이 운영 서버에 바로 배포한다.
>
> 이 프로젝트는 **Continuous Deployment**다. `main`에 push하고 테스트가 통과하면 운영 서버에 바로 배포된다.

CI와 CD는 이어져 있다. CD는 CI가 통과한 결과물만 배포해야 의미가 있다. 그래서 하나의 흐름 안에 "테스트 → 통과하면 배포" 순서로 둔다.

### GitHub Actions
> **GitHub Actions란?**
> GitHub가 제공하는 CI/CD 서비스다. 저장소 안의 `.github/workflows/` 폴더에 YAML 파일을 두면, GitHub가 정해진 사건(push 등)이 일어날 때 그 파일에 적힌 명령을 GitHub의 서버에서 실행한다. 공개 저장소는 무료, 비공개 저장소는 매달 정해진 실행 시간까지 무료다.

GitHub Actions의 구성 요소는 아래와 같다. 큰 것에서 작은 것 순서다.

| 구성 요소 | 뜻 | 이 프로젝트에서 |
|---|---|---|
| **workflow** (워크플로) | `.github/workflows/*.yml` 파일 하나. 언제, 무엇을 실행할지 전부 적는다 | `cicd.yml` |
| **event** (이벤트, trigger) | 워크플로를 시작시키는 사건 | `main` 브랜치에 push |
| **job** (잡) | 한 대의 서버에서 이어서 실행되는 작업 묶음. 잡이 여러 개면 기본적으로 서로 다른 서버에서 동시에 실행된다 | `build-deploy` 하나 |
| **runner** (러너) | 잡을 실행하는 서버. GitHub가 잡마다 새 가상 머신을 만들어 주고, 잡이 끝나면 지운다 | `ubuntu-latest` |
| **step** (스텝) | 잡 안에서 위에서 아래로 하나씩 실행되는 단계. 하나라도 실패하면 그 뒤 스텝은 실행되지 않는다 | 4개 |
| **action** (액션) | 다른 사람이 만들어 공개한, 다시 쓸 수 있는 스텝. `uses:`로 가져다 쓴다 | `actions/checkout`, `actions/setup-java`, `einaregilsson/beanstalk-deploy` |

> **가상 머신(VM)이란?**
> 실제 컴퓨터 한 대 위에서 소프트웨어로 만든, 독립된 컴퓨터다. 운영체제, 디스크, 메모리를 따로 가진다. GitHub의 러너는 잡마다 깨끗한 가상 머신을 새로 받으므로, 이전 실행에서 남은 파일이 없다. 그래서 필요한 것(코드, Java 등)은 매번 스텝에서 준비한다.

스텝은 두 종류다.
| 종류 | 쓰는 법 | 하는 일 |
|---|---|---|
| `uses` | `uses: 소유자/저장소@버전` | 공개된 액션을 실행한다. 설정은 `with:` 아래에 넘긴다 |
| `run` | `run: 명령어` | 러너의 셸에서 명령어를 그대로 실행한다 (리눅스면 bash) |

### YAML
> **YAML이란?**
> 설정을 적는 텍스트 형식이다. `이름: 값` 꼴로 적고, **들여쓰기(공백)로 포함 관계**를 나타낸다. 탭 문자는 쓸 수 없고 공백만 쓴다. `-`로 시작하는 줄은 목록의 한 항목이다. `#` 뒤는 주석이다.

```yaml
on:              # "on" 아래에
  push:          #   "push" 아래에
    branches:    #     "branches"의 값은
      - main     #       목록 [main]  (같은 뜻: branches: [main])
```
들여쓰기가 한 칸만 어긋나도 뜻이 달라지거나 파일을 읽지 못한다. GitHub는 문법이 틀린 워크플로를 실행하지 않고 Actions 탭에 오류로 보여 준다.

### 표현식과 컨텍스트
> **표현식(`${{ }}`)이란?**
> 워크플로 파일 안에서 실행할 때 정해지는 값을 넣는 문법이다. `${{ github.sha }}`는 실행할 때 실제 커밋 해시로 바뀐다.

> **컨텍스트(context)란?**
> 표현식에서 꺼내 쓸 수 있는 값의 묶음이다. 이 파일에서는 두 가지를 쓴다.
> - `github`: 이번 실행의 정보. `github.sha`는 이번 실행을 일으킨 커밋의 해시(40자)
> - `secrets`: 저장소에 등록한 비밀 값. `secrets.AWS_ACCESS_KEY_ID` 등

### 비밀 값(Secrets)
> **GitHub Secrets란?**
> 저장소 Settings → Secrets and variables → Actions에 등록하는 암호화된 값이다. 워크플로 파일에는 이름(`${{ secrets.이름 }}`)만 적고, 값은 실행할 때 GitHub가 넣어 준다. 실행 로그에 그 값이 찍히면 `***`로 가려진다. 한 번 등록한 값은 GitHub 화면에서도 다시 볼 수 없고 덮어쓰기만 된다. **저장소마다 따로 등록**하므로 accounts와 blog에 각각 넣었다.

이 워크플로는 AWS에 배포하려고 AWS 액세스 키를 쓴다.

> **IAM 사용자와 액세스 키란?**
> IAM(Identity and Access Management)은 AWS에서 "누가 무엇을 할 수 있는지"를 정하는 서비스다. IAM 사용자는 AWS 계정 안에 만드는 하나의 이용자이고, **액세스 키**는 그 사용자로 AWS API를 호출할 때 쓰는 아이디(`AWS_ACCESS_KEY_ID`)와 비밀번호(`AWS_SECRET_ACCESS_KEY`) 한 쌍이다. 사용자에게 붙인 **정책(policy)**에 적힌 일만 할 수 있다.
>
> 배포 전용 사용자를 따로 만들고 Elastic Beanstalk 배포에 필요한 권한(`AdministratorAccess-AWSElasticBeanstalk` 정책)만 주는 것을 권한다. 키가 새어도 할 수 있는 일이 Elastic Beanstalk 관리로 제한된다. 루트 계정(AWS에 처음 가입한 계정)의 키는 모든 권한을 가지므로 절대 쓰지 않는다.

> **API란?**
> 프로그램이 다른 프로그램의 기능을 부르는 정해진 방법이다. AWS 콘솔에서 버튼을 누르는 일도 내부적으로는 AWS API 호출이다. 워크플로는 콘솔 대신 API를 직접 호출해 같은 일을 한다.

### Elastic Beanstalk의 "애플리케이션 버전"
> **애플리케이션 버전(application version)이란?**
> Elastic Beanstalk에 올린 배포 파일(zip) 하나에 이름(버전 레이블)을 붙여 저장해 둔 것이다. 파일 자체는 S3 버킷(`elasticbeanstalk-ap-northeast-2-<계정 ID>`)에 저장된다. 환경은 "어느 버전을 실행할지"를 가리키고, 배포는 환경이 가리키는 버전을 바꾸는 일이다. 예전 버전도 남아 있으므로, 문제가 생기면 콘솔에서 예전 버전을 골라 다시 배포할 수 있다.

콘솔의 "업로드 및 배포"는 사실 아래 세 API 호출을 한 번에 한 것이다. 워크플로도 같은 순서로 호출한다.
1. zip을 S3에 올린다.
2. 그 파일로 애플리케이션 버전을 만든다 (`CreateApplicationVersion`).
3. 환경이 그 버전을 쓰도록 바꾼다 (`UpdateEnvironment`). 이때 EC2 안에서 배포 훅, Nginx 설정, Spring 재시작이 일어난다([6단계](./13_step6_deployment_plan.md)의 `.platform` 설명).

## 3. 전체 흐름
```
개발자: git push (main)
   │
   ▼
GitHub: push 이벤트 → .github/workflows/cicd.yml 실행
   │
   ▼
러너 (ubuntu-latest 가상 머신, 잡마다 새로)
   ① checkout        저장소 코드를 내려받음
   ② setup-java      Corretto 25 설치, Gradle 캐시 복원
   ③ gradlew         clean → test → ebBundle   (테스트 실패 시 여기서 멈춤, 배포 안 함)
   ④ beanstalk-deploy
        ├ zip을 S3에 업로드
        ├ 애플리케이션 버전 생성 (blog-<커밋 해시>)
        ├ 환경 업데이트 요청
        └ 배포가 끝나고 환경이 정상(Green)이 될 때까지 기다림
   │
   ▼
Elastic Beanstalk EC2 (단일 인스턴스)
   prebuild 훅(S3에서 인증서) → Nginx 설정(443) → Spring 재시작
```

화면(Next.js)은 이 워크플로가 배포하지 않는다. Vercel이 GitHub 저장소와 연결되어 있어서, push하면 Vercel이 `frontend/`를 따로 빌드하고 배포한다. 즉 push 한 번에 두 곳(GitHub Actions → Elastic Beanstalk, Vercel)에서 배포가 일어난다.

## 4. 코드 설명
두 저장소의 `.github/workflows/cicd.yml`은 이름 4곳만 다르다.

| 항목 | blog | accounts |
|---|---|---|
| `application_name` | `Antimony_Blog` | `accounts-api` |
| `environment_name` | `AntimonyBlog-env-1` | `Accounts-api-env` |
| `version_label` | `blog-${{ github.sha }}` | `accounts-${{ github.sha }}` |
| `deployment_package` | `backend/build/distributions/blog-eb.zip` | `backend/build/distributions/accounts-eb.zip` |

아래는 blog의 파일이다.

### 4-1. 이름과 실행 조건
```yaml
name: CI/CD

on:
  push:
    branches: [main]
```
- `name`: GitHub 저장소의 **Actions** 탭에 보이는 워크플로 이름.
- `on`: 언제 실행할지. `push` 이벤트 중 `main` 브랜치에 대한 push만 받는다. 다른 브랜치에 push하면 실행되지 않는다. 운영 서버에 배포하는 워크플로이므로 `main`으로 제한해야 한다. 제한하지 않으면(`on: push`) 실험용 브랜치를 push해도 운영에 배포된다.

### 4-2. 잡과 러너
```yaml
jobs:
  build-deploy:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
```
- `jobs` 아래의 `build-deploy`는 잡 이름이다. 이름은 자유롭게 정한다.
- `runs-on: ubuntu-latest`: GitHub가 제공하는 최신 Ubuntu 리눅스 가상 머신에서 실행한다.
- `defaults.run.working-directory: backend`: 이 잡의 모든 `run` 스텝을 `backend/` 폴더에서 실행한다. 이 저장소는 맨 위에 `backend/`(Spring)와 `frontend/`(Next.js)가 나란히 있고, `gradlew`와 `build.gradle`은 `backend/` 안에 있다. 이 설정이 없으면 저장소 맨 위에서 `./gradlew`를 찾다가 "파일 없음"으로 실패한다.
  - **주의**: 이 설정은 `run` 스텝에만 적용되고 `uses` 스텝에는 적용되지 않는다. 그래서 4-6의 `deployment_package`는 `backend/`부터 적는다.

### 4-3. 코드 내려받기
```yaml
    steps:
      - uses: actions/checkout@v6
```
- 러너는 빈 가상 머신이므로, 먼저 이 저장소의 코드를 내려받는다. 이벤트를 일으킨 커밋(`github.sha`)의 코드가 내려받아진다.
- `@v6`은 액션의 버전이다. 버전을 적어 두어야 액션 제작자가 새 버전을 내도 이 워크플로의 동작이 갑자기 바뀌지 않는다.

### 4-4. Java 설치와 Gradle 캐시
```yaml
      - uses: actions/setup-java@v5
        with:
          distribution: 'corretto'
          java-version: '25'
          cache: 'gradle'
```
- `distribution: 'corretto'`, `java-version: '25'`: Amazon Corretto 25를 설치한다. 운영 서버(Elastic Beanstalk "Corretto 25" 플랫폼)와 같은 Java로 빌드하고 테스트하려는 것이다. `build.gradle`의 toolchain(`JavaLanguageVersion.of(25)`)도 이 Java를 찾아 쓴다.
- `cache: 'gradle'`: Gradle이 내려받은 라이브러리(`~/.gradle/caches`)와 Gradle 자체(`~/.gradle/wrapper`)를 실행이 끝날 때 GitHub에 저장해 두고, 다음 실행 때 되살린다.

> **캐시란?**
> 한 번 만들거나 내려받은 결과를 저장해 두었다가 다시 쓰는 것이다. 러너는 매번 새 가상 머신이라, 캐시가 없으면 Spring Boot 라이브러리 수백 MB를 매번 새로 내려받는다. 캐시의 키는 `*.gradle` 파일과 `gradle-wrapper.properties`의 내용으로 만든다. 의존성을 바꿔 이 파일들이 달라지면 키가 달라져 새로 내려받고, 다시 저장한다.

### 4-5. 테스트와 배포 묶음
```yaml
      # 테스트 후 Elastic Beanstalk 배포 묶음(JAR + HTTPS 설정)을 만든다
      - name: Test and build bundle
        run: ./gradlew clean test ebBundle
```
- `name`: Actions 화면에서 이 스텝에 보이는 이름. 없으면 명령어가 그대로 보인다.
- `./gradlew`: 저장소에 들어 있는 Gradle Wrapper로 Gradle을 실행한다. 러너에 Gradle이 설치되어 있지 않아도, `gradle-wrapper.properties`에 적힌 버전(9.2.0)을 내려받아 쓴다. `gradlew` 파일은 Git에 실행 권한(`100755`)으로 저장되어 있어서 `chmod +x`가 필요 없다.
- Gradle 작업 3개를 순서대로 실행한다.

| 작업 | 하는 일 |
|---|---|
| `clean` | `build/` 폴더를 지운다. 러너는 매번 새것이라 실제로 지울 것은 없지만, 이전 결과물이 섞이지 않는다는 것을 분명히 한다 |
| `test` | 모든 테스트를 실행한다. `build.gradle`의 `systemProperty 'spring.profiles.active', 'test'` 때문에 test 프로필(H2 메모리 DB, 가짜 비밀 값)로 실행되므로, **RDS나 운영 비밀 값 없이** 돌아간다 |
| `ebBundle` | `bootJar`(실행 가능한 JAR)를 만든 뒤, JAR(`application.jar`로 이름 변경) + `Procfile` + `.platform/` + `.ebextensions/`를 `build/distributions/blog-eb.zip`으로 묶는다([6단계](./13_step6_deployment_plan.md)의 "HTTPS를 위해 저장소에 추가한 파일") |

- 명령어가 0이 아닌 종료 코드로 끝나면(테스트 실패, 컴파일 오류) 스텝이 실패하고, **다음 스텝(배포)은 실행되지 않는다.** 깨진 코드가 운영에 올라가지 않는 이유가 이것이다.

> **종료 코드(exit code)란?**
> 프로그램이 끝날 때 운영체제에 돌려주는 숫자다. 0이면 성공, 0이 아니면 실패를 뜻한다. GitHub Actions는 `run` 스텝의 종료 코드로 성공·실패를 판단한다.

> **`build` 대신 `test ebBundle`을 쓴 이유**
> `build`는 테스트와 함께 `jar`(실행할 수 없는 일반 JAR)와 `bootJar`를 모두 만든다. 배포에 필요한 것은 `ebBundle` 안의 `bootJar`뿐이므로, 필요한 작업만 적었다.

### 4-6. Elastic Beanstalk 배포
```yaml
      - name: Beanstalk Deploy
        uses: einaregilsson/beanstalk-deploy@v22
        with:
          aws_access_key: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws_secret_key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          application_name: Antimony_Blog
          environment_name: AntimonyBlog-env-1
          version_label: blog-${{ github.sha }}
          region: ap-northeast-2
          # uses 단계에는 working-directory가 적용되지 않아 backend/부터 적는다
          deployment_package: backend/build/distributions/blog-eb.zip
```
`einaregilsson/beanstalk-deploy`는 2절 "애플리케이션 버전"의 세 API 호출(S3 업로드 → 버전 생성 → 환경 업데이트)을 해 주는 공개 액션이다. AWS 공식 액션은 아니고 개인이 만든 것이지만 널리 쓰인다.

| 설정 | 값 | 뜻 |
|---|---|---|
| `aws_access_key`, `aws_secret_key` | Secrets | 배포 전용 IAM 사용자의 액세스 키. 파일에 값을 적지 않는다 |
| `application_name` | `Antimony_Blog` | Elastic Beanstalk 애플리케이션 이름 (콘솔에 보이는 그대로, 대소문자까지) |
| `environment_name` | `AntimonyBlog-env-1` | 배포할 환경 이름. 접속 주소(`antimonyblog-env-1.eba-...`)는 소문자로 보이지만 환경 이름은 대소문자가 섞여 있으므로 콘솔에서 확인해 적는다 |
| `version_label` | `blog-<커밋 해시>` | 만들 애플리케이션 버전의 이름. 커밋 해시를 쓰면 콘솔에서 "지금 돌고 있는 것이 어느 커밋인지" 바로 알 수 있고, 커밋마다 이름이 겹치지 않는다 |
| `region` | `ap-northeast-2` | 서울 리전 |
| `deployment_package` | zip 경로 | 4-5에서 만든 배포 묶음. 저장소 맨 위 기준 경로 |

- 이 액션은 기본적으로 **배포가 끝나고 환경이 정상이 될 때까지 기다린다.** 그래서 Actions에서 초록색 체크가 뜨면 Elastic Beanstalk 배포까지 끝났다는 뜻이다. 배포 중 환경이 오류 상태가 되면 이 스텝이 실패(빨간색)한다.

> **액션을 버전으로 고정하는 것과 신뢰**
> 이 액션에는 AWS 키를 넘긴다. 액션 코드가 악의적으로 바뀌면 키가 새어 나갈 수 있다. `@v22`처럼 버전을 고정하면 제작자가 새 버전을 내도 자동으로 바뀌지 않는다. 더 엄격하게는 버전 대신 커밋 해시(`@<40자 해시>`)로 고정한다. 배포 전용 IAM 사용자의 권한을 Elastic Beanstalk으로 제한한 것도 같은 이유다.

## 5. 이 파일에 이르기까지

### 첫 번째: `ci.yml` (커밋 `26a5e18`)
```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: 'corretto'
          java-version: '25'
```
코드를 내려받고 Java를 설치한 뒤 끝난다. `run` 스텝이 없어서 아무것도 검사하지 않고, 코드가 깨져 있어도 항상 성공으로 나온다. CI의 뼈대만 있는 상태로 먼저 커밋했다.

### 두 번째: 예제를 가져온 `cicd.yml` (커밋하지 않음)
Spring Boot 배포 예제의 워크플로를 가져왔다. 그대로 push했다면 이렇게 됐다.

| 예제의 내용 | 문제 | 고친 것 |
|---|---|---|
| `run: chmod +x gradlew`, `./gradlew clean build` (저장소 맨 위에서 실행) | 이 저장소의 `gradlew`는 `backend/` 안에 있어서 **"파일 없음"으로 실패** | `working-directory: backend` |
| `application_name: springboot-developer`, `environment_name: Springboot-developer-env` | 예제의 이름 그대로라 그런 환경이 없어 **실패** | 실제 이름 |
| `deployment_package: ./build/libs/${{env.artifact}}` (JAR 파일) | 배포에 성공해도 HTTPS 설정(`.platform`)이 빠져 Nginx 443이 사라진다. Cloudflare가 연결하지 못해 **blog-api가 내려간다** | `ebBundle`의 zip |
| `jar { enabled = false }` (build.gradle) | `build/libs`에 JAR가 하나만 남게 해서 `ls`로 파일 이름을 찾는 예제용 설정. zip을 쓰면 필요 없다 | 지움 |
| `on: push` | 어느 브랜치든 push하면 운영에 배포 | `branches: [main]` |
| 시간을 구하는 외부 액션(`josStorer/get-current-time`)으로 버전 이름 | 버전 이름만으로는 어느 커밋인지 알 수 없고, 외부 액션이 하나 더 늘어난다 | `github.sha` |

### 세 번째: 지금 파일 (blog `c00acbc`, accounts `38855e2`)
4절의 파일이다. `ci.yml`은 지우고 `cicd.yml` 하나로 합쳤다(이전 커밋은 그대로 두고 새 커밋으로).

## 6. 확인 결과
두 저장소를 push한 뒤 확인했다.
| 확인 | 결과 |
|---|---|
| `https://accounts-api.antimony.kr/actuator/health` | UP |
| `https://blog-api.antimony.kr/actuator/health` | UP |
| 로그인 시작 → 로그인 화면 주소 | `https://accounts.antimony.kr/login` (Nginx의 공개 주소 고정이 배포된 상태) |
| blog 로그인 시작 | `accounts.antimony.kr/oauth2/authorize?...`로 이동 |

Elastic Beanstalk 콘솔에서 각 환경의 "실행 중인 버전"이 `blog-<해시>`, `accounts-<해시>`로 보이면 GitHub Actions가 배포한 것이다.

## 7. 알아 둘 점
| 상황 | 일어나는 일 | 대응 |
|---|---|---|
| 노트(`note/`)만 고쳐서 push | blog 저장소의 워크플로가 실행되어 **서버가 다시 배포된다**. 코드가 같아도 재시작한다 | 나중에 `on.push.paths`로 `backend/**`, `.github/**`가 바뀔 때만 실행하도록 제한할 수 있다 |
| 배포 중 | 단일 인스턴스라 Spring이 재시작하는 수십 초 동안 API가 502를 낸다 | 로드밸런서와 인스턴스 2대 이상이면 한 대씩 바꿔 끊김을 없앨 수 있다(비용 증가) |
| 짧은 간격으로 두 번 push | 앞의 배포가 끝나기 전에 뒤의 배포가 환경을 업데이트하려 하면, Elastic Beanstalk이 "환경이 업데이트 중"이라며 거절해 뒤의 실행이 실패할 수 있다 | 앞의 배포가 끝난 뒤 실패한 실행을 다시 실행하거나, 워크플로에 `concurrency`를 두어 한 번에 하나만 실행되게 한다 |
| 실패한 실행을 "Re-run" | 같은 커밋이므로 버전 이름(`blog-<해시>`)이 같다. 앞선 실행에서 버전이 이미 만들어졌다면 "이미 있는 버전"이라며 실패할 수 있다 | 액션 설정 `use_existing_version_if_available: true`를 추가하면 있는 버전을 그대로 배포한다 |
| 테스트 실패 | 배포 스텝이 실행되지 않는다. 운영 서버는 이전 버전 그대로 | Actions 탭에서 실패한 테스트를 확인해 고친다 |
| 환경 속성(비밀 값) 변경 | 워크플로와 상관없다. 환경 속성은 Elastic Beanstalk 콘솔에서 바꾼다 | |

### 나중에 바꿀 수 있는 것
- **장기 액세스 키 대신 OIDC 역할**: GitHub Actions가 실행될 때마다 AWS에서 잠깐 쓰는 임시 권한을 받는 방식(`aws-actions/configure-aws-credentials`)이다. 저장소에 오래 유효한 키를 둘 필요가 없어진다.
- **CI와 CD 분리**: PR(`pull_request`)에서는 테스트만, `main` push에서는 테스트+배포를 한다.
- **frontend 검사**: 지금 frontend는 Vercel의 빌드에만 맡긴다. 워크플로에 `npm ci`, `npm run lint`, `npm run build` 잡을 추가하면 Vercel 배포 전에 lint 오류를 잡을 수 있다.
