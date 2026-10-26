package app.mappings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Pedido de ingreso de una computadora por QR: la computadora lo crea, el celular (ya logueado con
 * Google) lo aprueba y la computadora lo consume una sola vez para iniciar su sesión.
 */
@Entity
@Table(name = "login_request", indexes = @Index(name = "idx_login_request_expires_at", columnList = "expires_at"))
public class LoginRequest {

	public enum Status { PENDING, APPROVED, CONSUMED }

	public static final int CODE_LENGTH = 43;

	/** Secreto: quien lo conoce puede aprobarlo. Solo viaja en el QR. */
	@Id
	@Column(name = "code", length = CODE_LENGTH)
	private String code;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 16, nullable = false)
	private Status status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private AppUser user;

	protected LoginRequest() {
		// JPA
	}

	public LoginRequest(String code, Instant createdAt, Instant expiresAt) {
		this.code = code;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.status = Status.PENDING;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	public String getCode() {
		return code;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Status getStatus() {
		return status;
	}

	public AppUser getUser() {
		return user;
	}
}
