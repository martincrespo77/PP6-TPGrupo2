package app.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * GET /descargas/acortador-pp6.zip: la extensión lista para instalar. config.js se reescribe con
 * app.base-url, así la extensión descargada llama al mismo servidor del que se bajó (C10, D44).
 */
@Hidden
@Controller
public class ExtensionController {

	private static final String PACKAGE = "extension/acortador-pp6.zip";
	private static final String FILE_NAME = "acortador-pp6.zip";

	private final byte[] zip;

	public ExtensionController(@Value("${app.base-url}") String baseUrl) {
		this.zip = withApiBaseUrl(baseUrl.replaceAll("/+$", ""));
	}

	@GetMapping("/descargas/" + FILE_NAME)
	public ResponseEntity<byte[]> download() {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/zip"))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(FILE_NAME).build().toString())
				.body(zip);
	}

	private static byte[] withApiBaseUrl(String baseUrl) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (InputStream source = new ClassPathResource(PACKAGE).getInputStream();
				ZipInputStream in = new ZipInputStream(source);
				ZipOutputStream out = new ZipOutputStream(bytes)) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				byte[] content = in.readAllBytes();
				if (entry.getName().equals("config.js")) {
					String config = new String(content, StandardCharsets.UTF_8)
							.replaceAll("apiBaseUrl:\\s*'[^']*'", Matcher.quoteReplacement("apiBaseUrl: '" + baseUrl + "'"));
					content = config.getBytes(StandardCharsets.UTF_8);
				}
				out.putNextEntry(new ZipEntry(entry.getName()));
				out.write(content);
				out.closeEntry();
			}
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo preparar " + PACKAGE, e);
		}
		return bytes.toByteArray();
	}
}
