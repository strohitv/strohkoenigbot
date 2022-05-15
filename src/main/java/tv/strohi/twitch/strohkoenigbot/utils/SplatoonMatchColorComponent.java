package tv.strohi.twitch.strohkoenigbot.utils;

import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SplatoonMatchColorComponent {
	private boolean isAvailable = true;
	private Instant isAvailableBlocker = Instant.now();

	private final Color backgroundStandardColor = new Color(187, 187, 187);
	private final Color greenStandardColor = new Color(136, 255, 136);
	private final Color redStandardColor = new Color(255, 136, 136);

	private Color backgroundColor;
	private Color greenColor;
	private Color redColor;

	public SplatoonMatchColorComponent() {
		reset();
	}

	public boolean isAvailable() {
		if (!isAvailable && Instant.now().isAfter(isAvailableBlocker)) {
			isAvailable = true;
		}

		return isAvailable;
	}

	public void reset() {
		isAvailableBlocker = Instant.now().plus(30, ChronoUnit.SECONDS);

		backgroundColor = backgroundStandardColor;
		greenColor = greenStandardColor;
		redColor = redStandardColor;
	}

	public void setColors(Color backgroundColor, Color greenColor, Color redColor) {
		if (isAvailable) {
			this.backgroundColor = backgroundColor;
			this.greenColor = greenColor;
			this.redColor = redColor;

			isAvailableBlocker = Instant.now().plus(10, ChronoUnit.DAYS);
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
