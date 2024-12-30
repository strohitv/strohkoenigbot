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
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import javax.transaction.Transactional;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Log4j2
public class ImageService implements ScheduledService {
	private static final int DOWNLOAD_COUNT = 30;
	private static final int FAILED_COUNT = 50;
	private static final int IMAGE_DOWNLOAD_MAX_FAIL_COUNT = 5;

	private static final String SEPARATOR = FileSystems.getDefault().getSeparator();
	private static final String SEPARATOR_REGEX = Pattern.quote(FileSystems.getDefault().getSeparator());

	private final ImageRepository imageRepository;
	private final ResourcesDownloader resourcesDownloader;
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

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("ImageService_downloadSomeMissingImages")
			.schedule(TickSchedule.getScheduleString(60))
			.runnable(this::downloadSomeMissingImages)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of(ScheduleRequest.builder()
			.name("ImageService_fixImagePaths")
			.schedule(TickSchedule.getScheduleString(360))
			.runnable(this::fixImagePaths)
			.build());
	}

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
				var absolutePath = Paths.get(System.getProperty("user.dir"), path).toString();
				var relativePath = getRelativePath(absolutePath);

				var savedImage = imageRepository.save(image.toBuilder()
					.filePath(relativePath.orElse(absolutePath))
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

		if (!notDownloadedImages.isEmpty()) {
			getLogSender().sendLogs(log, String.format("### ImageService scheduled download result\n- **%d** successful\n- **%d** failed", i, brokenImages.size()));
		}
	}

	@Transactional
	public void fixImagePaths() {
		var allImagesWithWrongPath = imageRepository.findAll()
			.stream()
			.filter(image -> image.getFilePath() != null && !new File(image.getFilePath()).exists())
			.collect(Collectors.toCollection(LinkedList::new));

		var successfulCount = 0;
		var failedCount = 0;

		while (!allImagesWithWrongPath.isEmpty()) {
			var currentImage = allImagesWithWrongPath.removeFirst();

			var relativePath = getRelativePath(currentImage.getFilePath());
			if (relativePath.isPresent()) {
				imageRepository.save(currentImage.toBuilder().filePath(relativePath.get()).build());
				successfulCount++;
			} else {
				imageRepository.save(currentImage.toBuilder()
					.filePath(null)
					.downloaded(false)
					.failedDownloadCount(0)
					.build());
				failedCount++;
			}
		}

		if (successfulCount > 0 || failedCount > 0) {
			getLogSender().sendLogs(log, String.format("### ImageService fix filepath result\n- **%d** successful\n- **%d** failed", successfulCount, failedCount));
		}
	}

	private Optional<String> getRelativePath(String filePath) {
		var allDirectoryNames = Arrays.stream(filePath.split(SEPARATOR_REGEX))
			.collect(Collectors.toCollection(LinkedList::new));

		while (!allDirectoryNames.isEmpty()) {
			var builder = new StringBuilder(".")
				.append(SEPARATOR)
				.append(allDirectoryNames.removeFirst());
			allDirectoryNames.forEach(dir -> builder.append(SEPARATOR).append(dir));

			var file = new File(builder.toString());
			if (file.exists()) {
				return Optional.of(file.toString());
			}
		}

		return Optional.empty();
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
	public Optional<Image> ensureImageIsDownloaded(Image image) {
		if (image.isDownloaded()) {
			return Optional.of(image);
		}

		String imageLocationString = resourcesDownloader.ensureExistsLocally(image.getUrl().replace("\\u0026", "&"));
		String path = Paths.get(imageLocationString).toString();

//		logSender.sendLogs(log, String.format("Trying to download image: <%s>", path));

		if (!imageLocationString.startsWith("https://")) {
			var absolutePath = Paths.get(System.getProperty("user.dir"), path).toString();
			var relativePath = getRelativePath(absolutePath);

			var savedImage = imageRepository.save(image.toBuilder()
				.filePath(relativePath.orElse(absolutePath))
				.downloaded(true)
				.build());

			log.info("Image id {} was successfully saved on path: {}!", savedImage.getId(), savedImage.getFilePath());
//			logSender.sendLogs(log, String.format("Image id %d was successfully saved on path: `%s`!", savedImage.getId(), savedImage.getFilePath()));
			return Optional.of(savedImage);
		} else {
			// download failed, skip next time
			var savedImage = imageRepository.save(image.toBuilder()
				.failedDownloadCount(image.getFailedDownloadCount() + 1)
				.build());

			brokenImages.add(savedImage);
			log.warn("Image id {}, url '{}' could not be downloaded! Failed {} times", savedImage.getId(), savedImage.getUrl(), image.getFailedDownloadCount());
//			logSender.sendLogs(log, String.format("Image id %d, url <%s> could not be downloaded! Failed %d times", savedImage.getId(), savedImage.getUrl(), image.getFailedDownloadCount()));
			return Optional.empty();
		}
	}

	public boolean isFailed(Image image) {
		return !image.isDownloaded() && image.getFailedDownloadCount() > IMAGE_DOWNLOAD_MAX_FAIL_COUNT;
	}
}
