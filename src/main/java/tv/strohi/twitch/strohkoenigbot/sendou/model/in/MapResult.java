package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@ToString
public class MapResult implements Serializable {
	private MapMode map;
	private Object source;
	private Long winnerTeamId;
	private List<Long> participatedUserIds;
	private List<Integer> points;

	public boolean isSourceNumber() {
		return source instanceof Long;
	}

	public Long getSourceAsLong() {
		return (Long) source;
	}

	public String getSourceAsString() {
		return (String) source;
	}
}
