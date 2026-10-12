package app.support;

import app.service.AliasGenerator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generador de test: devuelve primero los alias forzados y después se comporta como el generador
 * real. Es seguro entre hilos.
 */
public class ScriptedAliasGenerator extends AliasGenerator {

	private final Queue<String> forced = new ConcurrentLinkedQueue<>();
	private final AtomicInteger calls = new AtomicInteger();

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
		return next != null ? next : super.generate();
	}
}
