package app.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Test con la aplicación completa sobre una base en memoria. Todos comparten la misma configuración,
 * así Spring arranca una sola vez; por eso cada test deja la tabla vacía al terminar.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:testdb",
		"app.base-url=https://sho.rt",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
@AutoConfigureMockMvc
@Import(TestBeans.class)
public @interface IntegrationTest {
}
