package tv.strohi.twitch.strohkoenigbot.utils;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.awt.*;

@Component
@Getter
public class SplatoonMatchColorComponent {
	private boolean isAvailable = true;

	private final Color backgroundStandardColor = new Color(187, 187, 187);
	private final Color greenStandardColor = new Color(136, 255, 136);
	private final Color redStandardColor = new Color(255, 136, 136);

	private Color backgroundColor;
	private Color greenColor;
	private Color redColor;

	public SplatoonMatchColorComponent() {
		reset();
	}

	public void reset() {
		isAvailable = true;

		backgroundColor = backgroundStandardColor;
		greenColor = greenStandardColor;
		redColor = redStandardColor;
	}

	public void setColors(Color backgroundColor, Color greenColor, Color redColor) {
		if (isAvailable) {
			this.backgroundColor = backgroundColor;
			this.greenColor = greenColor;
			this.redColor = redColor;

			isAvailable = false;
		}
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public Color getGreenColor() {
		return greenColor;
	}

	public Color getRedColor() {
		return redColor;
	}
}
