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

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "total_size")
    private Long totalSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BuildStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Builder
    private GameBuild(Game game, UUID uuid, String version) {
        this.game = game;
        this.uuid = uuid;
        this.version = version;
        this.status = BuildStatus.PENDING;
    }

    public void markAsUploaded(int totalFiles, long totalSize) {
        this.status = BuildStatus.UPLOADED;
        this.totalFiles = totalFiles;
        this.totalSize = totalSize;
    }

    public String getS3Prefix() {
        return String.format("%s/%s/", this.game.getUuid(), this.uuid);
    }

}
