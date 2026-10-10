package ar.edu.undef.fie.pp6.shortener.domain.port;

import ar.edu.undef.fie.pp6.shortener.domain.exception.InvalidUrlException;

/** Decide si una URL puede acortarse. Cambiar las reglas = cambiar la implementación. */
public interface UrlValidator {

	/**
	 * @throws InvalidUrlException con un mensaje apto para mostrar al usuario
	 */
	void validate(String url);
}
