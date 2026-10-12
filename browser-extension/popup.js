'use strict';

(function () {
	// Firefox expone `browser` y Chrome `chrome`; en Manifest V3 los dos devuelven promesas.
	const ext = globalThis.browser ?? globalThis.chrome;
	const API = PP6_CONFIG.apiBaseUrl + '/api/v1/links';

	const MESSAGES = {
		loading: 'Acortando…',
		notShortenable: 'Esta página no se puede acortar (solo http/https)',
		unavailable: 'No pudimos generar el enlace en este momento. Probá de nuevo en unos segundos.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.',
		unexpected: 'Ocurrió un error inesperado. Probá de nuevo.',
		copied: 'Enlace copiado.',
		copyFailed: 'No se pudo copiar. Seleccioná el enlace y copialo a mano.',
		expired: 'Este enlace venció. Apretá ACORTAR para crear uno nuevo.'
	};

	const pageUrl = document.getElementById('page-url');
	const button = document.getElementById('shorten');
	const status = document.getElementById('status');
	const result = document.getElementById('result');
	const shortUrl = document.getElementById('short-url');
	const copyButton = document.getElementById('copy');
	const qr = document.getElementById('qr');
	const download = document.getElementById('download');
	const expires = document.getElementById('expires');

	let tabUrl = null;
	let expirationTimer = null;

	function showStatus(message, kind) {
		status.textContent = message || '';
		status.className = 'status' + (kind ? ' ' + kind : '');
	}

	function isShortenable(url) {
		try {
			const protocol = new URL(url).protocol;
			return protocol === 'http:' || protocol === 'https:';
		} catch (e) {
			return false;
		}
	}

	function formatTime(isoInstant) {
		return new Date(isoInstant).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
	}

	function showResult(link) {
		clearTimeout(expirationTimer);
		result.classList.remove('expired');
		result.hidden = false;

		shortUrl.textContent = link.shortUrl;
		shortUrl.href = link.shortUrl;
		shortUrl.removeAttribute('aria-disabled');
		copyButton.disabled = false;

		qr.src = link.qrUrl;
		qr.alt = 'Código QR del enlace ' + link.shortUrl;
		// El atributo download no sirve entre orígenes: el servidor manda Content-Disposition (D22).
		download.href = link.qrUrl + '?download=true';
		download.hidden = false;

		expires.textContent = 'Vence a las ' + formatTime(link.expiresAt) + '.';
		expirationTimer = setTimeout(markExpired, link.secondsRemaining * 1000);
		showStatus('');
	}

	function markExpired() {
		result.classList.add('expired');
		shortUrl.removeAttribute('href');
		shortUrl.setAttribute('aria-disabled', 'true');
		copyButton.disabled = true;
		download.hidden = true;
		download.removeAttribute('href');
		qr.alt = 'Código QR vencido';
		expires.textContent = expires.textContent.replace('Vence', 'Venció');
		showStatus(MESSAGES.expired, 'warning');
	}

	async function problemDetail(response) {
		try {
			const body = await response.json();
			return body && body.detail;
		} catch (e) {
			return null;
		}
	}

	async function shorten() {
		showStatus(MESSAGES.loading, 'info');
		button.disabled = true;
		try {
			let response;
			try {
				response = await fetch(API, {
					method: 'POST',
					headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
					body: JSON.stringify({ url: tabUrl })
				});
			} catch (e) {
				showStatus(MESSAGES.network, 'error');
				return;
			}
			if (response.status === 201) {
				showResult(await response.json());
			} else if (response.status === 400) {
				showStatus((await problemDetail(response)) || MESSAGES.unexpected, 'error');
			} else if (response.status === 503) {
				showStatus(MESSAGES.unavailable, 'error');
			} else {
				showStatus(MESSAGES.unexpected, 'error');
			}
		} finally {
			button.disabled = false;
		}
	}

	async function init() {
		const [tab] = await ext.tabs.query({ active: true, currentWindow: true });
		const url = tab && tab.url;
		pageUrl.textContent = url || '';
		pageUrl.title = url || '';

		if (!isShortenable(url)) {
			button.disabled = true;
			showStatus(MESSAGES.notShortenable, 'warning');
			return;
		}
		tabUrl = url;
		button.addEventListener('click', shorten);
		await shorten();
	}

	copyButton.addEventListener('click', async function () {
		try {
			await navigator.clipboard.writeText(shortUrl.textContent);
			showStatus(MESSAGES.copied, 'success');
		} catch (e) {
			showStatus(MESSAGES.copyFailed, 'error');
		}
	});

	init();
})();
