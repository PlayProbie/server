package com.playprobie.api.domain.user.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.playprobie.api.domain.workspace.domain.Workspace;
import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Column(name = "user_uuid", nullable = false, unique = true)
    private java.util.UUID uuid = java.util.UUID.randomUUID();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL)
    private List<Workspace> workspaces = new ArrayList<>();

    @Builder
    public User(String email, String password, String name, String phone,
            String provider, String providerId) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.provider = provider;
        this.providerId = providerId;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 이메일/비밀번호 기반 회원 생성
     */
    public static User createWithEmail(String email, String password, String name, String phone) {
        return User.builder()
                .email(email)
                .password(password)
                .name(name)
                .phone(phone)
                .build();
    }

    /**
     * OAuth 기반 회원 생성
     */
    public static User createWithOAuth(String email, String name, String provider, String providerId) {
        return User.builder()
                .email(email)
                .name(name)
                .provider(provider)
                .providerId(providerId)
                .build();
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public boolean isOAuthUser() {
        return this.provider != null;
    }

    public List<Workspace> getWorkspaces() {
        return Collections.unmodifiableList(workspaces);
    }
}
