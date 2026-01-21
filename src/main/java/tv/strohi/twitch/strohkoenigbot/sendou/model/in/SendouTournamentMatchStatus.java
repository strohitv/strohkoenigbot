package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

public enum SendouTournamentMatchStatus {
	/** The two matches leading to this one are not completed yet. */
	LOCKED,

	/** One participant is ready and waiting for the other one. */
	WAITING,

	/** Both participants are ready to start. */
	READY,

	/** The match is running. */
	RUNNING,

	/** The match is completed. */
	COMPLETED
}
