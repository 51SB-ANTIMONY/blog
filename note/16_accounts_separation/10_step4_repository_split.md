---
title: accounts 분리하기 - 4단계 저장소 분리
date: 2026-10-03
tags: [git, subtree, repository, accounts]
---

# accounts 분리하기 - 4단계 저장소 분리

> 전체 단계는 [로드맵](./01_roadmap.md)에 있다.

## 1. 목적
**`blog/accounts/` 폴더를 새 Git 저장소(`51SB-ANTIMONY/accounts`)로 옮긴다.** accounts의 커밋 이력도 함께 옮긴다.

| 목적 | 내용 |
|---|---|
| 서비스마다 저장소 하나 | accounts는 blog만이 아니라 antimony.kr의 모든 앱이 쓰는 로그인 서버다. blog와 따로 버전을 관리하고 배포한다 |
| 배포 전에 나누기 | 배포에는 저장소마다 CI/CD(자동 빌드·배포)와 서버 설정이 필요하다. 나누기 전에 만들면 나눈 뒤 다시 만들어야 한다 |
| 이력 보존 | 1단계부터의 accounts 커밋을 새 저장소에서도 그대로 볼 수 있게 한다 |

**순서를 배포 준비보다 앞에 둔 이유:** 분리는 코드를 거의 바꾸지 않는 짧은 작업이다(1단계부터 accounts 커밋을 따로 해 왔기 때문). 배포는 정할 것이 많으므로, 분리를 먼저 끝내고 배포에 집중한다.

**이 단계에서 하지 않는 것:** 기능 변경, 배포. 원격 저장소로 push하는 일은 사용자가 한다.

## 2. 분리 전 점검 결과
| 점검 | 결과 |
|---|---|
| accounts를 건드린 커밋 | 12개 |
| accounts와 바깥 파일을 함께 바꾼 커밋 | 1개(`2e1799a`). accounts README와 노트 3개가 함께 들어갔다. 노트를 옮기며 `git mv`로 스테이징해 둔 것이 섞였다([3단계 트러블슈팅 3](./09_step3_blog_frontend.md)과 같은 원인). `git subtree split`은 `accounts/` 안의 변경만 가져가므로 분리에는 영향이 없다 |
| accounts 안에서 바깥을 가리키는 곳 | `accounts/README.md`의 노트 링크(`../note/...`) 1곳 |
| 빌드 설정 | `accounts/backend`에 자체 `settings.gradle`, `build.gradle`, `gradlew`, `.gitignore`. `accounts/frontend`에 자체 `package.json`, `.gitignore` |
| 새 저장소 맨 위의 `.gitignore` | 없음. blog 저장소 맨 위에도 없어서 기대던 설정은 없다. IDE 파일(`.idea/` 등)용으로 하나 둔다 |

## 3. 세부 단계와 수행 방안

### 4-1. 분리 전 정리 (blog 저장소, accounts 안의 파일만 커밋)
- `accounts/README.md`의 노트 링크를 blog 저장소의 GitHub 주소로 바꾼다. 분리하면 `../note`가 없기 때문이다.
- `accounts/.gitignore`를 만든다. 새 저장소 맨 위에서 IntelliJ 설정(`.idea/`, `*.iml`), `.DS_Store`, `.vscode/`를 무시한다.

### 4-2. accounts 이력만 뽑기
```bash
cd blog
git subtree split --prefix=accounts -b accounts-only
```

> **`git subtree split`이란?**
> 저장소 이력에서 한 폴더(`--prefix`)에 관련된 커밋만 골라, 그 폴더를 맨 위로 올린 새 이력을 만드는 명령이다. 결과는 새 브랜치(`-b`)로 남는다. `accounts/backend/build.gradle`은 새 이력에서 `backend/build.gradle`이 된다. 커밋 내용(작성자, 날짜, 메시지)은 같지만, 내용이 달라지므로 커밋 해시(`d525572` 같은 값)는 새로 바뀐다.

### 4-3. 새 저장소로 옮기기
```bash
cd ../accounts          # 비어 있는 새 저장소 (클론해 둔 것)
git pull ../blog accounts-only
```
빈 저장소의 `main`에 `accounts-only`의 이력이 그대로 들어온다. 확인할 것: 맨 위에 `backend/`, `frontend/`, `README.md`, `.gitignore`. 커밋 수가 blog의 accounts 커밋 수와 같다.

### 4-4. 새 저장소에서 혼자 동작하는지 확인
- Git에서 제외된 로컬 설정 `backend/src/main/resources/application.yml`을 새 위치로 복사한다(새 저장소에서도 Git 제외).
- backend: `./gradlew test` (37개 통과해야 함).
- frontend: 다른 폴더에 복사해 `npm ci`, `npm run lint`, `npm run build`.

### 4-5. blog에서 accounts 지우기
- `git rm -r accounts`로 지우고, blog의 README에서 `../accounts` 링크를 새 저장소 주소로 바꾼다.
- Git에서 제외되어 남는 파일(`application.yml`, `build/`)은 4-4에서 새 위치로 옮겼으므로 폴더째 지운다.
- 분리용 브랜치(`accounts-only`)를 지운다.
- blog의 노트에 적힌 accounts 커밋 해시는 blog 저장소 이력에 그대로 남아 있으므로 바꾸지 않는다.

### 4-6. 기록
이 노트에 결과를 적고, 로드맵의 단계를 고친다(4단계 저장소 분리, 5단계 배포 준비).

**push:** 새 저장소의 `main`과 blog 저장소의 커밋은 사용자가 push한다.
