package tv.strohi.twitch.strohkoenigbot.splatoon3saver.stream;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.TwitchBotClient;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3SpecialWeaponWinStatsDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3WeaponStatsDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3XPowerDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3BadgeRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.service.ImageService;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.IconBadgeNames;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.StreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Stats;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.ScheduledService;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Log4j2
public class StatsSidebarFiller implements ScheduledService {
	private final LoadedSplatNetObjects loadedSplatNetObjects;

	private final TwitchBotClient twitchBotClient;
	private final ImageService imageService;

	private final S3WeaponStatsDownloader weaponStatsDownloader;
	private final S3SpecialWeaponWinStatsDownloader specialWeaponWinStatsDownloader;

	private final Splatoon3VsResultRepository resultRepository;
	private final Splatoon3BadgeRepository badgeRepository;

	private List<SpecialWinCount> specialWinStatsAtStreamStart = null;

	@Getter
	private StreamData streamData = StreamData.empty();

	private void run() {
		if (twitchBotClient.getWentLiveTime() == null) {
			streamData = StreamData.empty();
			specialWinStatsAtStreamStart = null;
			return;
		}

		final var allGamesInStream = resultRepository.findByPlayedTimeAfterOrderByPlayedTimeAsc(twitchBotClient.getWentLiveTime());

		if (allGamesInStream.isEmpty()) {
			streamData = StreamData.empty();
			specialWinStatsAtStreamStart = null;
			return;
		}

		if (specialWinStatsAtStreamStart == null) {
			specialWinStatsAtStreamStart = specialWeaponWinStatsDownloader.downloadSpecialWeaponStats(twitchBotClient.getWentLiveTime()).orElse(null);
		}

		final var lastGame = allGamesInStream.get(allGamesInStream.size() - 1);
		final var ownPlayer = getOwnPlayer(lastGame);

		// Team Stats
		final var ownTeam = lastGame.getTeams().stream()
			.filter(Splatoon3VsResultTeam::getIsMyTeam)
			.findFirst()
			.orElse(null);
		final var opp1 = lastGame.getTeams().stream()
			.filter(t -> !t.getIsMyTeam())
			.findFirst()
			.orElse(null);

		if (ownTeam == null || opp1 == null) {
			// draw, do nothing
			return;
		}

		final var opp2 = lastGame.getTeams().stream()
			.filter(t -> !t.equals(ownTeam) && !t.equals(opp1))
			.findFirst()
			.orElse(null);

		// Weapon Stats
		final var weaponStats = loadedSplatNetObjects.getWeapons().orElse(null);
		if (weaponStats == null) {
			return;
		}

		final var specialWinStats = loadedSplatNetObjects.getSpecialWinCounts();
		if (specialWinStats == null) {
			return;
		}

		// Game Stats
		final var ownUsedWeaponStats = Arrays.stream(weaponStats)
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStats == null) {
			return;
		}

		final var ownUsedWeaponStatsAtStart = loadedSplatNetObjects.getWeaponStatsAtStreamStart().stream()
			.filter(w -> Objects.equals(w.getId(), ownPlayer.getWeapon().getApiId()))
			.findFirst()
			.orElse(null);

		if (ownUsedWeaponStatsAtStart == null) {
			return;
		}

		final var ownWeaponExpAtStart = getWeaponExp(ownUsedWeaponStatsAtStart.getStats().getLevel(), ownUsedWeaponStatsAtStart.getStats().getExpToLevelUp());
		final var ownWeaponExpNow = getWeaponExp(ownUsedWeaponStats.getStats().getLevel(), ownUsedWeaponStats.getStats().getExpToLevelUp());

		final var expWeaponGain = ownWeaponExpNow - ownWeaponExpAtStart;
		final var ownWeaponExpGoal = getExpGoal(ownUsedWeaponStats.getStats().getLevel());

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
				.kills(Stream.of(Optional.ofNullable(ownPlayer.getKills()), Optional.ofNullable(ownPlayer.getAssists()))
					.flatMap(Optional::stream)
					.reduce((kills, assists) -> kills - assists)
					.orElse(null))
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
			var powerType = choosePowerType(lastGame);

			var xPowers = Optional.<S3XPowerDownloader.Powers>empty();
			Double currentXPowers = null;
			if (powerType == PowerType.X) {
				xPowers = loadedSplatNetObjects.getXPowers();

				if (xPowers.isEmpty()) {
					return;
				}

				currentXPowers = getXPowerForRule(lastGame.getRule(), xPowers.get());
			}

			var allGamesToSearchForPower = List.<Splatoon3VsResult>of();

			if (powerType == PowerType.X) {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getMode(), lastGame.getMode()) && Objects.equals(g.getRule(), lastGame.getRule()))
					.collect(Collectors.toList());
			} else if (powerType == PowerType.SERIES) {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getMode(), lastGame.getMode()))
					.filter(g -> Objects.equals(getOwnPlayer(g).getWeapon(), ownPlayer.getWeapon()))
					.collect(Collectors.toList());
			} else if (powerType == PowerType.OPEN || powerType == PowerType.CHALLENGE) {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getRotation(), lastGame.getRotation()))
					.collect(Collectors.toList());
			} else {
				allGamesToSearchForPower = allGamesInStream.stream()
					.filter(g -> Objects.equals(g.getMode(), lastGame.getMode()))
					.collect(Collectors.toList());
			}

			final var secondToLastGame = allGamesToSearchForPower.stream()
				.filter(g -> g.getId() < lastGame.getId())
				.max(Comparator.comparing(Splatoon3VsResult::getPlayedTime))
				.orElse(null);

			streamDataBuilder = streamDataBuilder
				.power_stats(StreamData.PowerStats.builder()
					.mode_image(getModeIconResourceUrl(lastGame.getMode()))
					.rule_image(getRuleImageUrl(powerType, IconBadgeNames.RULES, lastGame.getRule().getName(), ownPlayer.getWeapon().getName(), ownPlayer.getWeapon().getImage3D()))
					.power_current(powerType == PowerType.X
						? currentXPowers
						: lastGame.getPower())
					.power_change(powerType == PowerType.X
						? (currentXPowers != null && lastGame.getPower() != null) ? currentXPowers - lastGame.getPower() : null
						: (lastGame.getPower() != null && secondToLastGame != null && secondToLastGame.getPower() != null
						? lastGame.getPower() - secondToLastGame.getPower()
						: null))
					.power_max(
						powerType == PowerType.X
							? null // TODO this would be nice to have
							: (
							powerType == PowerType.SERIES
								? Optional.ofNullable(ownUsedWeaponStats.getStats()).map(Stats::getMaxWeaponPower).orElse(null)
								:
								allGamesToSearchForPower.stream()
									.map(Splatoon3VsResult::getPower)
									.filter(Objects::nonNull)
									.max(Double::compare)
									.orElse(null))
					)
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
	}

	private Splatoon3VsResultTeamPlayer getOwnPlayer(Splatoon3VsResult result) {
		return result.getTeams().stream()
			.flatMap(t -> t.getTeamPlayers().stream())
			.filter(Splatoon3VsResultTeamPlayer::getIsMyself)
			.findFirst()
			.orElseThrow();
	}

	private PowerType choosePowerType(Splatoon3VsResult lastGame) {
		var powerType = PowerType.UNKNOWN;

		switch (lastGame.getMode().getApiMode()) {
			case "X_MATCH":
				powerType = PowerType.X;
				break;
			case "BANKARA":
				if (lastGame.getMode().getApiModeDistinction().equals("CHALLENGE")) {
					powerType = PowerType.SERIES;
				} else {
					powerType = PowerType.OPEN;
				}
				break;
			case "LEAGUE":
				powerType = PowerType.CHALLENGE;
				break;
			default:
				break;
		}

		return powerType;
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
		if (mode.getId() == 1) {
			return "/img/regular-battle.svg";
		}

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

		if (mode.getId() == 6 || mode.getId() == 7) {
			return getBadgeIconResourceUrl(IconBadgeNames.SPLATFEST_TWO_TEAMS);
		}

		if (mode.getId() == 8) {
			return "/img/tricolor-turf-war.svg";
		}

		if (mode.getId() == 9) {
			return "/img/private-battle.svg";
		}

		return "";
	}

	private String getRuleImageUrl(PowerType powerType, IconBadgeNames icon, @Nullable String ruleName, @NonNull String mainWeaponName, Image alternativeImage) {
		if (powerType == PowerType.SERIES) {
			return getMainWeaponBadgeIconResourceUrl(mainWeaponName, alternativeImage);
		} else {
			return getBadgeIconResourceUrl(icon, ruleName);
		}
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon) {
		return getBadgeIconResourceUrl(icon, null, null);
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon, @Nullable String ruleName) {
		return getBadgeIconResourceUrl(icon, ruleName, null);
	}

	private String getBadgeIconResourceUrl(IconBadgeNames icon, @Nullable String ruleName, @Nullable String filter) {
		return icon.getBadgeNames().stream()
			.filter(bn -> filter == null || bn.contains(filter))
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

	private enum PowerType {
		OPEN,
		SERIES,
		X,
		CHALLENGE,
		UNKNOWN
	}

	@Override
	public List<ScheduleRequest> createScheduleRequests() {
		return List.of(ScheduleRequest.builder()
			.name("StatsSidebarFiller_run")
			.prioritized(true)
			.schedule(TickSchedule.getScheduleString(1))
			.runnable(this::run)
			.build());
	}

	@Override
	public List<ScheduleRequest> createSingleRunRequests() {
		return List.of();
	}
}
