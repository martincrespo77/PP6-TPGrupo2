'use strict';

/** Página del usuario: se sirve en /cuenta.html y en /{usuario} (D57). */
(function () {
	const MESSAGES = {
		saving: 'Guardando…',
		saved: 'Nombre guardado.',
		reverted: 'Volviste a tu nombre anterior.',
		creating: 'Creando…',
		renaming: 'Cambiando el alias…',
		renamed: 'Alias cambiado. El enlace anterior ya no funciona.',
		mustChoose: 'Tenés un nombre provisorio. Elegí uno propio para poder crear tus enlaces.',
		otherSpace: 'Ese espacio es de otra cuenta: te llevamos al tuyo.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.',
		unexpected: 'Ocurrió un error inesperado. Probá de nuevo.',
		copied: 'Enlace copiado.',
		copyFailed: 'No se pudo copiar. Seleccioná el enlace y copialo a mano.',
		expired: 'Este enlace venció. Podés volver a crearlo con el mismo alias.'
	};
	const OWN_PAGE = /^\/[A-Za-z0-9-]{1,20}$/;

	const panel = document.getElementById('panel');
	const account = document.getElementById('account');
	const usernameForm = document.getElementById('username-form');
	const usernameInput = document.getElementById('username');
	const usernameHint = document.getElementById('username-hint');
	const previousName = document.getElementById('previous-name');
	const previousNameText = document.getElementById('previous-name-text');
	const revertButton = document.getElementById('revert-username');
	const linkForm = document.getElementById('link-form');
	const urlInput = document.getElementById('url');
	const aliasInput = document.getElementById('alias');
	const namespace = document.getElementById('namespace');
	const renameForm = document.getElementById('rename-form');
	const renameNamespace = document.getElementById('rename-namespace');
	const newAliasInput = document.getElementById('new-alias');
	const status = document.getElementById('status');
	const logout = document.getElementById('logout');

	let currentAlias = null;
	let previousUsername = null;
	let reservationTimer = null;

	function showStatus(message, kind) {
		status.textContent = message || '';
		status.className = 'status' + (kind ? ' ' + kind : '');
	}

	function formatTime(isoInstant) {
		return new Date(isoInstant).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
	}

	const card = createResultCard({
		result: document.getElementById('result'),
		shortUrl: document.getElementById('short-url'),
		copyButton: document.getElementById('copy'),
		qr: document.getElementById('qr'),
		download: document.getElementById('download'),
		expires: document.getElementById('expires')
	}, showStatus, MESSAGES);

	/** La dirección de la página es la del usuario: /{su nombre}. */
	function showOwnAddress(me) {
		const own = '/' + me.username;
		if (location.pathname === own) {
			return;
		}
		if (OWN_PAGE.test(location.pathname) && location.pathname.toLowerCase() !== own) {
			showStatus(MESSAGES.otherSpace, 'info');
		}
		history.replaceState(null, '', own);
	}

	/** Mientras el nombre anterior siga reservado se puede volver a él; al liberarse se actualiza solo (D55). */
	function renderReservation(me) {
		clearTimeout(reservationTimer);
		previousUsername = me.previousUsername;
		if (!previousUsername) {
			previousName.hidden = true;
			return;
		}
		previousNameText.textContent = 'Tu nombre anterior "' + previousUsername + '" queda reservado para vos hasta las '
				+ formatTime(me.previousUsernameReleasedAt) + '. Hasta entonces podés volver a él, y quien abra un enlace con /'
				+ previousUsername + '/… ve un aviso con la dirección nueva. Después se libera para que lo use otra persona '
				+ 'y esos enlaces dejan de funcionar con el nombre anterior.';
		revertButton.textContent = 'VOLVER A "' + previousUsername + '"';
		previousName.hidden = false;
		reservationTimer = setTimeout(refresh, me.secondsUntilPreviousReleased * 1000 + 500);
	}

	function render(me) {
		panel.hidden = false;
		account.textContent = me.email ? 'Sesión iniciada como ' + me.email + '.' : 'Sesión iniciada.';
		if (me.usernameChosen) {
			usernameInput.value = me.username;
			usernameHint.textContent = 'Tu página es ' + me.namespaceUrl.replace(/\/$/, '') + ' y tus enlaces quedan en ' + me.namespaceUrl + '…';
			namespace.textContent = me.namespaceUrl;
			renameNamespace.textContent = me.namespaceUrl;
			linkForm.hidden = false;
			showOwnAddress(me);
		} else {
			usernameInput.value = '';
			usernameHint.textContent = 'Nombre provisorio: ' + me.username + '. ' + MESSAGES.mustChoose;
			linkForm.hidden = true;
		}
		renderReservation(me);
	}

	async function handleError(response) {
		if (response.status === 401) {
			location.assign('/login.html');
			return;
		}
		showStatus((await problemDetail(response)) || MESSAGES.unexpected, 'error');
	}

	async function submit(form, request, onSuccess, loadingMessage) {
		showStatus(loadingMessage, 'info');
		form.setAttribute('aria-busy', 'true');
		try {
			const response = await request();
			if (response.ok) {
				await onSuccess(response);
			} else {
				await handleError(response);
			}
		} catch (e) {
			showStatus(MESSAGES.network, 'error');
		} finally {
			form.removeAttribute('aria-busy');
		}
	}

	function saveUsername(username, successMessage) {
		submit(usernameForm,
				() => sendJson('PUT', '/api/v1/me/username', { username: username }),
				async (response) => {
					render(await response.json());
					showStatus(successMessage, 'success');
				},
				MESSAGES.saving);
	}

	usernameForm.addEventListener('submit', function (event) {
		event.preventDefault();
		saveUsername(usernameInput.value.trim(), MESSAGES.saved);
	});

	revertButton.addEventListener('click', function () {
		saveUsername(previousUsername, MESSAGES.reverted);
	});

	linkForm.addEventListener('submit', function (event) {
		event.preventDefault();
		submit(linkForm,
				() => sendJson('POST', '/api/v1/me/links', { url: urlInput.value.trim(), alias: aliasInput.value.trim() }),
				async (response) => {
					showLink(await response.json());
					showStatus('');
				},
				MESSAGES.creating);
	});

	renameForm.addEventListener('submit', function (event) {
		event.preventDefault();
		submit(renameForm,
				() => sendJson('PATCH', '/api/v1/me/links/' + encodeURIComponent(currentAlias),
						{ alias: newAliasInput.value.trim() }),
				async (response) => {
					showLink(await response.json());
					showStatus(MESSAGES.renamed, 'success');
				},
				MESSAGES.renaming);
	});

	function showLink(link) {
		currentAlias = link.alias;
		newAliasInput.value = link.alias;
		renameForm.hidden = false;
		card.show(link);
	}

	logout.addEventListener('click', async function () {
		try {
			await sendJson('POST', '/api/v1/logout');
		} finally {
			location.assign('/');
		}
	});

	async function refresh() {
		let response;
		try {
			response = await fetch('/api/v1/me', { headers: { 'Accept': 'application/json' } });
		} catch (e) {
			showStatus(MESSAGES.network, 'error');
			return false;
		}
		if (response.ok) {
			render(await response.json());
			return true;
		}
		await handleError(response);
		return false;
	}

	async function init() {
		if (await refresh()) {
			startSessionTimer();
		}
	}

	init();
})();
