'use strict';

(function () {
	const API = '/api/v1/links';

	const MESSAGES = {
		loading: 'Acortando…',
		unavailable: 'No pudimos generar el enlace en este momento. Probá de nuevo en unos segundos.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.',
		unexpected: 'Ocurrió un error inesperado. Probá de nuevo.',
		copied: 'Enlace copiado.',
		copyFailed: 'No se pudo copiar. Seleccioná el enlace y copialo a mano.',
		expired: 'Este enlace venció. Creá uno nuevo para seguir compartiendo.'
	};

	const form = document.getElementById('shorten-form');
	const input = document.getElementById('url');
	const button = document.getElementById('shorten');
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

	function setLoading(loading) {
		button.disabled = loading;
		input.disabled = loading;
		form.setAttribute('aria-busy', String(loading));
	}

	function formatTime(isoInstant) {
		return new Date(isoInstant).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
	}

	/** Solo el último enlace generado (C8): cada resultado reemplaza al anterior y a su temporizador. */
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
		download.href = link.qrUrl + '?download=true';
		download.setAttribute('download', link.alias + '.png');
		download.hidden = false;

		expires.textContent = 'Vence a las ' + formatTime(link.expiresAt) + '.';

		// secondsRemaining no depende del reloj del dispositivo (D24); expiresAt solo se usa para mostrar la hora.
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

	async function shorten(url) {
		let response;
		try {
			response = await fetch(API, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
				body: JSON.stringify({ url: url })
			});
		} catch (e) {
			showStatus(MESSAGES.network, 'error');
			return;
		}
		if (response.status === 201) {
			showResult(await response.json());
			input.value = '';
		} else if (response.status === 400) {
			showStatus((await problemDetail(response)) || MESSAGES.unexpected, 'error');
			input.focus();
		} else if (response.status === 503) {
			showStatus(MESSAGES.unavailable, 'error');
		} else {
			showStatus(MESSAGES.unexpected, 'error');
		}
	}

	form.addEventListener('submit', async function (event) {
		event.preventDefault();
		showStatus(MESSAGES.loading, 'info');
		setLoading(true);
		try {
			await shorten(input.value.trim());
		} finally {
			setLoading(false);
		}
	});

	/** navigator.clipboard solo existe en contextos seguros (HTTPS o localhost); por HTTP plano (C14) se usa execCommand. */
	function copyWithSelection(text) {
		const helper = document.createElement('textarea');
		helper.value = text;
		helper.setAttribute('readonly', '');
		helper.style.position = 'fixed';
		helper.style.opacity = '0';
		document.body.appendChild(helper);
		helper.select();
		try {
			return document.execCommand('copy');
		} finally {
			document.body.removeChild(helper);
		}
	}

	async function copyText(text) {
		if (navigator.clipboard && window.isSecureContext) {
			try {
				await navigator.clipboard.writeText(text);
				return true;
			} catch (e) {
				// Sin permiso del navegador: se intenta con la selección.
			}
		}
		return copyWithSelection(text);
	}

	copyButton.addEventListener('click', async function () {
		const copied = await copyText(shortUrl.textContent);
		showStatus(copied ? MESSAGES.copied : MESSAGES.copyFailed, copied ? 'success' : 'error');
	});
})();
