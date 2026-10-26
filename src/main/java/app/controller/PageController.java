package app.controller;

import app.mappings.LoginRequest;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Páginas con ruta propia. El resto de la web son archivos estáticos con su nombre. */
@Hidden
@Controller
public class PageController {

	/** Dirección del QR de ingreso: la abre el celular, que antes pasa por el login de Google. */
	@GetMapping("/l/{code:[A-Za-z0-9_-]{" + LoginRequest.CODE_LENGTH + "}}")
	public String confirmLogin() {
		return "forward:/confirmar-ingreso.html";
	}
}
