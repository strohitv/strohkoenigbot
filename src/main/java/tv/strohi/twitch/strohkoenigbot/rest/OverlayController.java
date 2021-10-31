package tv.strohi.twitch.strohkoenigbot.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ResultsExporter;

@RequestMapping("overlay")
@Controller
public class OverlayController {
	private ResultsExporter exporter;

	@Autowired
	public void setExporter(ResultsExporter exporter) {
		this.exporter = exporter;
	}

	@GetMapping("")
	public @ResponseBody String getOverlay() {
		return exporter.getHtml();
	}
}
