package ar.edu.undef.fie.pp6.shortener.domain.port;

/** Genera la imagen de un código QR. Cambiar de biblioteca = otra implementación (ADR-0004). */
public interface QrCodeGenerator {

	/** @return PNG cuadrado de {@code size} × {@code size} píxeles que codifica {@code content} */
	byte[] generatePng(String content, int size);
}
