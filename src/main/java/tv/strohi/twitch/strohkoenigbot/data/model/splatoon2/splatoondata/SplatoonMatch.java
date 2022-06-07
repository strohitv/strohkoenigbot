package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonMatchResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonRule;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonMatch {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String battleNumber;
	private Integer splatnetBattleNumber;

	private Long startTime;
	private Integer elapsedTime;
	private Long endTime;

	private Long rotationId;

	private Long stageId;
	private SplatoonMode mode;
	private SplatoonRule rule;

	private String rank;
	private Double xPower;
	private Double xPowerEstimate;
	private Double xLobbyPower;

	private String leagueTag;
	private Double leaguePower;
	private Double leaguePowerMax;
	private Double leaguePowerEstimate;
	private Double leagueEnemyPower;

	private Long weaponId;
	private Integer turfGain;
	private Long turfTotal;

	private Integer kills;
	private Integer assists;
	private Integer deaths;
	private Integer specials;

	private Integer ownScore;
	private Double ownPercentage;
	private Integer enemyScore;
	private Double enemyPercentage;

	private SplatoonMatchResult matchResult;
	private Boolean isKo;

	private Long headgearId;
	private Long clothesId;
	private Long shoesId;

	private String jsonOverview;
	private String jsonMatch;

	public SplatNetMatchResult getMatchResultOverview() {
		return getSplatNetMatchResult(jsonOverview);
	}

	public void setMatchResultOverview(SplatNetMatchResult result) {
		if (result != null) {
			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(new JavaTimeModule());
			try {
				jsonOverview = mapper.writeValueAsString(result);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
	}

	public SplatNetMatchResult getMatchResultDetails() {
		return getSplatNetMatchResult(jsonMatch);
	}

	public void setMatchResultDetails(SplatNetMatchResult result) {
		if (result != null) {
			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(new JavaTimeModule());
			try {
				jsonMatch = mapper.writeValueAsString(result);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
	}

	@Nullable
	private SplatNetMatchResult getSplatNetMatchResult(String json) {
		SplatNetMatchResult result = null;

		if (json != null && !json.isBlank()) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				result = mapper.readValue(json, SplatNetMatchResult.class);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		return result;
	}
}
