'use strict';

(function () {
	const MESSAGES = {
		saving: 'Guardando…',
		saved: 'Nombre guardado.',
		creating: 'Creando…',
		renaming: 'Cambiando el alias…',
		renamed: 'Alias cambiado. El enlace anterior ya no funciona.',
		mustChoose: 'Tenés un nombre provisorio. Elegí uno propio para poder crear tus enlaces.',
		network: 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.',
		unexpected: 'Ocurrió un error inesperado. Probá de nuevo.',
		copied: 'Enlace copiado.',
		copyFailed: 'No se pudo copiar. Seleccioná el enlace y copialo a mano.',
		expired: 'Este enlace venció. Podés volver a crearlo con el mismo alias.'
	};

	const panel = document.getElementById('panel');
	const account = document.getElementById('account');
	const usernameForm = document.getElementById('username-form');
	const usernameInput = document.getElementById('username');
	const usernameHint = document.getElementById('username-hint');
	const linkForm = document.getElementById('link-form');
	const urlInput = document.getElementById('url');
	const aliasInput = document.getElementById('alias');
	const namespace = document.getElementById('namespace');
	const renameForm = document.getElementById('rename-form');
	const renameNamespace = document.getElementById('rename-namespace');
	const newAliasInput = document.getElementById('new-alias');
	const status = document.getElementById('status');
	let currentAlias = null;
	const logout = document.getElementById('logout');

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

	function render(me) {
		panel.hidden = false;
		account.textContent = me.email ? 'Sesión iniciada como ' + me.email + '.' : 'Sesión iniciada.';
		if (me.usernameChosen) {
			usernameInput.value = me.username;
			usernameHint.textContent = 'Tus enlaces quedan en ' + me.namespaceUrl + '…';
			namespace.textContent = me.namespaceUrl;
			renameNamespace.textContent = me.namespaceUrl;
			linkForm.hidden = false;
		} else {
			usernameInput.value = '';
			usernameHint.textContent = 'Nombre provisorio: ' + me.username + '. ' + MESSAGES.mustChoose;
			linkForm.hidden = true;
		}
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

	usernameForm.addEventListener('submit', function (event) {
		event.preventDefault();
		submit(usernameForm,
				() => sendJson('PUT', '/api/v1/me/username', { username: usernameInput.value.trim() }),
				async (response) => {
					render(await response.json());
					showStatus(MESSAGES.saved, 'success');
				},
				MESSAGES.saving);
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

	async function init() {
		let response;
		try {
			response = await fetch('/api/v1/me', { headers: { 'Accept': 'application/json' } });
		} catch (e) {
			showStatus(MESSAGES.network, 'error');
			return;
		}
		if (response.ok) {
			render(await response.json());
		} else {
			await handleError(response);
		}
	}

	init();
})();
