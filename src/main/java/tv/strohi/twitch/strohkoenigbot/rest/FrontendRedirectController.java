package tv.strohi.twitch.strohkoenigbot.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class FrontendRedirectController {
	@GetMapping("/frontend/{path:[^.]*}")
	public String forward(@PathVariable(value = "path", required = false) String path) {
		return "forward:/frontend/index.html";
	}
}
