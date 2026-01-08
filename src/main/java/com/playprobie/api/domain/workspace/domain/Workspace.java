package com.playprobie.api.domain.workspace.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.playprobie.api.domain.game.domain.Game;

import com.playprobie.api.global.domain.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workspace")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "workspace_id")
	private Long id;

	@Column(name = "workspace_uuid", nullable = false, unique = true, columnDefinition = "CHAR(36)")
	private UUID uuid;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "profile_image_url")
	private String profileImageUrl;

	@Column(name = "description")
	private String description;

	@OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL)
	private List<Game> games = new ArrayList<>();

	@OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL)
	private List<WorkspaceMember> members = new ArrayList<>();

	@Builder
	public Workspace(UUID uuid, String name, String profileImageUrl, String description) {
		this.uuid = uuid;
		this.name = name;
		this.profileImageUrl = profileImageUrl;
		this.description = description;
	}

	@PrePersist
	private void generateUuid() {
		if (this.uuid == null) {
			this.uuid = UUID.randomUUID();
		}
	}

	public static Workspace create(String name, String description) {
		return Workspace.builder()
			.name(name)
			.description(description)
			.build();
	}

	public void update(String name, String profileImageUrl, String description) {
		this.name = name;
		this.profileImageUrl = profileImageUrl;
		this.description = description;
	}

	public List<Game> getGames() {
		return Collections.unmodifiableList(games);
	}

	public List<WorkspaceMember> getMembers() {
		return Collections.unmodifiableList(members);
	}

}
