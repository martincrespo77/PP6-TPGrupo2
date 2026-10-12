package app.mappings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "short_link", indexes = @Index(name = "idx_short_link_expires_at", columnList = "expires_at"))
public class ShortLink {

	public static final int MAX_ALIAS_LENGTH = 16;
	public static final int MAX_URL_LENGTH = 2048;

	@Id
	@Column(name = "alias", length = MAX_ALIAS_LENGTH)
	private String alias;

	@Column(name = "original_url", length = MAX_URL_LENGTH, nullable = false)
	private String originalUrl;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected ShortLink() {
		// JPA
	}

	public ShortLink(String alias, String originalUrl, Instant createdAt, Instant expiresAt) {
		this.alias = alias;
		this.originalUrl = originalUrl;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** Vigente en el intervalo semiabierto [createdAt, expiresAt): en {@code expiresAt} ya venció. */
	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
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
