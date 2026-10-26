'use strict';

(function () {
	const MESSAGES = {
		approving: 'Aprobando…',
		approved: 'Listo. Ya podés seguir en la computadora.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.',
		unexpected: 'Ocurrió un error inesperado. Probá de nuevo.'
	};

	const button = document.getElementById('approve');
	const status = document.getElementById('status');
	// La página se sirve en /l/{código}.
	const code = location.pathname.split('/')[2];

	function showStatus(message, kind) {
		status.textContent = message || '';
		status.className = 'status' + (kind ? ' ' + kind : '');
	}

	button.addEventListener('click', async function () {
		button.disabled = true;
		showStatus(MESSAGES.approving, 'info');
		let response;
		try {
			response = await sendJson('POST', '/api/v1/login-requests/' + encodeURIComponent(code) + '/approve');
		} catch (e) {
			button.disabled = false;
			showStatus(MESSAGES.network, 'error');
			return;
		}
		if (response.status === 204) {
			button.hidden = true;
			showStatus(MESSAGES.approved, 'success');
		} else if (response.status === 401) {
			location.reload();
		} else {
			button.hidden = response.status === 404;
			button.disabled = false;
			showStatus((await problemDetail(response)) || MESSAGES.unexpected, 'error');
		}
	});
})();
