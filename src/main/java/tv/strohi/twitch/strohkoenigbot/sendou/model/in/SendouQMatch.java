package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@ToString
public class SendouQMatch implements Serializable {
	private SendouQMatchTeam teamAlpha;
	private SendouQMatchTeam teamBravo;
	private List<MapResult> mapList;
}
