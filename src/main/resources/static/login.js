'use strict';

(function () {
	const API = '/api/v1/login-requests';
	const POLL_MILLIS = 2000;

	const MESSAGES = {
		loading: 'Generando el código…',
		waiting: 'Esperando que lo apruebes desde el celular…',
		approved: 'Listo, entrando…',
		expired: 'El código venció.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.',
		unexpected: 'Ocurrió un error inesperado. Probá de nuevo.'
	};

	const qr = document.getElementById('login-qr');
	const status = document.getElementById('status');
	const countdown = document.getElementById('countdown');
	const retry = document.getElementById('retry');

	let pollTimer = null;
	let countdownTimer = null;

	function showStatus(message, kind) {
		status.textContent = message || '';
		status.className = 'status' + (kind ? ' ' + kind : '');
	}

	function stop() {
		clearTimeout(pollTimer);
		clearInterval(countdownTimer);
		countdown.textContent = '';
	}

	function startCountdown(seconds) {
		clearInterval(countdownTimer);
		let remaining = seconds;
		const render = function () {
			countdown.textContent = 'El código vence en ' + Math.floor(remaining / 60) + ':' + String(remaining % 60).padStart(2, '0') + '.';
		};
		render();
		countdownTimer = setInterval(function () {
			remaining = Math.max(0, remaining - 1);
			render();
		}, 1000);
	}

	function showExpired() {
		stop();
		qr.hidden = true;
		retry.hidden = false;
		showStatus(MESSAGES.expired, 'warning');
	}

	async function poll() {
		let response;
		try {
			response = await fetch(API + '/current', { headers: { 'Accept': 'application/json' } });
		} catch (e) {
			pollTimer = setTimeout(poll, POLL_MILLIS);
			return;
		}
		if (!response.ok) {
			stop();
			showStatus(MESSAGES.unexpected, 'error');
			retry.hidden = false;
			return;
		}
		const body = await response.json();
		if (body.status === 'APPROVED') {
			stop();
			showStatus(MESSAGES.approved, 'success');
			location.assign('/cuenta.html');
		} else if (body.status === 'EXPIRED') {
			showExpired();
		} else {
			pollTimer = setTimeout(poll, POLL_MILLIS);
		}
	}

	async function start() {
		stop();
		retry.hidden = true;
		showStatus(MESSAGES.loading, 'info');
		let response;
		try {
			response = await sendJson('POST', API);
		} catch (e) {
			showStatus(MESSAGES.network, 'error');
			retry.hidden = false;
			return;
		}
		if (response.status !== 201) {
			showStatus(MESSAGES.unexpected, 'error');
			retry.hidden = false;
			return;
		}
		const body = await response.json();
		qr.src = API + '/current/qr?t=' + Date.now();
		qr.hidden = false;
		showStatus(MESSAGES.waiting, 'info');
		startCountdown(body.secondsRemaining);
		pollTimer = setTimeout(poll, POLL_MILLIS);
	}

	/** Si la sesión ya está iniciada no hace falta el QR. */
	async function init() {
		try {
			const me = await fetch('/api/v1/me', { headers: { 'Accept': 'application/json' } });
			if (me.ok) {
				location.replace('/cuenta.html');
				return;
			}
		} catch (e) {
			// Sin conexión: start() muestra el error.
		}
		await start();
	}

	retry.addEventListener('click', start);
	init();
})();
