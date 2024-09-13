package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Log4j2
@RequiredArgsConstructor
public class S3GameExporter {
	private final ExceptionLogger exceptionLogger;
	private final DiscordBot discordBot;

	private final ImageService imageService;
	private final Splatoon3VsResultRepository resultRepository;

	@Transactional
	public void exportGames(long userId, int top, int skip) {
		var pageable = Pageable.ofSize(skip + top);

		var ids = resultRepository.findLatestGameIds(pageable);
		var games = ids.stream()
			.map(resultRepository::findById)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.skip(skip)
			.collect(Collectors.toList());

		try (var baos = new ByteArrayOutputStream();
			 var zos = new ZipOutputStream(baos)) {
			for (var game : games) {
				var zonedGameTime = game.getPlayedTime().atZone(ZoneOffset.UTC);

				var name = String.format("%d%02d%02dT%02d%02d%02dZ.json",
					zonedGameTime.getYear(),
					zonedGameTime.getMonthValue(),
					zonedGameTime.getDayOfMonth(),
					zonedGameTime.getHour(),
					zonedGameTime.getMinute(),
					zonedGameTime.getSecond());
				ZipEntry entry = new ZipEntry(name);

				zos.putNextEntry(entry);
				zos.write(imageService.restoreJson(game.getShortenedJson()).getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}

			zos.finish();

			try (var bais = new ByteArrayInputStream(baos.toByteArray())) {
				discordBot.sendPrivateMessageWithAttachment(
					userId,
					String.format("Here are the exported games for top `%d` and skip `%d`", top, skip),
					"export.zip",
					bais);
			}
		} catch (Exception ex) {
			exceptionLogger.logException(log, ex);
		}
	}
}
