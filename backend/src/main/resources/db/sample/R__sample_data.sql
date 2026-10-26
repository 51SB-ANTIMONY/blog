-- 로컬 개발용 예시 데이터. local 프로필에서만 읽는다 (backend/application-local.yml의 spring.flyway.locations).
-- 예시 작성자는 accounts에 없는 sub이므로 이 회원으로는 로그인할 수 없고, 글 목록을 채우는 데만 쓴다.
-- 작성일은 오늘에서 며칠 전으로 넣어서, 홈의 NEW 표시(7일)와 기도편지 연도 탭을 확인할 수 있게 한다.
INSERT INTO users (accounts_sub, nickname) VALUES ('sample', '예시 작성자');
INSERT INTO users (accounts_sub, nickname) VALUES ('sample-2', '닉네임이열여섯글자인긴예시작성자');

INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('함께 걷는 시간', '여름의 끝자락에서, 함께 걸어가는 사람들의 마음을 떠올립니다.', 'PRAYER', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -800, NOW()), TIMESTAMPADD(DAY, -800, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('한 해를 돌아보며', '한 해 동안 지켜 주신 은혜에 감사드립니다.', 'PRAYER', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -400, NOW()), TIMESTAMPADD(DAY, -400, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('노트를 정리하는 방법', '배운 것을 **한 줄로** 요약해 두면 나중에 다시 꺼내 보기 쉽습니다.', 'STUDY', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample-2'), TIMESTAMPADD(DAY, -60, NOW()), TIMESTAMPADD(DAY, -60, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('새로운 계절 앞에서', '선선한 바람이 불어오는 요즘, 우리 가정의 마음도 새로운 계절을 준비하고 있습니다.
지금까지 인도하신 하나님께 감사드리며, 앞으로의 걸음도 함께 기도 부탁드립니다.', 'PRAYER', '새로운 계절을 맞는 우리 가정의 마음과 앞으로의 걸음을 위한 기도 제목을 나눕니다.', (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -40, NOW()), TIMESTAMPADD(DAY, -40, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('사진과 일상 기록을 나눕니다', '사진과 함께 일상의 기록을 나누기 시작했어요.', 'NEWS', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -20, NOW()), TIMESTAMPADD(DAY, -20, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('하루 한 쪽씩 읽어가는 중', '하루에 한 쪽씩, 천천히 읽어가고 있습니다.
빨리 읽을 때는 놓치던 문장들이 보이기 시작했어요.', 'STUDY', '하루 한 쪽씩 천천히 읽으며 놓치던 문장을 다시 만난 이야기.', (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -12, NOW()), TIMESTAMPADD(DAY, -11, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('가을의 끝자락에서', '낙엽이 쌓인 길을 천천히 걸었습니다.

바람이 차가워졌지만, 햇살은 아직 따뜻했어요.', 'DAILY', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -8, NOW()), TIMESTAMPADD(DAY, -8, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('블로그를 새롭게 단장했어요', '안녕하세요. 소식과 일상, 학습, 기도편지를 조금 더 편하게 나누기 위해 블로그를 새롭게 정리했습니다.

## 이렇게 달라졌어요

1. 글을 소식·일상·학습·기도편지로 나누어 담았어요.
2. 새로운 소식은 첫 화면 상단에서 확인할 수 있어요.
3. 기도편지는 연도별로 모아두었습니다.

> 천천히 채워갈 이 공간에 종종 들러주세요.', 'NEWS', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -5, NOW()), TIMESTAMPADD(DAY, -5, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('작은 친절이 남긴 것', '낯선 이가 건넨 짧은 인사가 하루 종일 마음에 남았습니다.', 'DAILY', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample-2'), TIMESTAMPADD(DAY, -4, NOW()), TIMESTAMPADD(DAY, -4, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('별일 없어서 좋았던 하루', '오랜만에 서두르지 않는 아침을 보냈어요. 창가에 앉아 따뜻한 커피를 마시고, 읽다 만 책을 조금 읽었습니다.
특별한 일은 없었지만, 그래서 더 오래 기억하고 싶은 하루였습니다.

> 평범한 하루도 충분히 좋은 하루.', 'DAILY', '서두르지 않은 아침, 따뜻한 커피와 읽다 만 책 한 권.', (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -2, NOW()), TIMESTAMPADD(DAY, -1, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('작은 감사들을 모아', '바쁜 일상 속에서도 여전히 좋은 것을 주시는 하나님을 바라봅니다.
작은 일들 속에 숨겨진 은혜들을 돌아보며, 오늘도 감사의 제목을 적어봅니다.', 'PRAYER', '바쁜 하루 속에서도 좋은 것을 주시는 하나님께 감사하며, 작은 감사의 제목들을 함께 나눕니다.', (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -1, NOW()), TIMESTAMPADD(DAY, -1, NOW()));
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('이번 달 기도편지를 올렸어요', '이번 달 기도편지를 올렸습니다. 기도편지 메뉴에서 읽어 주세요.', 'NEWS', NULL, (SELECT id FROM users WHERE accounts_sub = 'sample'), TIMESTAMPADD(DAY, -1, NOW()), TIMESTAMPADD(DAY, -1, NOW()));
