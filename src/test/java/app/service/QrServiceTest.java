package app.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.support.QrDecoder;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Generación de la imagen. Las reglas del QR de un enlace (vigencia, tamaño) se prueban en LinkControllerTest. */
class QrServiceTest {

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private final QrService service = new QrService();

	@Test
	void generatesAPngThatDecodesToTheExactContent() throws Exception {
		byte[] png = service.generatePng("https://paradigmas6.agustingimenez.ar/xt3se", 256);

		assertThat(png).startsWith(PNG_SIGNATURE);
		assertThat(QrDecoder.decode(png)).isEqualTo("https://paradigmas6.agustingimenez.ar/xt3se");
	}

	@ParameterizedTest
	@ValueSource(ints = { 128, 256, 1024 })
	void imageHasTheRequestedSize(int size) throws Exception {
		BufferedImage image = QrDecoder.image(service.generatePng("http://localhost:8080/abcde", size));

		assertThat(image.getWidth()).isEqualTo(size);
		assertThat(image.getHeight()).isEqualTo(size);
	}

	@Test
	void smallestSizeIsStillReadable() throws Exception {
		String longest = "https://paradigmas6.agustingimenez.ar/" + "a".repeat(16);

		assertThat(QrDecoder.decode(service.generatePng(longest, 128))).isEqualTo(longest);
	}
}
