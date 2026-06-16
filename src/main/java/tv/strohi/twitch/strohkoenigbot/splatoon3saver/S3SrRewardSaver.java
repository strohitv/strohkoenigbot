package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrModeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.sr.Splatoon3SrRotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.SrReward;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3SrRewardSaver {
	private final LogSender logSender;
	private final ExceptionLogger exceptionLogger;

	private final Splatoon3SrModeRepository srModeRepository;
	private final Splatoon3SrRotationRepository srRotationRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public void addSrRewards(String rewards) {
		try {
			var ref = new TypeReference<List<SrReward>>() {
			};

			var srRewards = objectMapper.readValue(rewards, ref);
			var allRotations = new ArrayList<>(srRotationRepository.findByEndTimeAfter(Instant.now()));

			for (int i = allRotations.size() - 1; i >= 0; i--) {
				var reward = srRewards.get(i);

				var modeName = reward.getDate().split("[()]")[1];
				var mode = srModeRepository.findByName(modeName)
					.orElse(null);

				if (mode == null) {
					logSender.queueLogs(log, "# ERROR: Did not find mode `%s`", modeName);
					continue;
				}

				var date = LocalDate.parse(reward.getDate().split("[ ()]")[0], DateTimeFormatter.ofPattern("M/d/yyyy"))
					.atStartOfDay()
					.atZone(ZoneId.systemDefault())
					.toInstant();

				var foundRotation = allRotations.stream()
					.filter(r -> r.getStartTime().isAfter(date) && r.getMode().equals(mode))
					.reduce((a, b) -> b)
					.orElse(null);

				if (foundRotation == null) {
					logSender.queueLogs(log, "# ERROR: Did not find rotation for startDate after `%s` and mode `%s`", date, modeName);
					continue;
				}

				allRotations.remove(foundRotation);

				srRotationRepository.save(foundRotation.toBuilder()
					.leanDate(reward.getDate())
					.leanMoney(reward.getMoney())
					.leanMoneyTicketSmall(reward.getMoney_ticket_small())
					.leanMoneyTicketBig(reward.getMoney_ticket_big())
					.leanSilverScales(reward.getSilver_scales())
					.leanGoldScales(reward.getGold_scales())
					.leanResultEntries(reward.getResults())
					.build());

				logSender.queueLogs(log, "# Added SR rewards to rotation with id `%d`\n```\n%s\n```", foundRotation.getId(), reward);
			}
		} catch (Exception e) {
			exceptionLogger.logExceptionAsAttachment(log, "Exception happened during Lean Sr Result upload", e);
		}
	}
}
