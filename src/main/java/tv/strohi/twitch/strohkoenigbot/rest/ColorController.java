package tv.strohi.twitch.strohkoenigbot.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tv.strohi.twitch.strohkoenigbot.rest.model.ColorBody;
import tv.strohi.twitch.strohkoenigbot.utils.SplatoonMatchColorComponent;

import java.awt.*;
import java.util.Arrays;

@RestController
@RequestMapping("colors")
public class ColorController {
	private SplatoonMatchColorComponent splatoonMatchColorComponent;

	@Autowired
	public void setSplatoonMatchColorComponent(SplatoonMatchColorComponent splatoonMatchColorComponent) {
		this.splatoonMatchColorComponent = splatoonMatchColorComponent;
	}

	@PostMapping
	public void setColors(@RequestBody ColorBody colors) {
		if (colors.getOwnTeamColor() == null
				|| colors.getOwnTeamColor().length < 3
				|| Arrays.stream(colors.getOwnTeamColor()).filter(c -> c < 0 || c > 255).count() > 0
				|| colors.getOtherTeamColor() == null
				|| colors.getOtherTeamColor().length < 3
				|| Arrays.stream(colors.getOtherTeamColor()).filter(c -> c < 0 || c > 255).count() > 0) {
			return;
		}

		Color ownTeamColor = new Color(colors.getOwnTeamColor()[0], colors.getOwnTeamColor()[1], colors.getOwnTeamColor()[2]);
		Color otherTeamColor = new Color(colors.getOtherTeamColor()[0], colors.getOtherTeamColor()[1], colors.getOtherTeamColor()[2]);

		splatoonMatchColorComponent.setBackgroundColor(ownTeamColor);
		splatoonMatchColorComponent.setGreenColor(ownTeamColor);
		splatoonMatchColorComponent.setRedColor(otherTeamColor);
	}
}
