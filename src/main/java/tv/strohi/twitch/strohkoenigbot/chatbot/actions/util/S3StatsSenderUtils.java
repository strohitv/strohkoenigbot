package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeam;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

import javax.transaction.Transactional;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3StatsSenderUtils {
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private final Splatoon3VsResultRepository vsResultRepository;
	private final ExceptionLogger exceptionLogger;

	@Transactional
	public void respondWithGameStats(ActionArgs args, long matchDecidingNumber) {
		if (matchDecidingNumber <= 0) {
			var offset = (int) matchDecidingNumber * -1;
			var pageable = Pageable.ofSize(offset + 1);
			var latestGameId = vsResultRepository.findLatestGameIds(pageable).stream().reduce((a, b) -> b);

			if (latestGameId.isEmpty()) {
				args.getReplySender().send("**ERROR** I could not find the latest game at all!");
				return;
			}

			matchDecidingNumber = latestGameId.get();
		}

		var gameResult = vsResultRepository.findById(matchDecidingNumber);

		if (gameResult.isEmpty()) {
			args.getReplySender().send("**ERROR** I could not find a game with id `%d`", matchDecidingNumber);
			return;
		}

		try {
			var game = gameResult.get();
			var builder = new StringBuilder("**Game ID ").append(game.getId()).append("**\n")
				.append("Played at: ").append(formatter.format(game.getPlayedTime())).append("\n")
				.append("Duration: ").append(game.getDuration()).append(" seconds\n")
				.append("Result: ").append(escape(game.getOwnJudgement())).append("\n\n")
				.append("Mode: ").append(game.getMode().getName()).append("\n")
				.append("Rule: ").append(game.getRule().getName()).append("\n")
				.append("Stage: ").append(game.getStage().getName()).append("\n\n\n");

			for (var team : game.getTeams().stream().sorted(Comparator.comparingInt(Splatoon3VsResultTeam::getTeamOrder)).collect(Collectors.toList())) {
				builder.append("**Team ").append(team.getTeamOrder()).append("**\n");

				if (team.getJudgement() != null) {
					builder.append("Result: ").append(team.getJudgement()).append("\n");
				}

				if (team.getPaintRatio() != null) {
					builder.append("Paint Ratio: ").append(String.format("%.1f", team.getPaintRatio() * 100)).append("%\n");
				}

				if (team.getScore() != null) {
					builder.append("Score: ").append(team.getScore()).append("\n");
				}

				builder.append("\n");

				for (var player : team.getTeamPlayers()) {
					builder.append("__Team Player ID ").append(player.getPlayerId()).append("__\n");
					builder.append(player.getIsMyself() ? "Name: **" : "Name: ").append(escape(player.getName())).append("#").append(player.getNameId()).append(player.getIsMyself() ? "** - " : " - ");
					builder.append("Title: ").append(player.getTitle()).append("\n");

					if (player.getHasCrown() != null && player.getHasCrown()) {
						builder.append("**Top 500 player**\n");
					}

					builder.append("Weapon: ").append(player.getWeapon().getName()).append(" (").append(player.getWeapon().getSubWeapon().getName()).append(", ").append(player.getWeapon().getSpecialWeapon().getName()).append(")\n");
					builder.append("Stats: ").append(formatPlayerStats(player)).append("\n\n");
				}

				builder.append("\n");
			}

			args.getReplySender().send(builder.toString().trim());
		} catch (Exception ex) {
			args.getReplySender().send("Failed to load the game!");
			exceptionLogger.logException(log, ex);
		}
	}

	private String formatPlayerStats(Splatoon3VsResultTeamPlayer player) {
		if (player.getUltraSignalAttempts() == null) {
			return String.format("%sp - **%sk** - %sa - **%sd** - %ss",
				player.getPaint() != null ? player.getPaint() : "--",
				player.getKills() != null && player.getAssists() != null ? player.getKills() - player.getAssists() : "--",
				player.getAssists() != null ? player.getAssists() : "--",
				player.getDeaths() != null ? player.getDeaths() : "--",
				player.getSpecials() != null ? player.getSpecials() : "--");
		} else {
			return String.format("%sp - **%sk** - %sa - **%sd** - %ss - %s signal grabs",
				player.getPaint() != null ? player.getPaint() : "--",
				player.getKills() != null && player.getAssists() != null ? player.getKills() - player.getAssists() : "--",
				player.getAssists() != null ? player.getAssists() : "--",
				player.getDeaths() != null ? player.getDeaths() : "--",
				player.getSpecials() != null ? player.getSpecials() : "--",
				player.getUltraSignalAttempts());
		}
	}

	private String escape(String text) {
		return text.replace("_", "\\_").replace("*", "\\*");
	}
}
