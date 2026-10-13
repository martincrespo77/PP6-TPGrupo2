package app.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * GET /coloquio: guía para explicar el código. Sin esta ruta, "coloquio" cumple el patrón del alias y
 * RedirectController respondería 404 "Este enlace expiró o no existe".
 */
@Hidden
@Controller
public class ColoquioController {

	@GetMapping("/coloquio")
	public String coloquio() {
		return "forward:/coloquio.html";
	}
}
