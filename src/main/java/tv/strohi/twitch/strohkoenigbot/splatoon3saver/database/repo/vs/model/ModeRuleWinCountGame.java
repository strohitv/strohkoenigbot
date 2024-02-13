package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsMode;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRule;

import javax.persistence.Cacheable;

@Cacheable(false)
@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ModeRuleWinCountGame {
	private Splatoon3VsMode mode;
	private Splatoon3VsRule rule;

	private int winCount;

	public ModeRuleWinCountGame(Splatoon3VsMode mode, Splatoon3VsRule rule, long winCount) {
		this.mode = mode;
		this.rule = rule;
		this.winCount = Long.valueOf(winCount).intValue();
	}
}
