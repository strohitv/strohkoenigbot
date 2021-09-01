package tv.strohi.twitch.strohkoenigbot.model;

public class NormalizeResult {
	private final String original;
	private String normalized;

	private int removedCharacters;

	public NormalizeResult(String original) {
		this.original = original;
	}

	public String getOriginal() {
		return original;
	}

	public String getNormalized() {
		return normalized;
	}

	public void setNormalized(String normalized) {
		this.normalized = normalized;
	}

	public int getRemovedCharacters() {
		return removedCharacters;
	}

	public void increaseRemovedCharacters() {
		removedCharacters++;
	}
}
