package tv.strohi.twitch.strohkoenigbot.utils;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Getter
public class SplatoonMatchColorComponent {
	private Instant blockedUntil = Instant.now();

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
		blockedUntil = Instant.now().plus(60, ChronoUnit.SECONDS);

		backgroundColor = backgroundStandardColor;
		greenColor = greenStandardColor;
		redColor = redStandardColor;
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		if (this.backgroundColor == backgroundStandardColor && Instant.now().isAfter(blockedUntil)) {
			this.backgroundColor = backgroundColor;
		}
	}

	public Color getGreenColor() {
		return greenColor;
	}

	public void setGreenColor(Color greenColor) {
		if (this.greenColor == greenStandardColor && Instant.now().isAfter(blockedUntil)) {
			this.greenColor = greenColor;
		}
	}

	public Color getRedColor() {
		return redColor;
	}

	public void setRedColor(Color redColor) {
		if (this.redColor == redStandardColor && Instant.now().isAfter(blockedUntil)) {
			this.redColor = redColor;
		}
	}
}
