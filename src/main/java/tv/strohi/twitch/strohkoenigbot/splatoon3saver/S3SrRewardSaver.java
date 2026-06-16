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
				var srReward = srRewards.get(i);

				var modeName = srReward.getDate().split("[()]")[1];
				var mode = srModeRepository.findByName(modeName)
					.orElse(null);

				if (mode == null) {
					continue;
				}

				var date = LocalDate.parse(srReward.getDate().split("[ ()]")[0], DateTimeFormatter.ofPattern("M/d/yyyy"))
					.atStartOfDay()
					.atZone(ZoneId.systemDefault())
					.toInstant();

				var firstRotation = allRotations.stream()
					.filter(r -> r.getStartTime().isAfter(date) && r.getMode().equals(mode))
					.reduce((a, b) -> b)
					.orElse(null);

				if (firstRotation == null) {
					continue;
				}

				allRotations.remove(firstRotation);

				srRotationRepository.save(firstRotation.toBuilder()
					.leanDate(srReward.getDate())
					.leanMoney(srReward.getMoney())
					.leanMoneyTicketSmall(srReward.getMoney_ticket_small())
					.leanMoneyTicketBig(srReward.getMoney_ticket_big())
					.leanSilverScales(srReward.getSilver_scales())
					.leanGoldScales(srReward.getGold_scales())
					.leanResultEntries(srReward.getResults())
					.build());
			}
		} catch (Exception e) {
			exceptionLogger.logExceptionAsAttachment(log, "Exception happened during Lean Sr Result upload", e);
		}
	}
}
