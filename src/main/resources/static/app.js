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

	function showStatus(message, kind) {
		status.textContent = message || '';
		status.className = 'status' + (kind ? ' ' + kind : '');
	}

	const card = createResultCard({
		result: document.getElementById('result'),
		shortUrl: document.getElementById('short-url'),
		copyButton: document.getElementById('copy'),
		qr: document.getElementById('qr'),
		download: document.getElementById('download'),
		expires: document.getElementById('expires')
	}, showStatus, MESSAGES);

	function setLoading(loading) {
		button.disabled = loading;
		input.disabled = loading;
		form.setAttribute('aria-busy', String(loading));
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
			card.show(await response.json());
			showStatus('');
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
})();
