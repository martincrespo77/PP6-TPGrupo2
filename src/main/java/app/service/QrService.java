package app.service;

import app.exception.InvalidQrSizeException;
import app.exception.LinkNotFoundException;
import app.mappings.PersonalLink;
import app.mappings.ShortLink;
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
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** QR de un enlace: solo para enlaces vigentes y siempre con la URL corta (I4, D7, D21). */
@Service
public class QrService {

	public static final int MIN_SIZE = 128;
	public static final int MAX_SIZE = 1024;
	public static final int DEFAULT_SIZE = 256;

	/**
	 * Corrección M (~15 %): tolera una impresión gastada o una pantalla con reflejos. El margen de 2
	 * módulos alcanza para que la cámara encuentre el código y deja más lugar al dibujo en 128 px.
	 */
	private static final Map<EncodeHintType, Object> HINTS = Map.of(
			EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
			EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
			EncodeHintType.MARGIN, 2);

	@Autowired
	private LinkService linkService;

	@Autowired
	private PersonalLinkService personalLinkService;

	@Autowired
	private LoginRequestService loginRequestService;

	/** @return PNG con el QR de la URL corta del enlace */
	public byte[] qrFor(String alias, int size) {
		checkSize(size);
		ShortLink link = linkService.findLive(alias)
				.orElseThrow(() -> new LinkNotFoundException(alias));
		String content = linkService.shortUrl(link.getAlias());
		return generatePng(content, size);
	}

	/** @return PNG con el QR de la URL de un enlace personal vigente */
	public byte[] qrForPersonal(String username, String alias, int size) {
		checkSize(size);
		PersonalLink link = personalLinkService.findLive(username, alias)
				.orElseThrow(() -> new LinkNotFoundException(alias));
		return generatePng(personalLinkService.shortUrl(username.toLowerCase(Locale.ROOT), link.getAlias()), size);
	}

	/** @return PNG con el QR que escanea el celular para aprobar el ingreso de la computadora */
	public byte[] qrForLogin(String code) {
		return generatePng(loginRequestService.approvalUrl(code), DEFAULT_SIZE);
	}

	private static void checkSize(int size) {
		if (size < MIN_SIZE || size > MAX_SIZE) {
			throw new InvalidQrSizeException(MIN_SIZE, MAX_SIZE);
		}
	}

	/** @return PNG cuadrado de {@code size} × {@code size} píxeles que codifica {@code content} */
	byte[] generatePng(String content, int size) {
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
