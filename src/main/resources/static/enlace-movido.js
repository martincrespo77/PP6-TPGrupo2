'use strict';

/** Aviso de enlace con el nombre de usuario anterior: muestra la dirección nueva y sigue solo al destino. */
(function () {
	const SECONDS = 8;

	const oldUntil = document.getElementById('old-until');
	const countdown = document.getElementById('countdown');
	const target = document.getElementById('continue').href;

	const releasedAt = new Date(oldUntil.dataset.releasedAt);
	if (!isNaN(releasedAt)) {
		const time = releasedAt.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
		oldUntil.textContent = 'deja de funcionar a las ' + time;
	}

	let remaining = SECONDS;
	function render() {
		countdown.textContent = 'Te llevamos al destino en ' + remaining + ' s.';
	}
	render();
	const timer = setInterval(function () {
		remaining -= 1;
		if (remaining <= 0) {
			clearInterval(timer);
			location.assign(target);
			return;
		}
		render();
	}, 1000);
})();
