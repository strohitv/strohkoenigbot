package tv.strohi.twitch.strohkoenigbot.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ExtendedStatisticsExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.Statistics;

@RequestMapping("overlay")
@Controller
public class OverlayController {
	private Statistics statistics;

	@Autowired
	public void setExporter(Statistics statistics) {
		this.statistics = statistics;
	}

	private ExtendedStatisticsExporter fullscreenExporter;

	@Autowired
	public void setFullscreenExporter(ExtendedStatisticsExporter fullscreenExporter) {
		this.fullscreenExporter = fullscreenExporter;
	}

	@GetMapping("")
	public @ResponseBody String getOverlay() {
		return statistics.getFinishedHtml();
	}

	@GetMapping("/fullscreen")
	public @ResponseBody String getFullscreenOverlay() {
		return fullscreenExporter.getFinishedHtml();
	}
}
