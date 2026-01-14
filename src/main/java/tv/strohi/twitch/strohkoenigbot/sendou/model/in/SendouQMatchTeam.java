package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@ToString
public class SendouQMatchTeam implements Serializable {
	private Long id;
	private Integer score;
	private List<SendouQMatchPlayer> players;
}
