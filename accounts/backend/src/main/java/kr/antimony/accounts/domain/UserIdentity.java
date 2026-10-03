package kr.antimony.accounts.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 회원과 외부 로그인 계정(Google 등)의 연결.
// 외부 계정은 이메일이 아니라 제공자가 주는 고유 번호(sub)로 구별한다. 이메일은 바뀔 수 있기 때문이다.
@Table(name = "user_identities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_subject"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class UserIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // application.yml에 등록한 제공자 이름. 예: google
    @Column(name = "provider", nullable = false)
    private String provider;

    // 제공자가 주는 계정 고유 번호. Google의 sub (예: 110248495921238986420)
    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UserIdentity(User user, String provider, String providerSubject) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }
}
