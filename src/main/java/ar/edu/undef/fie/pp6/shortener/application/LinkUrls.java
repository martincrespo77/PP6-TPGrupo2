package ar.edu.undef.fie.pp6.shortener.application;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Arma las URLs públicas de un enlace siempre desde app.base-url, nunca desde la petición (I4, D23). */
@Component
public class LinkUrls {

	private final String baseUrl;

	@Autowired
	public LinkUrls(AppProperties properties) {
		this(properties.baseUrl());
	}

	public LinkUrls(String baseUrl) {
		this.baseUrl = baseUrl.replaceAll("/+$", "");
	}

	public String shortUrl(String alias) {
		return baseUrl + "/" + alias;
	}

	public String qrUrl(String alias) {
		return baseUrl + "/api/v1/links/" + alias + "/qr";
	}
}
