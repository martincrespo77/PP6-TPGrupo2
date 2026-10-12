package ar.edu.undef.fie.pp6.shortener.infrastructure.qr;

import ar.edu.undef.fie.pp6.shortener.domain.port.QrCodeGenerator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ZxingQrCodeGenerator implements QrCodeGenerator {

	/**
	 * Corrección M (~15 %): tolera una impresión gastada o una pantalla con reflejos. El margen de 2
	 * módulos alcanza para que la cámara encuentre el código y deja más lugar al dibujo en 128 px.
	 */
	private static final Map<EncodeHintType, Object> HINTS = Map.of(
			EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
			EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
			EncodeHintType.MARGIN, 2);

	@Override
	public byte[] generatePng(String content, int size) {
		try {
			BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, HINTS);
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(matrix, "PNG", png);
			return png.toByteArray();
		} catch (WriterException e) {
			throw new IllegalStateException("No se pudo generar el QR", e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
