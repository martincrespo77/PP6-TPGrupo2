package app.mappings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Enlace con alias elegido dentro del espacio de un usuario: /{username}/{alias}. Se ata al usuario y
 * no al nombre, así que si el usuario cambia de nombre sus enlaces pasan al nombre nuevo.
 */
@Entity
@Table(name = "personal_link",
		uniqueConstraints = @UniqueConstraint(name = "uk_personal_link_owner_alias", columnNames = { "owner_id", "alias" }),
		indexes = @Index(name = "idx_personal_link_expires_at", columnList = "expires_at"))
public class PersonalLink {

	public static final int MAX_ALIAS_LENGTH = 32;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(name = "alias", length = MAX_ALIAS_LENGTH, nullable = false)
	private String alias;

	@Column(name = "original_url", length = ShortLink.MAX_URL_LENGTH, nullable = false)
	private String originalUrl;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected PersonalLink() {
		// JPA
	}

	public PersonalLink(AppUser owner, String alias, String originalUrl, Instant createdAt, Instant expiresAt) {
		this.owner = owner;
		this.alias = alias;
		this.originalUrl = originalUrl;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** Misma regla que ShortLink: vigente en [createdAt, expiresAt). */
	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	public Long getId() {
		return id;
	}

	public AppUser getOwner() {
		return owner;
	}

	public String getAlias() {
		return alias;
	}

	public String getOriginalUrl() {
		return originalUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
