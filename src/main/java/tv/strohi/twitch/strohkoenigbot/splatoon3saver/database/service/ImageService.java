package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.ImageRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.ResourcesDownloader;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.SchedulingService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class ImageService {
	private static final int DOWNLOAD_COUNT = 30;
	private static final int FAILED_COUNT = 50;
	private static final int IMAGE_DOWNLOAD_MAX_FAIL_COUNT = 5;

	private final ImageRepository imageRepository;
	private final ResourcesDownloader resourcesDownloader;
	private final SchedulingService schedulingService;
	private final DiscordBot discordBot;

	@Getter
	@Setter
	private boolean pauseService = false;

	private LogSender logSender = null;

	private LogSender getLogSender() {
		if (logSender == null) {
			logSender = new LogSender(discordBot);
		}

		return logSender;
	}

	private final Pattern imageUrlPattern = Pattern.compile("https://api\\.lp1\\.av5ja\\.srv\\.nintendo\\.net[^\"]+");
	private final Pattern imagePlaceholderPattern = Pattern.compile("<<<[0-9]+>>>");

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("ShortenedImageService_download10Images", TickSchedule.getScheduleString(60), this::downloadSomeMissingImages);
	}

	@Transactional
	public Image ensureExists(@NonNull String imageUrl) {
		return imageRepository.findByUrl(imageUrl)
			.orElseGet(() -> imageRepository.save(Image.builder().url(imageUrl).build()));
	}

	@Transactional
	public String shortenJson(@NonNull String json) {
		var endResult = json;
		var results = imageUrlPattern.matcher(json).results().collect(Collectors.toList());

		for (var result : results) {
			endResult = endResult.replaceAll(Pattern.quote(result.group()), String.format("<<<%d>>>", ensureExists(result.group()).getId()));
		}

		return endResult;
	}

	@Transactional
	public String restoreJson(@NonNull String json) {
		var endResult = json;
		var results = imagePlaceholderPattern.matcher(json).results().collect(Collectors.toList());

		for (var result : results) {
			long id = Long.parseLong(result.group().replaceAll("[^0-9]", ""));
			var imageUrl = imageRepository.findById(id).map(Image::getUrl).orElseThrow();

			endResult = endResult.replaceAll(Pattern.quote(result.group()), imageUrl);
		}

		return endResult;
	}

	private final List<Image> brokenImages = new ArrayList<>();

	@Transactional
	public void downloadSomeMissingImages() {
		if (pauseService) return;

		var notDownloadedImages = imageRepository.findByFilePathNullAndFailedDownloadCountLessThanEqual(IMAGE_DOWNLOAD_MAX_FAIL_COUNT).stream()
			.filter(i -> !i.isDownloaded())
			.filter(i -> !brokenImages.contains(i))
			.collect(Collectors.toList());

		int i = 0;
		int failed = 0;

		for (var image : notDownloadedImages) {
			String imageLocationString = resourcesDownloader.ensureExistsLocally(image.getUrl().replace("\\u0026", "&"));
			String path = Paths.get(imageLocationString).toString();

			if (!imageLocationString.startsWith("https://")) {
				var savedImage = imageRepository.save(image.toBuilder()
					.filePath(Paths.get(System.getProperty("user.dir"), path).toString())
					.downloaded(true)
					.build());

				i++;

				log.info("Image id {} was successfully saved on path: {}!", savedImage.getId(), savedImage.getFilePath());
			} else {
				// download failed, skip next time
				failed++;

				var savedImage = imageRepository.save(image.toBuilder()
					.failedDownloadCount(image.getFailedDownloadCount() + 1)
					.build());

				brokenImages.add(savedImage);
				log.warn("Image id {}, url '{}' could not be downloaded! Failed {} times", savedImage.getId(), savedImage.getUrl(), image.getFailedDownloadCount());
			}

			if (i >= DOWNLOAD_COUNT || failed > FAILED_COUNT) {
				break;
			}
		}

		if (notDownloadedImages.size() > 0) {
			getLogSender().sendLogs(log, String.format("ImageService: scheduled download saved %d images on drive. Number of failed images: %d", i, brokenImages.size()));
		}
	}

	public boolean isFailed(Image image) {
		return !image.isDownloaded() && image.getFailedDownloadCount() > IMAGE_DOWNLOAD_MAX_FAIL_COUNT;
	}
}
