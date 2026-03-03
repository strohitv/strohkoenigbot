package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.persistence.Cacheable;

@Cacheable(false)
@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StageWinStatsWithRule {
	private String mapName;
	private String modeName;
	private String ruleName;
	private long winCount;
	private long defeatCount;
}
