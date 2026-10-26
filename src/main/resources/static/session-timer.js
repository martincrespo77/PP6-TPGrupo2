'use strict';

/**
 * Sesión de computadora (D59): vence sola. Faltando warningSeconds abre un aviso para extenderla; si
 * nadie responde, al vencer lleva al ingreso. El servidor la cierra igual aunque esta página no esté
 * abierta. Usa los segundos que informa el servidor, no la hora del dispositivo (D24).
 */
globalThis.startSessionTimer = async function () {
	const API = '/api/v1/me/session';
	const EXPIRED_PAGE = '/login.html?sesion=vencida';

	const dialog = document.getElementById('session-dialog');
	const text = document.getElementById('session-text');
	const extend = document.getElementById('session-extend');
	const logout = document.getElementById('session-logout');

	let deadline = 0;
	let warningMillis = 0;
	let ticker = null;

	function format(millis) {
		const seconds = Math.max(0, Math.ceil(millis / 1000));
		return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
	}

	function apply(session) {
		deadline = Date.now() + session.secondsRemaining * 1000;
		warningMillis = session.warningSeconds * 1000;
	}

	function tick() {
		const remaining = deadline - Date.now();
		if (remaining <= 0) {
			clearInterval(ticker);
			location.assign(EXPIRED_PAGE);
			return;
		}
		if (remaining <= warningMillis) {
			text.textContent = 'Por seguridad, tu sesión en esta computadora se cierra en ' + format(remaining)
					+ '. ¿Querés seguir usándola?';
			if (!dialog.open) {
				dialog.showModal();
				extend.focus();
			}
		} else if (dialog.open) {
			dialog.close();
		}
	}

	extend.addEventListener('click', async function () {
		extend.disabled = true;
		try {
			const response = await sendJson('POST', API + '/extend');
			if (response.status === 401) {
				location.assign(EXPIRED_PAGE);
				return;
			}
			if (response.ok) {
				apply(await response.json());
				dialog.close();
			}
		} catch (e) {
			// Sin conexión: el aviso sigue abierto y se puede reintentar.
		} finally {
			extend.disabled = false;
		}
	});

	logout.addEventListener('click', async function () {
		try {
			await sendJson('POST', '/api/v1/logout');
		} finally {
			location.assign('/');
		}
	});

	// Escape cerraría el aviso sin extender: la sesión vencería igual.
	dialog.addEventListener('cancel', function (event) {
		event.preventDefault();
	});

	let response;
	try {
		response = await fetch(API, { headers: { 'Accept': 'application/json' } });
	} catch (e) {
		return;
	}
	if (!response.ok) {
		return;
	}
	const session = await response.json();
	if (!session.limited) {
		return;
	}
	apply(session);
	tick();
	ticker = setInterval(tick, 1000);
};
