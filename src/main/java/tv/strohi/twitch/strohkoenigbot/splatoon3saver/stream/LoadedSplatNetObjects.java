package tv.strohi.twitch.strohkoenigbot.splatoon3saver.stream;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.S3XPowerDownloader;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model.SpecialWinCount;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.model.FullscreenStreamData;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.BattleResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.HistoryResult;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;

import java.util.*;

@Component
@Getter
@Setter
@ToString
public class LoadedSplatNetObjects {
	private Optional<Weapon[]> weapons = Optional.empty();
	private Optional<S3XPowerDownloader.Powers> xPowers = Optional.empty();
	private Optional<HistoryResult> parsedHistory = Optional.empty();
	private Optional<BattleResult> parsedLastGame = Optional.empty();
	private Optional<Long> headGameCount = Optional.empty();
	private Optional<Long> shirtGameCount = Optional.empty();
	private Optional<Long> shoesGameCount = Optional.empty();
	private final List<SpecialWinCount> specialWinCounts = new ArrayList<>();
	private final List<FullscreenStreamData.MapData> stageWins = new ArrayList<>();
	private final Map<Long, Long> playerMatchupNumbers = new HashMap<>();
	private final List<Weapon> weaponStatsAtStreamStart = new ArrayList<>();

	public boolean containsEmptyField() {
		return weapons.isEmpty()
			|| xPowers.isEmpty()
			|| parsedHistory.isEmpty()
			|| parsedLastGame.isEmpty()
			|| headGameCount.isEmpty()
			|| shirtGameCount.isEmpty()
			|| shoesGameCount.isEmpty()
			|| specialWinCounts.isEmpty()
			|| stageWins.isEmpty()
			|| playerMatchupNumbers.isEmpty()
			|| weaponStatsAtStreamStart.isEmpty();
	}

	public boolean isReady() {
		return !containsEmptyField();
	}

	public void reset() {
		weapons = Optional.empty();
		xPowers = Optional.empty();
		parsedHistory = Optional.empty();
		parsedLastGame = Optional.empty();
		headGameCount = Optional.empty();
		shirtGameCount = Optional.empty();
		shoesGameCount = Optional.empty();
		specialWinCounts.clear();
		stageWins.clear();
		playerMatchupNumbers.clear();
		weaponStatsAtStreamStart.clear();
	}
}
