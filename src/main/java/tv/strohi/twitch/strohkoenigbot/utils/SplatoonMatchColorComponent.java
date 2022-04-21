package tv.strohi.twitch.strohkoenigbot.utils;

import org.springframework.stereotype.Component;

import java.awt.*;

@Component
public class SplatoonMatchColorComponent {
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
		backgroundColor = backgroundStandardColor;
		greenColor = greenStandardColor;
		redColor = redStandardColor;
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		if (this.backgroundColor == backgroundStandardColor) {
			this.backgroundColor = backgroundColor;
		}
	}

	public Color getGreenColor() {
		return greenColor;
	}

	public void setGreenColor(Color greenColor) {
		if (this.greenColor == greenStandardColor) {
			this.greenColor = greenColor;
		}
	}

	public Color getRedColor() {
		return redColor;
	}

	public void setRedColor(Color redColor) {
		if (this.redColor == redStandardColor) {
			this.redColor = redColor;
		}
	}
}
