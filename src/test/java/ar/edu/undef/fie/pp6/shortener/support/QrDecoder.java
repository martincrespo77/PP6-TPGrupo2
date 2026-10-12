package ar.edu.undef.fie.pp6.shortener.support;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Lee un PNG como lo haría la cámara de un celular: decodifica el QR y devuelve su contenido. */
public final class QrDecoder {

	private QrDecoder() {
	}

	public static BufferedImage image(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		if (image == null) {
			throw new IOException("No es una imagen válida");
		}
		return image;
	}

	public static String decode(byte[] png) throws IOException, NotFoundException {
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image(png))));
		return new MultiFormatReader().decode(bitmap).getText();
	}
}
