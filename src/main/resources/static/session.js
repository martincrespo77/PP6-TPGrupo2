'use strict';

/**
 * Header con el token CSRF de Spring Security: viene en la cookie XSRF-TOKEN y vuelve en
 * X-XSRF-TOKEN. Lo necesitan los POST y PUT de las páginas con sesión.
 */
globalThis.csrfHeaders = function () {
	const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
	return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
};

/** fetch con JSON y el token CSRF. */
globalThis.sendJson = function (method, url, body) {
	const headers = Object.assign({ 'Accept': 'application/json' }, csrfHeaders());
	const options = { method: method, headers: headers };
	if (body !== undefined) {
		headers['Content-Type'] = 'application/json';
		options.body = JSON.stringify(body);
	}
	return fetch(url, options);
};
