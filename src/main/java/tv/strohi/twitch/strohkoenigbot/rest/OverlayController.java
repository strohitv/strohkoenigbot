package tv.strohi.twitch.strohkoenigbot.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3BadgeSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3EmoteSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3StreamStatistics;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ExtendedStatisticsExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.Statistics;

@RequestMapping("overlay")
@Controller
@RequiredArgsConstructor
public class OverlayController {
	private final Statistics statistics;
	private final S3StreamStatistics s3StreamStatistics;
	private final ExtendedStatisticsExporter fullscreenExporter;

	private final S3BadgeSender badgeSender;

	private final S3EmoteSender emoteSender;

	@GetMapping("s3")
	public @ResponseBody String getS3Overlay() {
		return s3StreamStatistics.getFinishedHtml();
	}

	@GetMapping("")
	public @ResponseBody String getOverlay() {
		return statistics.getFinishedHtml();
	}

	@GetMapping("/fullscreen")
	public @ResponseBody String getFullscreenOverlay() {
		return fullscreenExporter.getFinishedHtml();
	}

	@GetMapping(value = "/badges", produces = "text/html")
	public @ResponseBody String getBadgesOverlayHtml() {
		return badgeSender.getBadgesAsHtml();
	}

	@GetMapping(value = "/emotes", produces = "text/html")
	public @ResponseBody String getEmotesOverlayHtml() {
		return emoteSender.getEmotesAsHtml();
	}
}
