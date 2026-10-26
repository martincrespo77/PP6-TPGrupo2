'use strict';

/**
 * Tarjeta del último enlace generado: enlace, Copiar, QR, Descargar QR y vencimiento, con el estado
 * "vencido" (§11.1). La usan la página principal y la de la cuenta.
 *
 * @param elements { result, shortUrl, copyButton, qr, download, expires }
 * @param showStatus función (mensaje, tipo) de la página
 * @param messages { copied, copyFailed, expired }
 */
globalThis.createResultCard = function (elements, showStatus, messages) {
	const { result, shortUrl, copyButton, qr, download, expires } = elements;
	let expirationTimer = null;

	function formatTime(isoInstant) {
		return new Date(isoInstant).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
	}

	/** Solo el último enlace generado (C8): cada resultado reemplaza al anterior y a su temporizador. */
	function show(link) {
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
		showStatus(messages.expired, 'warning');
	}

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
		showStatus(copied ? messages.copied : messages.copyFailed, copied ? 'success' : 'error');
	});

	return { show: show };
};

/** Detalle de un error ProblemDetail (D26), o null si la respuesta no lo trae. */
globalThis.problemDetail = async function (response) {
	try {
		const body = await response.json();
		return body && body.detail;
	} catch (e) {
		return null;
	}
};
