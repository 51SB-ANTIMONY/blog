---
title: Spring Boot란? Spring과의 차이
date: 2026-09-30
tags: [spring, spring-boot]
---

# Spring Boot

> 이 글에 나오는 Bean, 컨테이너, 트랜잭션 같은 용어는 [Spring Framework](../01_spring/01_spring_framework.md)와 [03_spring_concepts](../03_spring_concepts/01_ioc.md)에서 설명했다.

## 정의
Spring Boot는 Spring Framework를 기반으로, 설정을 자동화하고 실행 환경을 내장해서
바로 실행할 수 있는 애플리케이션을 빠르게 만들도록 도와주는 도구다.
Spring을 대체하는 것이 아니라, 내부에서는 그대로 Spring을 사용한다.

> **설정이란?**
> 애플리케이션이 동작하는 데 필요한 정보를 지정하는 것이다.
> 예를 들어 어떤 DB에 연결할지, 서버를 몇 번 포트로 열지, 어떤 객체를 Bean으로 등록할지를 정하는 것이 모두 설정이다.

## Spring Boot가 해 주는 일
| 기능 | 설명 | 자세한 설명 |
|---|---|---|
| 자동 설정 | 클래스패스의 라이브러리를 보고 필요한 Bean을 자동으로 등록한다 | [자동 구성](../05_auto_configuration/01_auto_configuration.md) |
| 스타터 | 관련 라이브러리를 버전까지 맞춰 한 번에 추가한다 | [스프링 부트 스타터](../04_spring_boot_starter/01_starter.md) |
| 내장 서버 | Tomcat을 따로 설치하지 않고 애플리케이션 안에서 실행한다 | [내장 WAS와 그 밖의 차이](./03_difference_embedded_was.md) |
| 외부 설정 파일 | XML 대신 `application.yml`에 값만 적는다 | [내장 WAS와 그 밖의 차이](./03_difference_embedded_was.md) |

## Spring vs Spring Boot 한눈에 보기
| 항목 | Spring Framework | Spring Boot |
|---|---|---|
| 역할 | IoC/DI, AOP, MVC 등 핵심 기능 제공 | Spring을 쉽게 쓰도록 설정과 실행 환경을 자동화 |
| 설정 | XML 또는 Java Config로 직접 작성 | 자동 설정 + `application.yml` |
| 의존성 관리 | 라이브러리와 버전을 개별 지정 | Starter로 묶음 관리, 버전은 Spring Boot가 관리 |
| 서버 | 외부 WAS에 WAR로 배포 | 내장 서버, 실행 가능한 JAR |
| 관계 | 기반 기술 | Spring 위에서 동작 |

표에 나온 차이는 두 가지로 나눠서 자세히 정리했다.
- [차이점 1. 구성의 차이](./02_difference_configuration.md)
- [차이점 2. 내장 WAS의 유무와 그 밖의 차이](./03_difference_embedded_was.md)

## 정리
객체 관리(DI), 트랜잭션, 웹 요청 처리 같은 실제 기능은 Spring이 제공한다.
Spring Boot는 그 기능을 쓰는 데 필요한 설정, 의존성 관리, 서버 실행을 자동으로 처리한다.
