package app.mappings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Usuario que ingresó con Google. El nombre de usuario es su espacio de nombres: /{username}/{alias}. */
@Entity
@Table(name = "app_user")
public class AppUser {

	public static final int MAX_USERNAME_LENGTH = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Claim "sub" de Google: no cambia aunque el usuario cambie su correo. */
	@Column(name = "google_subject", length = 255, nullable = false, unique = true)
	private String googleSubject;

	@Column(name = "email", length = 320)
	private String email;

	@Column(name = "username", length = MAX_USERNAME_LENGTH, nullable = false, unique = true)
	private String username;

	/** false mientras tenga el nombre aleatorio del alta: hasta elegir uno no puede crear enlaces. */
	@Column(name = "username_chosen", nullable = false)
	private boolean usernameChosen;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AppUser() {
		// JPA
	}

	public AppUser(String googleSubject, String email, String randomUsername, Instant createdAt) {
		this.googleSubject = googleSubject;
		this.email = email;
		this.username = randomUsername;
		this.usernameChosen = false;
		this.createdAt = createdAt;
	}

	public void chooseUsername(String username) {
		this.username = username;
		this.usernameChosen = true;
	}

	public void updateEmail(String email) {
		this.email = email;
	}

	public Long getId() {
		return id;
	}

	public String getGoogleSubject() {
		return googleSubject;
	}

	public String getEmail() {
		return email;
	}

	public String getUsername() {
		return username;
	}

	public boolean isUsernameChosen() {
		return usernameChosen;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
