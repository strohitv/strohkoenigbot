package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype.ActionArgs;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeam;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeamPlayer;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsWeapon;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.player.Splatoon3PlayerRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.Splatoon3VsResultTeamPlayerRepository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.utils.ExceptionLogger;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Log4j2
public class S3StatsSenderUtils {
	private final Splatoon3VsResultRepository vsResultRepository;
	private final Splatoon3VsResultTeamPlayerRepository vsResultTeamPlayerRepository;
	private final Splatoon3PlayerRepository playerRepository;
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
				.append("Played at: <t:").append(game.getPlayedTime().getEpochSecond()).append(":f>\n")
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

	@Transactional
	public void respondWithPlayerStats(ActionArgs args, long playerId) {
		var searchedPlayer = playerRepository.findById(playerId);

		if (searchedPlayer.isEmpty()) {
			args.getReplySender().send("**ERROR** I could not find a player with this id");
			return;
		}

		var allPlayerGameStats = vsResultTeamPlayerRepository.findByPlayerId(playerId);

		Instant firstMet = null;
		Instant lastMet = null;

		var lastUsedName = "";
		Splatoon3VsWeapon lastUsedWeapon = null;
		Splatoon3VsWeapon ownLastUsedWeapon = null;

		Map<String, Integer> names = new HashMap<>();
		Map<Splatoon3VsWeapon, Integer> weapons = new HashMap<>();

		Map<Splatoon3VsWeapon, Integer> ownUsedWeapons = new HashMap<>();

		var sameTeamWins = 0L;
		var sameTeamDraws = 0L;
		var sameTeamDefeats = 0L;
		var sameTeamUnknown = 0L;
		var opposingTeamWins = 0L;
		var opposingTeamDraws = 0L;
		var opposingTeamDefeats = 0L;
		var opposingTeamUnknown = 0L;

		// todo game modes and game rules!

		for (var gameStats : allPlayerGameStats) {
			var playerTeam = gameStats.getTeam();
			var game = playerTeam.getResult();

			var ownTeam = game.getTeams().stream().filter(t -> t.getIsMyTeam() || t.getTeamPlayers().stream().anyMatch(Splatoon3VsResultTeamPlayer::getIsMyself)).findFirst().orElseThrow();
			var ownGameStats = ownTeam.getTeamPlayers().stream().filter(Splatoon3VsResultTeamPlayer::getIsMyself).findFirst().orElseThrow();

			var nameInGame = String.format("%s#%s", gameStats.getName(), gameStats.getNameId());
			names.putIfAbsent(nameInGame, 0);
			names.put(nameInGame, names.get(nameInGame) + 1);

			var weapon = gameStats.getWeapon();
			weapons.putIfAbsent(weapon, 0);
			weapons.put(weapon, weapons.get(weapon) + 1);

			var ownWeapon = ownGameStats.getWeapon();
			ownUsedWeapons.putIfAbsent(ownWeapon, 0);
			ownUsedWeapons.put(ownWeapon, ownUsedWeapons.get(ownWeapon) + 1);

			if (firstMet == null || firstMet.isAfter(game.getPlayedTime())) {
				firstMet = game.getPlayedTime();
			}

			if (lastMet == null || lastMet.isBefore(game.getPlayedTime())) {
				lastMet = game.getPlayedTime();
				lastUsedName = nameInGame;
				lastUsedWeapon = gameStats.getWeapon();
				ownLastUsedWeapon = ownGameStats.getWeapon();
			}

			boolean sameTeam = ownTeam == playerTeam || // normal modes
				(ownTeam.getTeamPlayers().size() == 2 && playerTeam.getTeamPlayers().size() == 2); // splatfest

			if (sameTeam) {
				if (ownTeam.getJudgement() != null) {
					if (ownTeam.getJudgement().equalsIgnoreCase("WIN")) {
						sameTeamWins++;
					} else if (ownTeam.getJudgement().equalsIgnoreCase("DRAW")) {
						sameTeamDraws++;
					} else {
						sameTeamDefeats++;
					}
				} else {
					sameTeamUnknown++;
				}
			} else {
				if (ownTeam.getJudgement() != null) {
					if (ownTeam.getJudgement().equalsIgnoreCase("WIN")) {
						opposingTeamWins++;
					} else if (ownTeam.getJudgement().equalsIgnoreCase("DRAW")) {
						opposingTeamDraws++;
					} else {
						opposingTeamDefeats++;
					}
				} else if (playerTeam.getJudgement() != null) {
					if (playerTeam.getJudgement().equalsIgnoreCase("WIN")) {
						opposingTeamDefeats++;
					} else if (playerTeam.getJudgement().equalsIgnoreCase("DRAW")) {
						opposingTeamDraws++;
					} else {
						opposingTeamWins++;
					}
				} else {
					opposingTeamUnknown++;
				}
			}
		}

		var builder = new StringBuilder("**Player ID ").append(playerId).append("**\n")
			.append("First met at: <t:").append(firstMet != null ? firstMet.getEpochSecond() : "0").append(":f>\n")
			.append("Last met at: <t:").append(lastMet != null ? lastMet.getEpochSecond() : "0").append(":f>\n\n");

		builder.append("**Names**\nHe last played with the name **").append(escape(lastUsedName)).append("**\n__All names__\n");
		for (var name : names.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).collect(Collectors.toList())) {
			builder.append(escape(name.getKey())).append(": ").append(name.getValue()).append(" times");

			if (name.getKey().equals(lastUsedName)) {
				builder.append(" (last used name)");
			}

			builder.append("\n");
		}

		builder.append("\n");

		builder.append("**Weapons**\n");
		if (lastUsedWeapon != null) {
			builder.append("Last time, they played with the weapon **").append(lastUsedWeapon.getName()).append("** (").append(lastUsedWeapon.getSubWeapon().getName()).append(", ").append(lastUsedWeapon.getSpecialWeapon().getName()).append(")\n");
		}
		builder.append("__All weapons__\n");
		for (var weapon : weapons.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).collect(Collectors.toList())) {
			builder.append(weapon.getKey().getName()).append(": ").append(weapon.getValue()).append(" times");

			if (weapon.getKey().equals(lastUsedWeapon)) {
				builder.append(" (last used weapon)");
			}

			builder.append("\n");
		}

		builder.append("\n");

		builder.append("**Own Weapons**\n");
		if (lastUsedWeapon != null) {
			builder.append("Last time, I played with the weapon **").append(ownLastUsedWeapon.getName()).append("** (").append(ownLastUsedWeapon.getSubWeapon().getName()).append(", ").append(ownLastUsedWeapon.getSpecialWeapon().getName()).append(")\n");
		}
		builder.append("__All weapons__\n");
		for (var weapon : ownUsedWeapons.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).collect(Collectors.toList())) {
			builder.append(weapon.getKey().getName()).append(": ").append(weapon.getValue()).append(" times");

			if (weapon.getKey().equals(lastUsedWeapon)) {
				builder.append(" (last used weapon)");
			}

			builder.append("\n");
		}

		builder.append("\n");

		builder.append("**Results**\n");
		builder.append("This player was in your lobbies ").append(allPlayerGameStats.size()).append(" times\n")
			.append("Total **wins: ").append(sameTeamWins + opposingTeamWins).append("** (").append(sameTeamWins).append(" same team + ").append(opposingTeamWins).append(" opposing team)\n")
			.append("Total **draws: ").append(sameTeamDraws + opposingTeamDraws).append("** (").append(sameTeamDraws).append(" same team + ").append(opposingTeamDraws).append(" opposing team)\n")
			.append("Total **defeats: ").append(sameTeamDefeats + opposingTeamDefeats).append("** (").append(sameTeamDefeats).append(" same team + ").append(opposingTeamDefeats).append(" opposing team)\n");

		if (sameTeamUnknown + opposingTeamUnknown > 0) {
			builder.append("Total unknown results: ").append(sameTeamUnknown + opposingTeamUnknown).append(" (").append(sameTeamUnknown).append(" same team + ").append(opposingTeamUnknown).append(" opposing team)\n");
		}

		args.getReplySender().send(builder.toString().trim());
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
