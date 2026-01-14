package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
public class SendouQMatchPlayer implements Serializable {
	private Long userId;
	private SendouQRank rank;
}
