---
title: accounts 분리하기 - 8단계 Lightsail로 옮기기 (계획)
date: 2026-10-22
tags: [deployment, aws, lightsail, mysql, nginx, systemd, cicd]
---

# accounts 분리하기 - 8단계 Lightsail로 옮기기 (계획)

> 전체 단계는 [로드맵](./01_roadmap.md)에, 지금 운영 구성은 [6단계](./13_step6_deployment_plan.md)와 [CI/CD](./14_step6_cicd.md)에 있다. 이 단계는 accounts 저장소와 blog 저장소 모두에서 진행한다.

## 1. 목적
**AWS 요금을 줄인다.** Spring 서버 2개(accounts, blog)와 MySQL을 **Lightsail 서버 1대**로 옮긴다. 화면(Vercel)과 도메인, DNS(Cloudflare)는 그대로 둔다.

| 항목 | 지금 (6단계) | 옮긴 뒤 |
|---|---|---|
| accounts Spring | Elastic Beanstalk 단일 인스턴스 (`t3.micro`) | Lightsail 서버 1대 안에서 함께 실행 |
| blog Spring | Elastic Beanstalk 단일 인스턴스 (`t3.micro`) | 〃 |
| DB | RDS MySQL (`db.t4g.micro`) | 같은 서버 안의 MySQL |
| 화면 2개 | Vercel | 그대로 |
| DNS, HTTPS | Cloudflare 프록시 + 원본 인증서 | 그대로 (가리키는 곳만 바꿈) |
| 배포 | GitHub Actions → Elastic Beanstalk | GitHub Actions → SSH로 JAR 올리고 재시작 |
| 월 비용 | 약 4만 원 이상 (EC2 2대, 공인 IPv4 2개, RDS) | **$12 (약 1.7만 원)** + 스냅샷 소액 |

> **Lightsail이란?**
> AWS의 간단한 서버 상품이다. 서버(CPU, 메모리), SSD 디스크, 월 데이터 전송량, 고정 IP를 하나로 묶어 월 고정 요금으로 받는다. EC2처럼 디스크, 트래픽, IP 요금이 따로 붙지 않는다.

**이 단계에서 하지 않는 것:** Docker 도입, 화면(Vercel) 이전, 이미지 업로드(S3), 코드 기능 변경.

## 2. 정한 것
| 질문 | 결정 | 이유 |
|---|---|---|
| 서버 수 | **1대**에 accounts, blog, MySQL 모두 | blog 로그인은 어차피 accounts에 의존한다. 나눠도 함께 멈추고, 비용만 두 배가 된다 |
| 요금제 | **$12 (2GB 메모리, 2 vCPU, 60GB SSD, 월 3TB 전송)**, 공인 IPv4 포함 | Spring 2개 + MySQL에 약 1.3GB가 필요하다. $7(1GB)는 부족하다 |
| 리전 | 서울 (`ap-northeast-2`) | 지금과 같게 |
| OS | **Ubuntu 24.04 LTS** | 자료가 많고 2029년까지 보안 업데이트 |
| Java | **Amazon Corretto 25** | CI(`setup-java`)와 같은 배포판 |
| MySQL | **MySQL 8.4 LTS** (MySQL 공식 APT 저장소) | 지금 RDS가 8.4다. Ubuntu 기본 저장소는 8.0이라, 8.4에서 뺀 데이터를 넣으면 버전이 낮아진다 |
| 앱 실행 | **systemd** 서비스 2개 (`blog`, `accounts`). Docker는 쓰지 않는다 | 5단계에서 정한 대로 JAR를 그대로 실행한다. 한쪽을 재시작해도 다른 쪽은 계속 동작한다 |
| 앞단 | **Nginx 1개**가 주소(`blog-api`, `accounts-api`)를 보고 포트를 나눈다 | 6단계의 Nginx 설정을 거의 그대로 쓴다 |
| 포트 | blog `8080`, accounts `9000` (로컬과 같게). **127.0.0.1에서만** 받는다 | 바깥에서는 Nginx(443)로만 들어오게 |
| blog → accounts 서버끼리 호출 | `ACCOUNTS_BACKCHANNEL_URI=http://127.0.0.1:9000` | 같은 서버라 인터넷과 Cloudflare를 거치지 않는다 |
| DB 계정 | 앱마다 따로 (`blog_app`, `accounts_app`), 자기 DB에만 권한 | 지금은 두 앱이 `admin` 하나를 쓴다. 한쪽이 털려도 다른 쪽 데이터는 못 보게 |
| 각 저장소에 둘 것 | 자기 앱의 systemd 유닛, Nginx 설정, 배포 워크플로 (`backend/deploy/`) | 두 저장소는 설정으로만 연결한다는 원칙 그대로 |
| 서버 전체 설정 | 스왑, MySQL, 방화벽, 백업은 **이 노트에만** 적는다 | 어느 한 저장소의 것이 아니므로 |

### 메모리 계획 (2GB)
| 항목 | 상한 |
|---|---|
| OS + Nginx | 약 200MB |
| blog JVM | 힙 320MB (프로세스 전체 약 450MB) |
| accounts JVM | 힙 320MB (프로세스 전체 약 450MB) |
| MySQL | 버퍼 풀 128MB (프로세스 전체 약 350MB) |
| **합계** | **약 1.45GB** + 스왑 2GB |

> **JVM 힙(`-Xmx`)이란?**
> Java 프로그램이 객체를 만들 때 쓰는 메모리 공간이다. `-Xmx320m`은 최대 320MB까지만 쓰라는 뜻이다. 정하지 않으면 서버 메모리의 1/4까지 잡는다. 프로세스 전체 메모리는 힙 외에 클래스 정보(Metaspace), 스레드 스택 등이 더해져 힙보다 크다.

> **스왑(Swap)이란?**
> RAM이 부족할 때 디스크 일부를 메모리처럼 쓰는 공간이다. RAM보다 훨씬 느리지만, 메모리가 잠깐 모자랄 때 리눅스가 프로그램을 강제로 종료하는 것(OOM Killer)을 막는다.

### 옮겨도 바뀌지 않는 것
5단계에서 **도메인 기준으로 설정**하고 환경 변수로만 값을 받게 했기 때문에, 아래는 고치지 않는다.

- issuer `https://accounts.antimony.kr`, redirect_uri, Google의 승인된 리디렉션 URI
- Vercel 환경 변수 `ACCOUNTS_BACKEND_URL`, `BLOG_BACKEND_URL` (주소가 같으므로)
- accounts 서명 키(`SIGNING_KEY_BASE64`), `REMEMBER_ME_KEY`, Client Secret (같은 값을 옮긴다. 바꾸면 발급한 토큰과 로그인 상태 유지가 모두 끊긴다)
- Cloudflare 원본 인증서 (같은 파일을 새 서버에 둔다)

## 3. 구성
```
브라우저 ─HTTPS─▶ accounts.antimony.kr, blog.antimony.kr          Vercel (그대로)
                        │
                        ▼ HTTPS
                  accounts-api.antimony.kr, blog-api.antimony.kr     Cloudflare 프록시 (그대로)
                        │ HTTPS (원본 인증서)
                        ▼
  ┌──────────── Lightsail 서버 1대 (고정 IP) ─────────────┐
  │  Nginx :443                                          │
  │    ├─ Host: accounts-api.antimony.kr ─▶ 127.0.0.1:9000  accounts (systemd)
  │    └─ Host: blog-api.antimony.kr     ─▶ 127.0.0.1:8080  blog (systemd)
  │                                          │
  │  blog ── 토큰 교환, 공개 키 ─▶ 127.0.0.1:9000 (accounts)
  │                                          ▼
  │  MySQL 8.4 :3306 (127.0.0.1)   DB: accounts, blog
  └──────────────────────────────────────────────────────┘
```

| 서버 안 위치 | 내용 | 소유자, 권한 |
|---|---|---|
| `/opt/blog/releases/blog-<커밋>.jar` | 배포한 JAR들 (최근 3개 보관) | `deploy` |
| `/opt/blog/app.jar` | 지금 실행할 JAR를 가리키는 심볼릭 링크 | `deploy` |
| `/etc/antimony/blog.env` | blog 환경 변수 (비밀 값 포함) | `root`, `600` |
| `/etc/systemd/system/blog.service` | blog 서비스 정의 | `root` |
| `/etc/nginx/conf.d/blog-api.conf` | blog 주소의 Nginx 설정 | `root` |
| `/etc/ssl/cloudflare/origin.crt`, `origin.key` | Cloudflare 원본 인증서와 개인 키 | `root`, 키는 `600` |
| `/var/backups/mysql/` | 매일 만드는 DB 백업 파일 | `root` |

accounts도 `blog` 자리에 `accounts`를 넣은 같은 구조다.

> **심볼릭 링크(Symbolic Link)란?**
> 다른 파일을 가리키는 바로가기 파일이다. `app.jar`가 `releases/blog-abc123.jar`를 가리키게 해 두면, 새 버전을 배포할 때 링크만 바꾸면 된다. 문제가 생기면 이전 JAR로 링크를 되돌려 바로 복구할 수 있다.

## 4. 세부 단계
| 단계 | 내용 | 저장소 |
|---|---|---|
| 8-1 | Lightsail 서버 만들기: 인스턴스, 고정 IP, 방화벽, 스왑, 사용자 | (서버) |
| 8-2 | 프로그램 설치: Corretto 25, MySQL 8.4, Nginx | (서버) |
| 8-3 | MySQL 설정: 메모리 줄이기, DB와 앱별 계정 | (서버) |
| 8-4 | 앱 실행 설정: 환경 변수 파일, systemd 유닛 | 두 저장소 `backend/deploy/` |
| 8-5 | Nginx와 원본 인증서 | 두 저장소 `backend/deploy/` |
| 8-6 | 빈 DB로 첫 실행, DNS를 바꾸기 전에 확인 | (서버) |
| 8-7 | 데이터 옮기기와 DNS 전환 | (서버, Cloudflare) |
| 8-8 | CI/CD를 SSH 배포로 바꾸기 | 두 저장소 |
| 8-9 | 백업과 모니터링 | (서버) |
| 8-10 | 정리: Elastic Beanstalk, RDS 삭제, EB 전용 파일 제거 | AWS, 두 저장소 |
| 8-11 | 기록 | blog `note/` |

## 5. 수행 방안

### 8-1. Lightsail 서버 만들기
1. Lightsail 콘솔 → **인스턴스 생성** → 리전 서울, 플랫폼 **Linux/Unix**, 블루프린트 **OS 전용 → Ubuntu 24.04 LTS**.
2. SSH 키 페어: 새로 만들어 내려받는다(`antimony-lightsail.pem`). Git 밖에 둔다.
3. 요금제: **듀얼 스택(IPv4 포함) $12**. 이름 `antimony-api`.
4. **네트워킹 → 고정 IP 생성** → 이 인스턴스에 연결. 이 IP를 적어 둔다. 연결해 두면 무료다(연결하지 않은 고정 IP는 요금이 나온다).
5. **네트워킹 → IPv4 방화벽**:

| 포트 | 허용 대상 | 이유 |
|---|---|---|
| 22 (SSH) | 모두 | GitHub Actions 배포 서버의 IP가 매번 바뀐다. 비밀번호 로그인을 끄고 키로만 접속한다 |
| 443 (HTTPS) | 모두 (나중에 Cloudflare IP 대역만으로 좁히기) | Cloudflare 프록시가 들어오는 길 |
| 80 (HTTP) | **삭제** | 기본으로 열려 있다. Cloudflare가 HTTPS로만 접속하므로 필요 없다 |

> **고정 IP(Static IP)란?**
> 서버를 껐다 켜도 바뀌지 않는 공인 IP 주소다. Lightsail 인스턴스의 기본 IP는 중지했다 켜면 바뀔 수 있어서, DNS가 가리킬 IP는 고정 IP로 만든다.

> **방화벽(Firewall)이란?**
> 서버로 들어오는 연결을 포트 번호와 출발지 IP로 허용하거나 막는 설정이다. 6단계의 EC2 보안 그룹과 같은 역할이다.

접속한 뒤 기본 설정:
```bash
ssh -i antimony-lightsail.pem ubuntu@<고정 IP>

# 시간대, 패키지 업데이트, 보안 업데이트 자동 설치
sudo timedatectl set-timezone Asia/Seoul
sudo apt update && sudo apt upgrade -y
sudo apt install -y unattended-upgrades

# 스왑 2GB
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swap.conf   # RAM이 정말 부족할 때만 스왑을 쓰게

# 앱 실행용 사용자 (로그인 불가), 배포용 사용자
sudo useradd --system --no-create-home --shell /usr/sbin/nologin blog
sudo useradd --system --no-create-home --shell /usr/sbin/nologin accounts
sudo useradd --create-home --shell /bin/bash deploy
```

> **`vm.swappiness`란?**
> 리눅스가 스왑을 얼마나 적극적으로 쓸지 정하는 값(0~100)이다. 기본 60이면 RAM에 여유가 있어도 스왑을 쓰기 시작해 느려질 수 있다. 10이면 RAM이 거의 찼을 때만 쓴다.

`deploy` 사용자는 GitHub Actions가 접속할 계정이다. 이 계정은 JAR를 올리고 두 서비스를 재시작하는 것**만** 할 수 있게 한다.
```bash
# /etc/sudoers.d/deploy (sudo visudo -f /etc/sudoers.d/deploy 로 편집)
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart blog, /usr/bin/systemctl restart accounts
```

> **sudoers란?**
> 어떤 사용자가 `sudo`로 어떤 명령을 관리자 권한으로 실행할 수 있는지 적는 설정이다. 위 줄은 `deploy`가 비밀번호 없이 두 서비스 재시작만 할 수 있다는 뜻이다. `visudo`로 편집하면 문법이 틀렸을 때 저장을 막아 준다.

### 8-2. 프로그램 설치
```bash
# Amazon Corretto 25 (Corretto 공식 APT 저장소)
wget -O - https://apt.corretto.aws/corretto.key | sudo gpg --dearmor -o /usr/share/keyrings/corretto-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" \
  | sudo tee /etc/apt/sources.list.d/corretto.list
sudo apt update && sudo apt install -y java-25-amazon-corretto-jdk
java -version   # 25 확인

# Nginx
sudo apt install -y nginx
sudo rm /etc/nginx/sites-enabled/default   # 기본 80 포트 페이지 끄기
```

MySQL 8.4는 MySQL 공식 APT 저장소에서 설치한다.
1. https://dev.mysql.com/downloads/repo/apt/ 에서 `mysql-apt-config_*.deb`를 내려받는다.
2. `sudo dpkg -i mysql-apt-config_*.deb` → 선택 화면에서 **MySQL Server & Cluster → mysql-8.4-lts** → OK.
3. `sudo apt update && sudo apt install -y mysql-server` (root 비밀번호를 묻는다. 비밀 값 파일에 적어 둔다).
4. `mysql --version`으로 8.4 확인.

> **APT 저장소란?**
> Ubuntu가 프로그램을 내려받는 곳이다. 기본 저장소에 없는 버전(Corretto, MySQL 8.4)은 만든 회사의 저장소를 추가하고, 그 회사의 서명 키(`gpg`)로 내려받은 파일이 진짜인지 확인한다.

### 8-3. MySQL 설정
메모리를 줄이고, 바깥에서는 접속하지 못하게 한다.
```ini
# /etc/mysql/mysql.conf.d/zz-small.cnf
[mysqld]
bind-address            = 127.0.0.1   # 같은 서버 안에서만 접속
innodb_buffer_pool_size = 128M        # 기본값도 128M이지만 명시해 둔다
performance_schema      = OFF         # 성능 통계 기능. 끄면 메모리 약 200MB 절약
max_connections         = 60          # 앱 2개 × 커넥션 풀 10개 + 여유
```
```bash
sudo systemctl restart mysql
```

> **`innodb_buffer_pool_size`란?**
> MySQL이 테이블 데이터와 인덱스를 메모리에 올려 두는 공간의 크기다. 클수록 디스크를 덜 읽어 빠르다. 지금 데이터는 수 MB 수준이라 128MB면 전부 메모리에 들어간다.

> **커넥션 풀(Connection Pool)이란?**
> 앱이 DB 연결을 미리 몇 개 만들어 두고 돌려 쓰는 것이다. Spring Boot의 기본 커넥션 풀(HikariCP)은 최대 10개를 만든다.

DB와 앱별 계정:
```sql
-- sudo mysql
CREATE DATABASE accounts CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE blog     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'accounts_app'@'localhost' IDENTIFIED BY '<새 비밀번호 1>';
CREATE USER 'blog_app'@'localhost'     IDENTIFIED BY '<새 비밀번호 2>';

-- 자기 DB 안에서만 모든 권한 (Flyway가 테이블을 만들고 바꿔야 하므로)
GRANT ALL PRIVILEGES ON accounts.* TO 'accounts_app'@'localhost';
GRANT ALL PRIVILEGES ON blog.*     TO 'blog_app'@'localhost';
```
`'user'@'localhost'`는 이 서버 안에서 접속할 때만 이 계정을 쓸 수 있다는 뜻이다. 새 비밀번호는 비밀 값 파일에 추가한다.

### 8-4. 앱 실행 설정 (systemd)
각 저장소에 `backend/deploy/` 폴더를 만들고 서버에 둘 파일의 원본을 넣는다. 비밀 값이 들어가는 환경 변수 파일은 **예시만** 저장소에 둔다.

| 파일 (blog 저장소 기준) | 서버 위치 |
|---|---|
| `backend/deploy/blog.service` | `/etc/systemd/system/blog.service` |
| `backend/deploy/blog-api.conf` | `/etc/nginx/conf.d/blog-api.conf` |
| `backend/deploy/blog.env.example` | `/etc/antimony/blog.env` (값을 채워서) |

```ini
# backend/deploy/blog.service
[Unit]
Description=blog API (Spring Boot)
After=network.target mysql.service
Requires=mysql.service

[Service]
User=blog
EnvironmentFile=/etc/antimony/blog.env
ExecStart=/usr/bin/java -Xms128m -Xmx320m -XX:MaxMetaspaceSize=160m -Xss512k -jar /opt/blog/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

> **systemd란?**
> 리눅스에서 프로그램을 백그라운드 서비스로 실행하고 관리하는 도구다. 서버가 재부팅되면 서비스를 자동으로 켜고(`WantedBy=multi-user.target`), 프로그램이 비정상 종료되면 다시 켠다(`Restart=on-failure`). Elastic Beanstalk이 대신 해 주던 일이다.

> **유닛 파일(Unit File)이란?**
> systemd 서비스 하나를 정의하는 설정 파일이다. `[Unit]`은 실행 순서(MySQL이 켜진 뒤에), `[Service]`는 실행할 명령과 사용자, `[Install]`은 부팅할 때 켤지를 정한다.

> **`SuccessExitStatus=143`이란?**
> systemd가 서비스를 멈출 때 보내는 종료 신호(SIGTERM)를 받으면 Java는 종료 코드 143으로 끝난다. 이것을 정상 종료로 보라는 설정이다. 없으면 재시작할 때마다 "실패"로 기록된다.

```bash
# backend/deploy/blog.env.example → 서버의 /etc/antimony/blog.env (root, 600)
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
PORT=8080
DB_URL=jdbc:mysql://127.0.0.1:3306/blog?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_USERNAME=blog_app
DB_PASSWORD=
ACCOUNTS_ISSUER=https://accounts.antimony.kr
ACCOUNTS_BACKCHANNEL_URI=http://127.0.0.1:9000
ACCOUNTS_CLIENT_SECRET=
```
accounts는 6단계 ③의 표와 같은 변수에 `SERVER_ADDRESS=127.0.0.1`, `PORT=9000`, `DB_USERNAME=accounts_app`을 넣고, `TLS_CERT_S3_URI`는 뺀다. `DB_URL`의 DB가 이미 있으므로 `createDatabaseIfNotExist=true`도 뺀다.

> **`SERVER_ADDRESS`란?**
> Spring의 `server.address` 설정이다. `127.0.0.1`(이 서버 자신을 뜻하는 주소)로 정하면 같은 서버 안에서 온 연결만 받는다. 방화벽 설정을 실수로 열어도 8080, 9000으로는 바깥에서 들어올 수 없다.

> **`EnvironmentFile`이란?**
> systemd가 서비스를 켤 때 환경 변수를 읽어 올 파일이다. 파일을 root만 읽을 수 있게(`600`) 해도, systemd가 root 권한으로 읽은 뒤 `blog` 사용자로 앱을 실행하므로 앱은 값을 받는다.

서버에 적용:
```bash
sudo mkdir -p /etc/antimony /opt/blog/releases /opt/accounts/releases
sudo chown -R deploy:deploy /opt/blog /opt/accounts
sudo cp blog.service accounts.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable blog accounts   # 부팅할 때 켜기 (아직 시작은 하지 않음)
```

### 8-5. Nginx와 원본 인증서
6단계에서 만든 Cloudflare 원본 인증서(`origin.crt`, `origin.key`)를 그대로 쓴다. S3 콘솔의 `elasticbeanstalk-ap-northeast-2-<계정 ID>/tls/origin/`에서 내려받아 서버로 복사한다.
```bash
scp -i antimony-lightsail.pem origin.crt origin.key ubuntu@<고정 IP>:/tmp/
# 서버에서
sudo mkdir -p /etc/ssl/cloudflare
sudo mv /tmp/origin.crt /tmp/origin.key /etc/ssl/cloudflare/
sudo chmod 644 /etc/ssl/cloudflare/origin.crt
sudo chmod 600 /etc/ssl/cloudflare/origin.key
```
내려받은 개인 키는 복사한 뒤 내 컴퓨터에서 지운다.

저장소마다 자기 주소의 `server` 블록만 둔다. 6단계 `https.conf`와 다른 점은 **`server_name`으로 주소를 구분**하고, **넘기는 포트**가 다르다는 것뿐이다.
```nginx
# backend/deploy/blog-api.conf
# Forwarded: 6단계 트러블슈팅 4와 같은 이유로 공개 주소를 고정한다 (Vercel이 원래 주소를 넘기지 않음).
server {
    listen 443 ssl;
    http2 on;
    server_name blog-api.antimony.kr;

    ssl_certificate     /etc/ssl/cloudflare/origin.crt;
    ssl_certificate_key /etc/ssl/cloudflare/origin.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Connection "";
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto https;
        proxy_set_header   Forwarded "host=blog.antimony.kr;proto=https";
    }
}
```
accounts는 `server_name accounts-api.antimony.kr`, `proxy_pass http://127.0.0.1:9000`, `Forwarded "host=accounts.antimony.kr;proto=https"`로 바꾼다. `Forwarded`의 `host`는 API 주소가 아니라 **화면 주소**다(6단계와 같은 값).

모르는 주소로 들어온 요청은 거절하는 기본 서버(서버 전체 설정이라 이 노트에만 둔다):
```nginx
# /etc/nginx/conf.d/00-default.conf
server {
    listen 443 ssl default_server;
    ssl_reject_handshake on;   # IP로 직접 접속하거나 다른 주소로 오면 HTTPS 연결 자체를 거절
}
```
```bash
sudo nginx -t && sudo systemctl reload nginx
```

> **`server_name`이란?**
> Nginx가 요청의 `Host` 헤더(사용자가 접속한 주소)를 보고 어느 `server` 블록으로 처리할지 고르는 기준이다. 서버 하나에서 `blog-api`와 `accounts-api` 두 주소를 받을 수 있는 이유다.

### 8-6. 빈 DB로 첫 실행, DNS 바꾸기 전에 확인
DNS를 바꾸기 전에, **빈 DB로** 새 서버가 제대로 뜨는지 먼저 본다.
```bash
# 내 컴퓨터에서 JAR 만들어 올리기 (blog, accounts 각각)
./gradlew bootJar
scp -i antimony-lightsail.pem build/libs/blog-0.0.1-SNAPSHOT.jar deploy@<고정 IP>:/opt/blog/releases/blog-manual.jar

# 서버에서
ln -sfn /opt/blog/releases/blog-manual.jar /opt/blog/app.jar
sudo systemctl start accounts blog
journalctl -u accounts -f        # "Tomcat started on port 9000" 확인 (Ctrl+C로 나가기)
curl -s localhost:9000/actuator/health   # {"status":"UP"}
curl -s localhost:8080/actuator/health
```
`deploy` 사용자로 SSH 접속하려면 `ubuntu` 사용자의 `~/.ssh/authorized_keys`를 `deploy`에게도 복사해 둔다(8-8에서 배포 전용 키로 바꾼다).

Flyway가 빈 DB에 테이블을 만든 것도 확인한다. 확인이 끝나면 **서비스를 멈추고 DB를 비운다**(8-7에서 실제 데이터를 넣는다).
```bash
sudo systemctl stop blog accounts
sudo mysql -e "DROP DATABASE blog; DROP DATABASE accounts; CREATE DATABASE blog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE accounts CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

DNS를 바꾸지 않고 바깥에서 Nginx까지 확인(내 컴퓨터에서, 인증서는 Cloudflare만 믿으므로 `-k`):
```bash
curl -k --resolve blog-api.antimony.kr:443:<고정 IP> https://blog-api.antimony.kr/actuator/health
```

> **`journalctl`이란?**
> systemd가 모아 둔 서비스 로그를 보는 명령이다. `-u blog`는 blog 서비스의 로그만, `-f`는 새로 쌓이는 로그를 계속 보여 준다. Elastic Beanstalk의 "로그 요청"을 대신한다.

> **`--resolve`란?**
> curl이 DNS를 묻지 않고, 지정한 주소를 지정한 IP로 접속하게 하는 옵션이다. DNS를 바꾸기 전에 새 서버를 실제 주소 이름으로 시험할 수 있다.

### 8-7. 데이터 옮기기와 DNS 전환
RDS는 퍼블릭 액세스가 꺼져 있어 바깥에서 접속할 수 없다. **잠깐만** 새 서버의 고정 IP에서 오는 접속을 허용해 옮긴다.

1. RDS → 데이터베이스 → 수정 → **퍼블릭 액세스 가능: 예** → 즉시 적용.
2. RDS 보안 그룹에 인바운드 규칙 추가: MySQL/Aurora(3306), 소스 **`<고정 IP>/32`** (이 IP 하나만).
3. 새 서버에서 RDS의 데이터를 바로 받아 로컬 MySQL에 넣는다.
   ```bash
   mysqldump -h <RDS 엔드포인트> -u admin -p \
     --single-transaction --set-gtid-purged=OFF --no-tablespaces \
     --databases accounts blog > /tmp/rds-final.sql
   sudo mysql < /tmp/rds-final.sql
   ```
4. 두 서비스를 켜고 health와 로그(`journalctl`)를 본다. Flyway는 `flyway_schema_history`도 함께 옮겨졌으므로 이미 적용된 스크립트를 다시 실행하지 않는다.
5. **Cloudflare DNS 전환**:

| 이름 | 지금 | 바꾼 뒤 | 프록시 |
|---|---|---|---|
| `accounts-api` | CNAME → `xxx.elasticbeanstalk.com` | **A → `<고정 IP>`** | 켬 (그대로) |
| `blog-api` | CNAME → `xxx.elasticbeanstalk.com` | **A → `<고정 IP>`** | 켬 (그대로) |

6. 6단계 [5. 배포 뒤 확인](./13_step6_deployment_plan.md#5-배포-뒤-확인)의 표를 다시 돈다. 특히 **기존 회원으로 로그인**, 기존 글 보이기, 로그인 상태 유지(세션 테이블도 옮겼으므로 기존 로그인이 유지되는지).
7. RDS의 퍼블릭 액세스를 다시 **아니요**로 바꾸고, 2번에서 추가한 규칙을 지운다.
8. `/tmp/rds-final.sql`은 `/var/backups/mysql/`로 옮겨 첫 백업으로 둔다.

> **mysqldump란?**
> MySQL의 데이터를 SQL 문(`CREATE TABLE`, `INSERT`) 파일로 내보내는 명령이다. 이 파일을 다른 MySQL에서 실행하면 같은 데이터가 만들어진다.
> - `--single-transaction`: 내보내는 동안 테이블을 잠그지 않고, 시작 시점의 데이터를 일관되게 내보낸다.
> - `--set-gtid-purged=OFF`: RDS의 복제용 기록(GTID)을 빼서, 새 MySQL에서 오류가 나지 않게 한다.
> - `--no-tablespaces`: RDS의 `admin`에게 없는 권한(PROCESS)이 필요한 정보를 뺀다.
> - `--databases`: 파일 안에 `CREATE DATABASE`, `USE`를 넣는다. 그래서 받는 쪽에서 DB 이름을 따로 정하지 않는다.

> **A 레코드, CNAME 레코드란?**
> 둘 다 DNS 설정이다. A 레코드는 "이 이름 → 이 IP 주소", CNAME은 "이 이름 → 다른 이름"이다. Elastic Beanstalk은 IP가 바뀔 수 있어 이름(CNAME)을 가리켰고, Lightsail은 고정 IP라 A 레코드로 IP를 직접 가리킨다.

**멈추는 시간(다운타임):** 3번에서 6번까지 약 5~10분. 그 사이 옛 서버에 들어온 쓰기(새 글, 회원가입)는 옮겨지지 않는다. 사용자가 거의 없는 시간에 한다. Cloudflare 프록시를 켠 주소는 바깥에서 보이는 IP가 Cloudflare의 것이라 DNS 전환이 거의 바로 적용된다(DNS 캐시를 기다리지 않는다).

**되돌리기:** 8-10에서 Elastic Beanstalk을 지우기 전까지는, DNS를 원래 CNAME으로 바꾸기만 하면 옛 서버로 돌아간다. 단, 옮긴 뒤 새 서버에 쌓인 데이터는 옛 RDS에 없다.

### 8-8. CI/CD를 SSH 배포로 바꾸기
배포 전용 SSH 키를 만든다(내 컴퓨터에서, 비밀번호 없이).
```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f deploy_key -N ""
ssh-keyscan -t ed25519 <고정 IP>     # 출력을 LIGHTSAIL_KNOWN_HOSTS에 넣는다
```
- 공개 키 `deploy_key.pub` → 서버의 `/home/deploy/.ssh/authorized_keys`
- 저장소마다 GitHub → Settings → Secrets and variables → Actions:

| 이름 | 값 |
|---|---|
| `LIGHTSAIL_HOST` | 고정 IP |
| `LIGHTSAIL_SSH_KEY` | 개인 키 `deploy_key`의 내용 전체 |
| `LIGHTSAIL_KNOWN_HOSTS` | `ssh-keyscan`의 출력 |

> **known_hosts란?**
> 접속할 서버의 공개 키를 적어 두는 파일이다. SSH는 접속할 때 서버가 보낸 키가 이 파일의 키와 같은지 확인한다. 다르면 가짜 서버일 수 있어 접속을 거부한다. 미리 넣어 두지 않으면 CI에서 "이 서버를 믿겠습니까?" 질문에 답할 수 없어 멈춘다.

`build.gradle`에서 `bootJar` 이름을 고정한다(버전이 바뀌어도 워크플로를 고치지 않게).
```groovy
tasks.named('bootJar') {
    archiveFileName = "${project.name}.jar"
}
```

워크플로(`.github/workflows/cicd.yml`)의 `Beanstalk Deploy` 단계를 아래로 바꾼다(blog 기준, accounts는 이름과 포트 `9000`만 다르다).
```yaml
      - name: Test and build
        run: ./gradlew clean test bootJar

      - name: Deploy to Lightsail
        env:
          HOST: ${{ secrets.LIGHTSAIL_HOST }}
          SSH_KEY: ${{ secrets.LIGHTSAIL_SSH_KEY }}
          KNOWN_HOSTS: ${{ secrets.LIGHTSAIL_KNOWN_HOSTS }}
        run: |
          mkdir -p ~/.ssh
          echo "$SSH_KEY" > ~/.ssh/id_ed25519 && chmod 600 ~/.ssh/id_ed25519
          echo "$KNOWN_HOSTS" > ~/.ssh/known_hosts

          release=/opt/blog/releases/blog-${GITHUB_SHA}.jar
          scp build/libs/blog.jar deploy@$HOST:$release

          # 링크를 새 JAR로 바꾸고 재시작 → health가 UP이 될 때까지 최대 90초 기다린다 → 옛 JAR는 3개만 남긴다
          ssh deploy@$HOST "
            set -e
            ln -sfn $release /opt/blog/app.jar
            sudo systemctl restart blog
            for i in \$(seq 1 30); do
              curl -fs localhost:8080/actuator/health && break
              [ \$i = 30 ] && { echo 'health 확인 실패'; exit 1; }
              sleep 3
            done
            ls -t /opt/blog/releases/*.jar | tail -n +4 | xargs -r rm
          "
```
health가 UP이 되지 않으면 워크플로가 실패로 끝난다. 그때는 서버에서 이전 JAR로 링크를 되돌린다.
```bash
ls -t /opt/blog/releases/           # 두 번째 것이 이전 버전
ln -sfn /opt/blog/releases/<이전 JAR> /opt/blog/app.jar && sudo systemctl restart blog
```

새 워크플로로 한 번 배포가 성공하면 GitHub Secrets의 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`를 지우고, AWS IAM에서 그 액세스 키도 삭제한다.

### 8-9. 백업과 모니터링
RDS는 매일 자동 백업을 해 줬지만, 직접 설치한 MySQL은 백업을 직접 챙겨야 한다. 두 가지를 함께 한다.

| 백업 | 방법 | 되돌릴 수 있는 것 |
|---|---|---|
| 서버 전체 | Lightsail 인스턴스 → 스냅샷 → **자동 스냅샷 켜기** (매일, 최근 7개 보관). 쓰는 용량만큼 GB당 월 약 $0.05 | 서버가 망가졌을 때 통째로 새 인스턴스로 |
| DB만 | 매일 새벽 `mysqldump`, 7일 보관 | 실수로 지운 데이터만 골라서 |

```bash
# /etc/cron.d/mysql-backup
30 4 * * * root mysqldump --single-transaction --databases accounts blog | gzip > /var/backups/mysql/$(date +\%F).sql.gz && find /var/backups/mysql -name '*.sql.gz' -mtime +7 -delete
```
```bash
sudo mkdir -p /var/backups/mysql && sudo chmod 700 /var/backups/mysql
```

> **스냅샷(Snapshot)이란?**
> 어느 시점의 서버 디스크 전체를 저장해 둔 것이다. 이 스냅샷으로 같은 상태의 새 서버를 만들 수 있다. 더 큰 요금제로 옮길 때도 스냅샷으로 새 인스턴스를 만든다.

> **cron이란?**
> 정해진 시각에 명령을 자동으로 실행하는 리눅스 기능이다. `30 4 * * *`는 "매일 4시 30분"이다(분, 시, 일, 월, 요일 순서). cron 설정 안에서 `%`는 특수 문자라 `\%`로 쓴다.

모니터링 (Lightsail 인스턴스 → 지표 → 경보 추가, 알림은 이메일):

| 지표 | 경보 조건 | 의미 |
|---|---|---|
| CPU 사용률 | 30분 동안 평균 70% 이상 | 무언가 CPU를 계속 쓰고 있다 |
| 버스트 용량(%) | 20% 아래 | CPU 크레딧이 거의 바닥나 곧 느려진다 |
| 상태 확인 실패 | 1회 이상 | 서버가 응답하지 않는다 |

메모리는 Lightsail 지표에 없으므로 가끔 `free -h`로 본다. 스왑을 계속 쓰고 있으면 $24(4GB)로 올린다.

> **버스트 용량(Burst Capacity)이란?**
> Lightsail 서버는 평소 CPU를 기준치보다 적게 쓰면 크레딧을 모으고, 필요할 때 크레딧을 써서 기준치보다 높은 성능을 낸다. 버스트 용량은 남은 크레딧의 비율이다. 0%가 되면 기준치 성능으로 떨어진다. Spring 서버가 켜질 때 CPU를 많이 쓰지만 몇십 초라 크레딧으로 충분하다.

### 8-10. 정리
새 서버로 **일주일** 문제없이 운영한 뒤 지운다(그 전까지는 8-7의 되돌리기가 가능하다).

| 순서 | 할 일 | 비고 |
|---|---|---|
| 1 | Elastic Beanstalk → 환경 2개 **종료** | EC2, 공인 IP, 보안 그룹이 함께 지워진다 |
| 2 | Elastic Beanstalk → 애플리케이션 2개 삭제 | 저장된 애플리케이션 버전도 정리 |
| 3 | RDS → 데이터베이스 **삭제** | "최종 스냅샷 생성"은 하지 않는다(스냅샷도 요금이 나온다). 8-7의 `rds-final.sql`이 마지막 백업이다 |
| 4 | RDS → 자동 백업, 수동 스냅샷이 남았는지 확인하고 삭제 | |
| 5 | EC2 → 탄력적 IP, 남은 볼륨, 스냅샷 확인 | 연결되지 않은 탄력적 IP와 볼륨은 요금이 계속 나온다 |
| 6 | S3 `elasticbeanstalk-ap-northeast-2-<계정 ID>` 버킷 비우고 삭제 | 원본 인증서 사본도 함께 지워진다. 인증서는 서버와 비밀번호 관리자에 있다 |
| 7 | ACM 인증서 | 무료라 남겨도 되지만, 쓸 곳이 없으므로 삭제 |
| 8 | 다음 달 **Cost Explorer**에서 서비스별 요금 확인 | Lightsail 외에 남은 항목이 있는지 |

저장소에서 Elastic Beanstalk 전용 파일을 지운다(두 저장소 모두, 별도 커밋).
- `.ebextensions/`, `.platform/`, `Procfile`, `build.gradle`의 `ebBundle` 작업
- `application-prod.yml` 맨 위의 환경 변수 설명에서 `TLS_CERT_S3_URI`, `PORT=5000` 관련 내용
- README의 배포 구성과 아키텍처 그림

### 8-11. 기록
- 이 노트 아래에 결과, 실제 메모리 사용량(`free -h`, `systemctl status`), 겪은 문제를 남긴다.
- [로드맵](./01_roadmap.md)의 표와 문서 목록에 8단계를 추가한다.
- 두 저장소 README의 배포 절을 Lightsail 기준으로 고친다.

## 6. 비용 (대략, 서울 리전)
| 항목 | 월 비용 |
|---|---|
| Lightsail $12 요금제 (고정 IP, 월 3TB 전송 포함) | $12 (약 1.7만 원) |
| 자동 스냅샷 (사용 용량 약 10GB × 7일분 증분) | $1 이하 |
| Vercel | 무료 (Hobby) |
| Cloudflare | 무료 |
| **합계** | **약 $13** |

$12 요금제는 새 계정이나 첫 Lightsail 사용 때 **처음 3개월 무료** 혜택이 있을 수 있다(콘솔의 요금제 선택 화면에서 확인).

## 7. 배포해 봐야 아는 것
| 확인할 것 | 이유 |
|---|---|
| JVM 힙 320MB로 accounts가 충분한지 | Spring Authorization Server는 blog보다 무겁다. `OutOfMemoryError`가 나면 accounts만 384MB로 올린다 |
| blog → accounts를 `http://127.0.0.1:9000`으로 부를 때 문제가 없는지 | 이 요청은 Nginx를 거치지 않아 `Forwarded` 헤더가 없다. issuer는 설정값으로 고정되어 있어 문제없을 것으로 보지만, 토큰 교환과 공개 키 조회를 로그로 확인한다 |
| RDS 8.4에서 뺀 덤프가 그대로 들어가는지 | 같은 8.4라 문제없을 것으로 보지만, `DEFINER`, 문자 정렬(collation) 오류가 나는지 본다 |
| 세션이 옮겨져 기존 로그인이 유지되는지 | 세션 테이블(`SPRING_SESSION`)도 덤프에 들어간다 |
