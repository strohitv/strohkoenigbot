package tv.strohi.twitch.strohkoenigbot.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3BadgeSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3Downloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3EmoteSender;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3StreamStatistics;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.ExtendedStatisticsExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.Statistics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RequestMapping("overlay")
@Controller
@RequiredArgsConstructor
@Log4j2
public class OverlayController {
	private final Statistics statistics;
	private final S3StreamStatistics s3StreamStatistics;
	private final ExtendedStatisticsExporter fullscreenExporter;
	private final S3BadgeSender badgeSender;
	private final S3EmoteSender emoteSender;
	private final TwitchBotClient twitchBotClient;
	private final S3Downloader s3Downloader;
	private final LogSender logSender;

	@GetMapping("s3")
	public @ResponseBody String getS3Overlay() {
		if (twitchBotClient.getWentLiveTime() != null) {
			var lastUpdateTime = s3StreamStatistics.getLastUpdate();
			if (lastUpdateTime == null || Instant.now().minus(10, ChronoUnit.MINUTES).isAfter(lastUpdateTime)) {
				logSender.sendLogs(log, "s3 stream statistics was not queried for 10 minutes, attempting to refresh s3 battles directly...");
				s3Downloader.downloadBattles(true);

				if (lastUpdateTime != s3StreamStatistics.getLastUpdate()) {
					logSender.sendLogs(log, "done");
				} else {
					logSender.sendLogs(log, "still no change! :(");
				}
			}
		}

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
