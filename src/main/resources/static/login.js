'use strict';

/**
 * Ingreso de la computadora por QR (D51). Cada código dura app.login.ttl: al vencer se pide otro y el
 * anterior deja de servir (D58). Después de MAX_CODES seguidos sin que nadie lo escanee se deja de
 * renovar, para no consultar al servidor indefinidamente con la pestaña olvidada.
 */
(function () {
	const API = '/api/v1/login-requests';
	const POLL_MILLIS = 2000;
	const MAX_CODES = 10;

	const MESSAGES = {
		loading: 'Generando el código…',
		waiting: 'Esperando que lo apruebes desde el celular…',
		approved: 'Listo, entrando…',
		expired: 'El código venció.',
		sessionExpired: 'Tu sesión en esta computadora se cerró por tiempo. Escaneá el código para volver a entrar.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.',
		unexpected: 'Ocurrió un error inesperado. Probá de nuevo.'
	};

	const qr = document.getElementById('login-qr');
	const status = document.getElementById('status');
	const countdown = document.getElementById('countdown');
	const retry = document.getElementById('retry');
	const notice = document.getElementById('notice');

	let pollTimer = null;
	let countdownTimer = null;
	let codesShown = 0;

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
			countdown.textContent = 'El código se renueva en ' + Math.floor(remaining / 60) + ':' + String(remaining % 60).padStart(2, '0') + '.';
		};
		render();
		countdownTimer = setInterval(function () {
			remaining = Math.max(0, remaining - 1);
			render();
		}, 1000);
	}

	/** El código vencido ya no sirve: se reemplaza por uno nuevo, salvo que se hayan mostrado demasiados. */
	function showExpired() {
		stop();
		if (codesShown < MAX_CODES) {
			start();
			return;
		}
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
		codesShown += 1;
		qr.src = API + '/current/qr?t=' + Date.now();
		qr.hidden = false;
		showStatus(MESSAGES.waiting, 'info');
		startCountdown(body.secondsRemaining);
		pollTimer = setTimeout(poll, POLL_MILLIS);
	}

	function restart() {
		codesShown = 0;
		start();
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

	if (new URLSearchParams(location.search).get('sesion') === 'vencida') {
		notice.textContent = MESSAGES.sessionExpired;
		notice.hidden = false;
	}
	retry.addEventListener('click', restart);
	init();
})();
