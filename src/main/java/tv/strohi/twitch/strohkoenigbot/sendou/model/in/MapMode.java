package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
public class MapMode implements Serializable {
	private String mode;
	private Stage stage;
}
