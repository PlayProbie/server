package com.playprobie.api.domain.game.domain;

import java.util.UUID;

import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_build")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameBuild extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "build_id")
    private Long id;

    @Column(name = "build_uuid", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    private UUID uuid;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BuildStatus status;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Builder
    private GameBuild(Game game, UUID uuid, String originalFilename, String s3Key, Long fileSize) {
        this.game = game;
        this.uuid = uuid;
        this.originalFilename = originalFilename;
        this.s3Key = s3Key;
        this.fileSize = fileSize;
        this.status = BuildStatus.PENDING;
    }

    public void markAsUploaded() {
        this.status = BuildStatus.UPLOADED;
    }
}
