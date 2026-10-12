package ar.edu.undef.fie.pp6.shortener.application;

import ar.edu.undef.fie.pp6.shortener.domain.exception.InvalidQrSizeException;
import ar.edu.undef.fie.pp6.shortener.domain.exception.LinkNotFoundException;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.QrCodeGenerator;
import org.springframework.stereotype.Service;

/** Caso de uso "QR de un enlace": solo para enlaces vigentes y siempre con la shortUrl (I4, D7, D21). */
@Service
public class LinkQrService {

	public static final int MIN_SIZE = 128;
	public static final int MAX_SIZE = 1024;
	public static final int DEFAULT_SIZE = 256;

	private final ResolveLinkService resolveLinkService;
	private final LinkUrls linkUrls;
	private final QrCodeGenerator qrCodeGenerator;

	public LinkQrService(ResolveLinkService resolveLinkService, LinkUrls linkUrls, QrCodeGenerator qrCodeGenerator) {
		this.resolveLinkService = resolveLinkService;
		this.linkUrls = linkUrls;
		this.qrCodeGenerator = qrCodeGenerator;
	}

	public LinkQr qrFor(String alias, int size) {
		if (size < MIN_SIZE || size > MAX_SIZE) {
			throw new InvalidQrSizeException(MIN_SIZE, MAX_SIZE);
		}
		ShortLink link = resolveLinkService.findLive(alias)
				.orElseThrow(() -> new LinkNotFoundException(alias));
		String content = linkUrls.shortUrl(link.getAlias());
		return new LinkQr(link.getAlias(), qrCodeGenerator.generatePng(content, size));
	}

	/** @param alias el alias normalizado, para nombrar la descarga */
	public record LinkQr(String alias, byte[] png) {
	}
}
