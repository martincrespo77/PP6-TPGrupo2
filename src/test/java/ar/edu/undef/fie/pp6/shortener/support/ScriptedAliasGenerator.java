package ar.edu.undef.fie.pp6.shortener.support;

import ar.edu.undef.fie.pp6.shortener.domain.port.AliasGenerator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generador de test: devuelve primero los alias forzados y después delega en el generador real
 * (o falla si no hay uno). Es seguro entre hilos.
 */
public class ScriptedAliasGenerator implements AliasGenerator {

	private final Queue<String> forced = new ConcurrentLinkedQueue<>();
	private final AliasGenerator fallback;
	private final AtomicInteger calls = new AtomicInteger();

	public ScriptedAliasGenerator(AliasGenerator fallback) {
		this.fallback = fallback;
	}

	public ScriptedAliasGenerator force(String... aliases) {
		forced.addAll(List.of(aliases));
		return this;
	}

	public void reset() {
		forced.clear();
		calls.set(0);
	}

	public int calls() {
		return calls.get();
	}

	@Override
	public String generate() {
		calls.incrementAndGet();
		String next = forced.poll();
		if (next != null) {
			return next;
		}
		if (fallback == null) {
			throw new IllegalStateException("Se pidieron más alias que los forzados en el test");
		}
		return fallback.generate();
	}
}
