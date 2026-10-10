'use strict';

// Servidor al que llama la extensión (D44, C10: no configurable por el usuario).
// Para otro servidor: scripts\package-extension.ps1 -ApiUrl http://localhost:8080
// La dirección tiene que estar también en host_permissions de manifest.json.
globalThis.PP6_CONFIG = Object.freeze({
	apiBaseUrl: 'https://paradigmas6.agustingimenez.ar'
});
