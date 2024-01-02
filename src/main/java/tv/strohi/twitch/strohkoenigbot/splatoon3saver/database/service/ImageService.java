package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.ImageRepository;
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
public class ImageService {
	private final ImageRepository imageRepository;
	private final ResourcesDownloader resourcesDownloader;
	private final SchedulingService schedulingService;

	private final Pattern imageUrlPattern = Pattern.compile("https://api\\.lp1\\.av5ja\\.srv\\.nintendo\\.net[^\"]+");
	private final Pattern imagePlaceholderPattern = Pattern.compile("<<<[0-9]+>>>");

	@PostConstruct
	public void registerSchedule() {
		schedulingService.register("ShortenedImageService_download10Images", TickSchedule.getScheduleString(360), this::downloadUpTo10MissingImages);
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
	public void downloadUpTo10MissingImages() {
		var notDownloadedImages = imageRepository.findByFilePathNull().stream()
			.filter(i -> !brokenImages.contains(i))
			.collect(Collectors.toList());

		int i = 0;

		for (var image : notDownloadedImages) {
			String imageLocationString = resourcesDownloader.ensureExistsLocally(image.getUrl());
			String path = Paths.get(imageLocationString).toString();

			if (!imageLocationString.startsWith("https://")) {
				image.setFilePath(Paths.get(System.getProperty("user.dir"), path).toString());
				imageRepository.save(image);
				i++;
			} else {
				// download failed, skip next time
				brokenImages.add(image);
			}

			if (i >= 10) {
				break;
			}
		}
	}
}
