package ar.edu.undef.fie.pp6.shortener.domain.port;

/**
 * Propone un alias candidato. No garantiza que esté libre: la unicidad la asegura
 * {@link ShortLinkRepository#persist} y el reintento, quien orquesta.
 */
public interface AliasGenerator {

	String generate();
}
