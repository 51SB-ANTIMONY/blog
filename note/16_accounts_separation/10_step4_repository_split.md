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

## 4. 결과

### 커밋
| 저장소 | 커밋 | 내용 |
|---|---|---|
| blog | `943f9d4` | 4단계 계획 노트, 로드맵 단계 정리 |
| blog | `e6ff23d` | 4-1: accounts README 링크를 GitHub 주소로, `accounts/.gitignore` 추가 (accounts 안의 파일만) |
| accounts | 13개 커밋, 마지막 `3e9ec66` | 4-2·4-3: `git subtree split`으로 뽑은 이력 |
| blog | `801ee07` | 4-5: `accounts/` 삭제(파일 109개), blog README의 accounts 링크를 새 저장소 주소로 |

### 확인한 것
| 확인 | 결과 |
|---|---|
| 뽑은 이력의 커밋 수 | 13개 = blog에서 accounts를 건드린 커밋 12개 + 4-1 커밋 1개 |
| 뽑은 이력의 마지막 내용 | `git diff HEAD:accounts accounts-only` 차이 없음 |
| 작성자, 메시지 | 13개 모두 사용자 본인. 커밋 메시지는 blog 저장소와 같음 |
| 새 저장소 맨 위 | `.gitignore`, `README.md`, `backend/`, `frontend/` |
| `gradlew` 실행 권한 | `100755` 그대로 |
| backend 테스트 | 37개 통과 (새 저장소 위치에서) |
| frontend | Git에 올라간 파일만 다른 폴더에 복사해 `npm ci`, `npm run lint`, `npm run build` 통과 |
| blog 노트·README의 링크 | 깨진 링크 없음 |

### 새 저장소로 옮긴 로컬 파일 (Git 제외)
| 파일 | 처리 |
|---|---|
| `backend/src/main/resources/application.yml` | 새 저장소에 복사. 내용이 같은지 `cmp`로 확인 |
| `backend/.idea/` | IntelliJ 설정이라 새 저장소에 복사 |
| `backend/.gradle/`, `backend/build/` | 다시 생기는 파일이라 지움 |

## 5. 트러블슈팅

### 1. `accounts/backend/.gradle`의 잠금 파일을 지우지 못했다
- **문제**: blog에서 `accounts/` 폴더를 지울 때 `.gradle/` 안의 `.lock`, `.bin` 파일 8개가 `Permission denied`로 남았다.
- **원인**: Windows의 IntelliJ(또는 Gradle 데몬)가 이 프로젝트를 열어 두고 있어서 파일을 잠그고 있었다. WSL에서는 Windows가 잠근 파일을 지울 수 없다.
- **해결**: Git이 추적하는 파일의 삭제는 정상적으로 커밋됐다. 남은 폴더(`blog/accounts/`)는 Git이 추적하지 않는 파일뿐이므로, IntelliJ에서 이 프로젝트를 닫은 뒤 지우면 된다. 그 전까지 `git status`에 `?? accounts/`로 보인다.

### 2. 바깥 파일이 섞인 커밋이 이미 있었다
- **문제**: 분리 전 점검에서 `2e1799a`(accounts README 커밋)에 노트 파일 3개가 함께 들어간 것을 찾았다.
- **원인**: 노트를 새 폴더로 옮기며 `git mv`로 스테이징해 둔 상태에서 `git add accounts/README.md` 후 커밋했다. 스테이징된 모든 변경이 함께 커밋됐다([3단계 트러블슈팅 3](./09_step3_blog_frontend.md)과 같은 원인).
- **영향**: 없다. `git subtree split`은 `accounts/` 안의 변경만 가져가므로, 새 저장소의 같은 커밋(`05d9e89`)에는 README만 들어 있다. 이미 push한 blog 이력은 고치지 않았다.

## 6. 남은 것
| 남은 것 | 하는 사람 |
|---|---|
| 새 저장소 `main` push (`cd accounts`, `git push -u origin main`) | 사용자 |
| blog 저장소 push | 사용자 |
| `blog/accounts/` 남은 폴더 삭제 (IntelliJ를 닫은 뒤) | 사용자 |
| IntelliJ에서 새 위치(`Desktop/accounts/backend`)를 프로젝트로 열기 | 사용자 |
