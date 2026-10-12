'use strict';

// Servidor al que llama la extensión (D44, C10: no configurable por el usuario).
// Para otro servidor: scripts\package-extension.ps1 -ApiUrl https://paradigmas6.agustingimenez.ar
// La dirección tiene que estar también en host_permissions de manifest.json.
globalThis.PP6_CONFIG = Object.freeze({
	apiBaseUrl: 'http://localhost:8080'
});
