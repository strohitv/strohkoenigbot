package tv.strohi.twitch.strohkoenigbot.splatoon3saver;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3BadgeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.IconBadgeNames;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.StreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.LogSender;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3StreamDataService implements ScheduledService {
	private final TwitchBotClient twitchBotClient;
	private final LogSender logSender;

	private final S3SpecialWeaponWinStatsDownloader specialWeaponWinStatsDownloader;
	private final S3WeaponStatsDownloader weaponStatsDownloader;
	private final S3XPowerDownloader xPowerDownloader;

	private final Splatoon3BadgeRepository badgeRepository;
	private final Splatoon3VsResultRepository resultRepository;
	private final ImageService imageService;

	@Getter
	private StreamData streamData = StreamData.empty();

	private Instant newestFoundGameStartTime = null;
	private List<SpecialWinCount> specialWinStatsAtStreamStart = null;
	private Weapon[] weaponStatsAtStreamStart = null;

	private void refreshStreamData() {
		logSender.sendLogs(log, "S3StreamDataService: running refresh method");

		if (twitchBotClient.getWentLiveTime() == null) {
			streamData = StreamData.empty();
			newestFoundGameStartTime = null;
			specialWinStatsAtStreamStart = null;
			weaponStatsAtStreamStart = null;
			logSender.sendLogs(log, "S3StreamDataService: channel is offline");
			return;
		}

		if (weaponStatsAtStreamStart == null) {
			logSender.sendLogs(log, "S3StreamDataService: weaponStatsAtStreamStart refresh");
			weaponStatsAtStreamStart = weaponStatsDownloader.downloadWeaponStats().orElse(null);
		}

		if (specialWinStatsAtStreamStart == null) {
			logSender.sendLogs(log, "S3StreamDataService: specialWinStatsAtStreamStart refresh");
			specialWinStatsAtStreamStart = specialWeaponWinStatsDownloader.downloadSpecialWeaponStats().orElse(null);
		}

		final var allGamesInStream = resultRepository.findByPlayedTimeAfterOrderByPlayedTimeAsc(twitchBotClient.getWentLiveTime());

		if (allGamesInStream.isEmpty()) {
			streamData = StreamData.empty();
			newestFoundGameStartTime = null;
			specialWinStatsAtStreamStart = null;
			weaponStatsAtStreamStart = null;
			logSender.sendLogs(log, "S3StreamDataService: no games found");
			return;
		}

		final var lastGame = allGamesInStream.get(allGamesInStream.size() - 1);

		if (newestFoundGameStartTime != null && newestFoundGameStartTime.equals(lastGame.getPlayedTime())) {
			// nothing to refresh
			logSender.sendLogs(log, "S3StreamDataService: nothing to refresh");
			return;
		}

		newestFoundGameStartTime = lastGame.getPlayedTime();

		// Weapon Stats
		final var weaponStats = weaponStatsDownloader.downloadWeaponStats().orElse(null);
		if (weaponStats == null) {
			logSender.sendLogs(log, "S3StreamDataService: weaponStats null");
			return;
		}

		final var specialWinStats = specialWeaponWinStatsDownloader.downloadSpecialWeaponStats().orElse(null);
		if (specialWinStats == null) {
			logSender.sendLogs(log, "S3StreamDataService: specialWinStats null");
			return;
		}

		// Game Stats
		final var ownPlayer = lastGame.getTeams().stream()
			.flatMap(t -> t.getTeamPlayers().stream())
			.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
			.findFirst()
			.orElseThrow();

		final var ownUsedWeaponStats = Arrays.stream(weaponStats)
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStats == null) {
			logSender.sendLogs(log, "S3StreamDataService: ownUsedWeaponStats null");
			return;
		}

		final var ownUsedWeaponStatsAtStart = Arrays.stream(weaponStatsAtStreamStart)
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStatsAtStart == null) {
			logSender.sendLogs(log, "S3StreamDataService: ownUsedWeaponStatsAtStart null");
			return;
		}

		final var ownWeaponExpAtStart = getWeaponExp(ownUsedWeaponStatsAtStart.getStats().getLevel(), ownUsedWeaponStatsAtStart.getStats().getExpToLevelUp());
		final var ownWeaponExpNow = getWeaponExp(ownUsedWeaponStats.getStats().getLevel(), ownUsedWeaponStats.getStats().getExpToLevelUp());

		final var expWeaponGain = ownWeaponExpNow - ownWeaponExpAtStart;
		final var ownWeaponExpGoal = getExpGoal(ownUsedWeaponStatsAtStart.getStats().getLevel());

		var alreadyOwnedExpRatio = ownWeaponExpAtStart * 100.0 / ownWeaponExpGoal;
		var earnedExpStreamRatio = expWeaponGain * 100.0 / ownWeaponExpGoal;
		var remainingExpRatio = 100.0 - alreadyOwnedExpRatio - earnedExpStreamRatio;

		if (ownUsedWeaponStats.getStats().getLevel() >= 5) {
			var previousLevelExp = getExpGoal(ownUsedWeaponStats.getStats().getLevel() - 1);
			alreadyOwnedExpRatio = (ownWeaponExpAtStart - previousLevelExp) * 100.0 / (ownWeaponExpGoal - previousLevelExp);
			earnedExpStreamRatio = expWeaponGain * 100.0 / (ownWeaponExpGoal - previousLevelExp);
			remainingExpRatio = 100.0 - alreadyOwnedExpRatio - earnedExpStreamRatio;
		}

		final var ownUsedSpecialWeaponStats = specialWinStats.stream()
			.filter(s -> Objects.equals(s.getSpecialWeapon(), ownPlayer.getWeapon().getSpecialWeapon()))
			.findFirst()
			.orElse(null);

		if (ownUsedSpecialWeaponStats == null) {
			logSender.sendLogs(log, "S3StreamDataService: ownUsedSpecialWeaponStats null");
			return;
		}

		final var ownSpecialWeaponWinsAtStreamStart = specialWinStatsAtStreamStart.stream()
			.filter(s -> Objects.equals(s.getSpecialWeapon(), ownPlayer.getWeapon().getSpecialWeapon()))
			.findFirst()
			.map(SpecialWinCount::getWinCount)
			.orElse(0);

		// Stream Stats
		final var totalWins = allGamesInStream.stream()
			.filter(g -> g.getOwnJudgement().equalsIgnoreCase("WIN"))
			.count();
		final var totalDefeats = allGamesInStream.stream()
			.filter(g -> g.getOwnJudgement().equalsIgnoreCase("LOSE"))
			.count();
		final var winRatio = totalWins * 100.0 / (Math.max(totalWins + totalDefeats, 1));
		final var defeatRatio = 100.0 - winRatio;

		// Team Stats
		final var ownTeam = lastGame.getTeams().stream()
			.filter(Splatoon3VsResultTeam::getIsMyTeam)
			.findFirst()
			.orElseThrow();
		final var opp1 = lastGame.getTeams().stream()
			.filter(t -> !t.getIsMyTeam())
			.findFirst()
			.orElseThrow();
		final var opp2 = lastGame.getTeams().stream()
			.filter(t -> !t.equals(ownTeam) && !t.equals(opp1))
			.findFirst()
			.orElse(null);
		final var totalPointsSum = ownTeam.getScore() != null
			? ownTeam.getScore() + opp1.getScore() + (opp2 != null ? opp2.getScore() : 0)
			: ownTeam.getPaintRatio() * 100 + opp1.getPaintRatio() * 100 + (opp2 != null ? opp2.getPaintRatio() * 100 : 0);

		var streamDataBuilder = StreamData.prepare()
			.weapon_info(
				StreamData.WeaponInfo.builder()
					.image(getMainWeaponBadgeIconResourceUrl(ownPlayer.getWeapon().getName(), ownPlayer.getWeapon().getImage3D()))
					.sub_weapon_image(getResourceUrl(ownPlayer.getWeapon().getSubWeapon().getImage()))
					.special_weapon_image(getResourceUrl(ownPlayer.getWeapon().getSpecialWeapon().getImage()))
					.wins(ownUsedWeaponStats.getStats().getWin())
					.stars(ownUsedWeaponStats.getStats().getLevel())
					.exp_start(ownWeaponExpAtStart)
					.exp_change(expWeaponGain)
					.exp_now(ownWeaponExpNow)
					.exp_start_ratio(alreadyOwnedExpRatio)
					.exp_change_ratio(earnedExpStreamRatio)
					.exp_left_ratio(remainingExpRatio)
					.build())
			.abilities_info(StreamData.AbilitiesInfo.builder()
				.head(StreamData.PieceAbilitiesInfo.builder()
					.main_image(getResourceUrl(ownPlayer.getHeadGearMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getHeadGearSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getHeadGearSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.shirt(StreamData.PieceAbilitiesInfo.builder()
					.main_image(getResourceUrl(ownPlayer.getClothingMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getClothingSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getClothingSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.shoes(StreamData.PieceAbilitiesInfo.builder()
					.main_image(getResourceUrl(ownPlayer.getShoesMainAbility().getImage()))
					.sub_1_image(getResourceUrl(ownPlayer.getShoesSecondaryAbility1().getImage()))
					.sub_2_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility2()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.sub_3_image(Optional.ofNullable(ownPlayer.getShoesSecondaryAbility3()).map(Splatoon3VsAbility::getImage).map(this::getResourceUrl).orElse(null))
					.build())
				.build())
			.team_stats(StreamData.MatchStats.builder()
				.own_team(buildTeamResult(ownTeam, totalPointsSum))
				.opp_1(buildTeamResult(opp1, totalPointsSum))
				.opp_2(Optional.ofNullable(opp2)
					.map(o -> buildTeamResult(o, totalPointsSum))
					.orElse(null))
				.build())
			.stream_stats(StreamData.StreamStats.builder()
				.wins(totalWins)
				.defeats(totalDefeats)
				.win_ratio(winRatio)
				.defeat_ratio(defeatRatio)
				.build())
			.game_stats(StreamData.GameStats.builder()
				.kills(ownPlayer.getKills())
				.deaths(ownPlayer.getDeaths())
				.assists(ownPlayer.getAssists())
				.specials(ownPlayer.getSpecials())
				.paint(ownPlayer.getPaint())
				.build())
			.special_stats(StreamData.SpecialStats.builder()
				.image(getSpecialWeaponBadgeIconResourceUrl(ownPlayer.getWeapon().getSpecialWeapon().getName()))
				.wins(ownUsedSpecialWeaponStats.getWinCount())
				.gained(ownUsedSpecialWeaponStats.getWinCount() - ownSpecialWeaponWinsAtStreamStart)
				.build())
			.power_stats(null);

		if (lastGame.isHasPower()) {
			final var isX = lastGame.getMode().getApiMode().equals("X_MATCH");

			var xPowers = Optional.<S3XPowerDownloader.Powers>empty();
			Double currentXPowers = null;
			if (isX) {
				xPowers = xPowerDownloader.downloadXPowers();

				if (xPowers.isEmpty()) {
					logSender.sendLogs(log, "## Error\n- S3StreamDataService could not load X Powers!");
					return;
				}

				currentXPowers = getXPowerForRule(lastGame.getRule(), xPowers.get());
			}

			final var allGamesFromRotation = allGamesInStream.stream()
				.filter(g -> Objects.equals(g.getRotation(), lastGame.getRotation()))
				.collect(Collectors.toList());
			final var secondToLastGame = allGamesFromRotation.stream()
				.filter(g -> g.getId() < lastGame.getId())
				.max(Comparator.comparing(Splatoon3VsResult::getPlayedTime))
				.orElse(null);


			streamDataBuilder = streamDataBuilder
				.power_stats(StreamData.PowerStats.builder()
					.mode_image(getModeIconResourceUrl(lastGame.getMode()))
					.rule_image(getBadgeIconResourceUrl(IconBadgeNames.RULES, lastGame.getRule().getName()))
					.power_current(isX
						? currentXPowers
						: lastGame.getPower())
					.power_change(isX
						? (currentXPowers != null && lastGame.getPower() != null) ? currentXPowers - lastGame.getPower() : null
						: (lastGame.getPower() != null && secondToLastGame != null && secondToLastGame.getPower() != null
						? lastGame.getPower() - secondToLastGame.getPower()
						: null))
					.power_max(isX
						? null
						: allGamesFromRotation.stream()
						.map(Splatoon3VsResult::getPower)
						.filter(Objects::nonNull)
						.max(Double::compare)
						.orElse(null))
					.build());
		}

		var result = streamDataBuilder.build();
		if (100 > (result.getTeam_stats().getOwn_team().getResult_ratio()
			+ result.getTeam_stats().getOpp_1().getResult_ratio()
			+ (result.getTeam_stats().getOpp_2() != null ? result.getTeam_stats().getOpp_2().getResult_ratio() : 0))) {
			var difference = 100 - (result.getTeam_stats().getOwn_team().getResult_ratio()
				+ result.getTeam_stats().getOpp_1().getResult_ratio()
				+ (result.getTeam_stats().getOpp_2() != null ? result.getTeam_stats().getOpp_2().getResult_ratio() : 0));

			result.getTeam_stats().getOwn_team().setResult_ratio(result.getTeam_stats().getOwn_team().getResult_ratio() + difference);
		}

		streamData = result;
		logSender.sendLogs(log, "S3StreamDataService: streamData refreshed");
	}

	private StreamData.TeamResult buildTeamResult(Splatoon3VsResultTeam team, double totalPointsSum) {
		if (team == null) {
			return null;
		}

		var points = team.getScore() != null
			? team.getScore()
			: team.getPaintRatio() * 100;

		return StreamData.TeamResult.builder()
			.color(String.format("#%02x%02x%02x%02x", (int) (255 * team.getInkColorR()), (int) (255 * team.getInkColorG()), (int) (255 * team.getInkColorB()), (int) (255 * team.getInkColorA())))
			.result(team.getJudgement() != null ? team.getJudgement() : "DRAW")
			.result_points(team.getScore() != null ? String.format("%d p", team.getScore()) : String.format("%.1f %%", team.getPaintRatio() * 100))
			.result_ratio((long) (points * 100 / totalPointsSum))
			.build();
	}

	private Double getXPowerForRule(Splatoon3VsRule rule, S3XPowerDownloader.Powers powers) {
		if (rule.getId() == 2) {
			return powers.getZones();
		}
		if (rule.getId() == 3) {
			return powers.getTower();
		}
		if (rule.getId() == 4) {
			return powers.getClams();
		}
		if (rule.getId() == 5) {
			return powers.getRainmaker();
		}

		return null;
	}

	private String getModeIconResourceUrl(Splatoon3VsMode mode) {
		if (mode.getId() == 2) {
			return getBadgeIconResourceUrl(IconBadgeNames.ANARCHY_SERIES);
		}

		if (mode.getId() == 3) {
			return getBadgeIconResourceUrl(IconBadgeNames.ANARCHY_OPEN);
		}

		if (mode.getId() == 4) {
			return getBadgeIconResourceUrl(IconBadgeNames.X_BATTLE);
		}

		if (mode.getId() == 5) {
			return getBadgeIconResourceUrl(IconBadgeNames.CHALLENGE);
		}

		if (mode.getId() == 7) {
			return getBadgeIconResourceUrl(IconBadgeNames.SPLATFEST);
		}

		return "";
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon) {
		return getBadgeIconResourceUrl(icon, null);
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon, @Nullable String ruleName) {
		return icon.getBadgeNames().stream()
			.filter(bn -> ruleName == null || bn.contains(ruleName))
			.map(badgeRepository::findByDescription)
			.filter(Optional::isPresent)
			.map(b -> getResourceUrl(b.get().getImage()))
			.filter(url -> !url.isBlank())
			.findFirst()
			.orElse("");
	}

	private String getMainWeaponBadgeIconResourceUrl(@NonNull String mainWeaponName, Image alternativeImage) {
		return IconBadgeNames.MAIN.getBadgeNames().stream()
			.map(bn -> String.format(bn, mainWeaponName))
			.map(badgeRepository::findByDescription)
			.filter(Optional::isPresent)
			.map(b -> getResourceUrl(b.get().getImage()))
			.filter(url -> !url.isBlank())
			.findFirst()
			.orElse(getResourceUrl(alternativeImage));
	}

	private String getSpecialWeaponBadgeIconResourceUrl(@NonNull String specialName) {
		return IconBadgeNames.SPECIAL.getBadgeNames().stream()
			.map(bn -> String.format(bn, specialName))
			.map(badgeRepository::findByDescription)
			.filter(Optional::isPresent)
			.map(b -> getResourceUrl(b.get().getImage()))
			.filter(url -> !url.isBlank())
			.findFirst()
			.orElse("");
	}

	private String getResourceUrl(Image image) {
		imageService.ensureImageIsDownloaded(image);

		return Optional.ofNullable(image.getFilePath())
			.map(url -> url.replace("./resources/prod/", "/splatnet3/"))
			.orElse("");
	}

	private int getExpGoal(Integer level) {
		var expGoal = 0;

		switch (level) {
			case 1: {
				expGoal = 25_000;
				break;
			}
			case 2: {
				expGoal = 60_000;
				break;
			}
			case 3: {
				expGoal = 160_000;
				break;
			}
			case 4: {
				expGoal = 1_160_000;
				break;
			}
			case 5: {
				expGoal = 2_000_000;
				break;
			}
			case 6: {
				expGoal = 3_000_000;
				break;
			}
			case 7: {
				expGoal = 4_000_000;
				break;
			}
			case 8: {
				expGoal = 5_000_000;
				break;
			}
			case 9:
			case 10: {
				expGoal = 6_000_000;
				break;
			}
			case 0:
			default: {
				expGoal = 5_000;
				break;
			}
		}

		return expGoal;
	}

	private int getWeaponExp(Integer level, Integer expToLevelUp) {
		var currentExp = 0;

		switch (level) {
			case 1: {
				currentExp = 25_000 - expToLevelUp;
				break;
			}
			case 2: {
				currentExp = 60_000 - expToLevelUp;
				break;
			}
			case 3: {
				currentExp = 160_000 - expToLevelUp;
				break;
			}
			case 4: {
				currentExp = 1_160_000 - expToLevelUp;
				break;
			}
			case 5: {
				currentExp = 2_000_000 - expToLevelUp;
				break;
			}
			case 6: {
				currentExp = 3_000_000 - expToLevelUp;
				break;
			}
			case 7: {
				currentExp = 4_000_000 - expToLevelUp;
				break;
			}
			case 8: {
				currentExp = 5_000_000 - expToLevelUp;
				break;
			}
			case 9: {
				currentExp = 6_000_000 - expToLevelUp;
				break;
			}
			case 10: {
				currentExp = 6_000_000;
				break;
			}
			case 0:
			default: {
				currentExp = 5_000 - expToLevelUp;
				break;
			}
		}

		return currentExp;
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("S3StreamDataService_refreshStreamData")
			.schedule(TickSchedule.getScheduleString(13))
			.runnable(this::refreshStreamData)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}
