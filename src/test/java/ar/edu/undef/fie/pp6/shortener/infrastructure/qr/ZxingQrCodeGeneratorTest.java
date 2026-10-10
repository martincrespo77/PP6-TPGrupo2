package ar.edu.undef.fie.pp6.shortener.infrastructure.qr;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.undef.fie.pp6.shortener.domain.port.QrCodeGenerator;
import ar.edu.undef.fie.pp6.shortener.support.QrDecoder;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ZxingQrCodeGeneratorTest {

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	private final QrCodeGenerator generator = new ZxingQrCodeGenerator();

	@Test
	void generatesAPngThatDecodesToTheExactContent() throws Exception {
		byte[] png = generator.generatePng("https://paradigmas6.agustingimenez.ar/xt3se", 256);

		assertThat(png).startsWith(PNG_SIGNATURE);
		assertThat(QrDecoder.decode(png)).isEqualTo("https://paradigmas6.agustingimenez.ar/xt3se");
	}

	@ParameterizedTest
	@ValueSource(ints = { 128, 256, 1024 })
	void imageHasTheRequestedSize(int size) throws Exception {
		BufferedImage image = QrDecoder.image(generator.generatePng("http://localhost:8080/abcde", size));

		assertThat(image.getWidth()).isEqualTo(size);
		assertThat(image.getHeight()).isEqualTo(size);
	}

	@Test
	void smallestSizeIsStillReadable() throws Exception {
		String longest = "https://paradigmas6.agustingimenez.ar/" + "a".repeat(16);

		assertThat(QrDecoder.decode(generator.generatePng(longest, 128))).isEqualTo(longest);
	}
}
