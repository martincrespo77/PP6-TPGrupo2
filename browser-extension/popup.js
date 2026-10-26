'use strict';

(function () {
	// Firefox expone `browser` y Chrome `chrome`; en Manifest V3 los dos devuelven promesas.
	const ext = globalThis.browser ?? globalThis.chrome;
	const API = PP6_CONFIG.apiBaseUrl + '/api/v1/links';
	const ME_API = PP6_CONFIG.apiBaseUrl + '/api/v1/me';
	// Mismos patrones que RedirectController: /{alias o usuario} y /{usuario}/{alias}.
	const SHORT_PATH = /^\/[A-Za-z0-9-]{1,20}(\/[A-Za-z0-9-]{1,32})?$/;

	const MESSAGES = {
		loading: 'Acortando…',
		notShortenable: 'Esta página no se puede acortar (solo http/https)',
		alreadyShort: 'Esta página ya es un enlace corto de nuestro acortador.',
		renewed: 'Esta página ya estaba acortada: renovamos el enlace por 60 minutos.',
		personal: 'Enlace personal de ',
		anonymous: 'Enlace público. Ingresá en el acortador para que queden a tu nombre.',
		noUsername: 'Enlace público. Elegí tu nombre en el acortador para crear enlaces personales.',
		unavailable: 'No pudimos generar el enlace en este momento. Volvé a abrir la extensión en unos segundos.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y volvé a abrir la extensión.',
		unexpected: 'Ocurrió un error inesperado. Volvé a abrir la extensión.',
		copied: 'Enlace copiado.',
		copyFailed: 'No se pudo copiar. Seleccioná el enlace y copialo a mano.',
		expired: 'Este enlace venció. Volvé a abrir la extensión para crear uno nuevo.'
	};

	const pageUrl = document.getElementById('page-url');
	const mode = document.getElementById('mode');
	const status = document.getElementById('status');
	const result = document.getElementById('result');
	const shortUrl = document.getElementById('short-url');
	const copyButton = document.getElementById('copy');
	const qr = document.getElementById('qr');
	const download = document.getElementById('download');
	const expires = document.getElementById('expires');

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

	function isOwnShortLink(url) {
		const parsed = new URL(url);
		return parsed.origin === new URL(PP6_CONFIG.apiBaseUrl).origin && SHORT_PATH.test(parsed.pathname);
	}

	function showMode(text) {
		mode.textContent = text;
		mode.hidden = false;
	}

	function formatExpiration(isoInstant) {
		const date = new Date(isoInstant);
		const day = date.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
		const time = date.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
		return 'el ' + day + ' a las ' + time;
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

		expires.textContent = 'Vence ' + formatExpiration(link.expiresAt) + '.';
		expirationTimer = setTimeout(markExpired, link.secondsRemaining * 1000);
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

	function post(endpoint, body, withSession) {
		return fetch(endpoint, {
			method: 'POST',
			credentials: withSession ? 'include' : 'omit',
			headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
			body: JSON.stringify(body)
		});
	}

	/**
	 * La cuenta con la que se ingresó en el acortador, o null. La extensión no tiene ingreso propio:
	 * usa la cookie de sesión del navegador, que llega porque la extensión tiene permiso sobre el host (D60).
	 */
	async function currentAccount() {
		try {
			const response = await fetch(ME_API, { credentials: 'include', headers: { 'Accept': 'application/json' } });
			return response.ok ? await response.json() : null;
		} catch (e) {
			return null;
		}
	}

	/**
	 * Con sesión y nombre elegido, enlace personal; si no, público. Con reuseExisting el servidor
	 * devuelve 200 y renueva el enlace si la página ya estaba acortada.
	 */
	async function shorten(url) {
		showStatus(MESSAGES.loading, 'info');
		const account = await currentAccount();
		let response;
		try {
			if (account && account.usernameChosen) {
				response = await post(ME_API + '/links', { url: url, alias: '', reuseExisting: true }, true);
				if (response.status === 401) {
					response = null;
				} else {
					showMode(MESSAGES.personal + account.username + '.');
				}
			}
			if (!response) {
				showMode(account && !account.usernameChosen ? MESSAGES.noUsername : MESSAGES.anonymous);
				response = await post(API, { url: url, reuseExisting: true }, false);
			}
		} catch (e) {
			showStatus(MESSAGES.network, 'error');
			return;
		}
		if (response.status === 201) {
			showResult(await response.json());
			showStatus('');
		} else if (response.status === 200) {
			showResult(await response.json());
			showStatus(MESSAGES.renewed, 'success');
		} else if (response.status === 400) {
			showStatus((await problemDetail(response)) || MESSAGES.unexpected, 'error');
		} else if (response.status === 503) {
			showStatus(MESSAGES.unavailable, 'error');
		} else {
			showStatus(MESSAGES.unexpected, 'error');
		}
	}

	/** Al tocar el ícono se acorta la pestaña activa, sin pasos extra (consigna, §11.2). */
	async function init() {
		const [tab] = await ext.tabs.query({ active: true, currentWindow: true });
		const url = tab && tab.url;
		pageUrl.textContent = url || '';
		pageUrl.title = url || '';

		if (!isShortenable(url)) {
			showStatus(MESSAGES.notShortenable, 'warning');
			return;
		}
		if (isOwnShortLink(url)) {
			showStatus(MESSAGES.alreadyShort, 'warning');
			return;
		}
		await shorten(url);
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
