package tv.strohi.twitch.strohkoenigbot.splatoonapi.results;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonMonthlyResult;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata.SplatoonMonthlyResultRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetXRankLeaderBoard;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;
import tv.strohi.twitch.strohkoenigbot.utils.DiscordChannelDecisionMaker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class PeaksExporter {
	private SplatoonMonthlyResultRepository monthlyResultRepository;

	@Autowired
	public void setMonthlyResultRepository(SplatoonMonthlyResultRepository monthlyResultRepository) {
		this.monthlyResultRepository = monthlyResultRepository;
	}

	private RequestSender splatoonPeaksLoader;

	@Autowired
	public void setSplatoonPeaksLoader(RequestSender splatoonPeaksLoader) {
		this.splatoonPeaksLoader = splatoonPeaksLoader;
	}

	private WeaponExporter weaponExporter;

	@Autowired
	public void setWeaponExporter(WeaponExporter weaponExporter) {
		this.weaponExporter = weaponExporter;
	}

	private DiscordBot discordBot;

	@Autowired
	public void setDiscordBot(DiscordBot discordBot) {
		this.discordBot = discordBot;
	}

	@Scheduled(initialDelay = 10000, fixedDelay = Integer.MAX_VALUE)
	public void reloadMonthlyResults() {
		List<SplatoonMonthlyResult> peaks = monthlyResultRepository.findAll();

		int year = 2018;
		int month = 5;

		while (Instant.now().isAfter(Instant.parse(String.format("%04d-%02d-01T01:00:00.00Z", year, month)))) {
			int finalMonth = month;
			int finalYear = year;
			if (peaks.stream().noneMatch(p -> p.getPeriodMonth() == finalMonth && p.getPeriodYear() == finalYear)) {
				SplatNetXRankLeaderBoard board = getLeaderBoard(year, month);

				SplatoonMonthlyResult splatoonMonthlyResult = new SplatoonMonthlyResult();

				splatoonMonthlyResult.setPeriodMonth(month);
				splatoonMonthlyResult.setPeriodYear(year);
				splatoonMonthlyResult.setStartTime(board.getSplat_zones().getStart_time());
				splatoonMonthlyResult.setEndTime(board.getSplat_zones().getEnd_time());

				if (board.getSplat_zones().getMy_ranking() != null) {
					splatoonMonthlyResult.setZonesCurrent(board.getSplat_zones().getMy_ranking().getX_power());
					splatoonMonthlyResult.setZonesPeak(board.getSplat_zones().getMy_ranking().getX_power());
					splatoonMonthlyResult.setZonesWeaponId(weaponExporter.loadWeapon(board.getSplat_zones().getMy_ranking().getWeapon()).getId());
				}

				if (board.getRainmaker().getMy_ranking() != null) {
					splatoonMonthlyResult.setRainmakerCurrent(board.getRainmaker().getMy_ranking().getX_power());
					splatoonMonthlyResult.setRainmakerPeak(board.getRainmaker().getMy_ranking().getX_power());
					splatoonMonthlyResult.setRainmakerWeaponId(weaponExporter.loadWeapon(board.getRainmaker().getMy_ranking().getWeapon()).getId());
				}

				if (board.getTower_control().getMy_ranking() != null) {
					splatoonMonthlyResult.setTowerCurrent(board.getTower_control().getMy_ranking().getX_power());
					splatoonMonthlyResult.setTowerPeak(board.getTower_control().getMy_ranking().getX_power());
					splatoonMonthlyResult.setTowerWeaponId(weaponExporter.loadWeapon(board.getTower_control().getMy_ranking().getWeapon()).getId());
				}

				if (board.getClam_blitz().getMy_ranking() != null) {
					splatoonMonthlyResult.setClamsCurrent(board.getClam_blitz().getMy_ranking().getX_power());
					splatoonMonthlyResult.setClamsPeak(board.getClam_blitz().getMy_ranking().getX_power());
					splatoonMonthlyResult.setClamsWeaponId(weaponExporter.loadWeapon(board.getClam_blitz().getMy_ranking().getWeapon()).getId());
				}

				splatoonMonthlyResult = monthlyResultRepository.save(splatoonMonthlyResult);

				discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
						String.format("New X Rank peak month **%d-%d** with id **%d** and peaks Zones: **%s**, Rainmaker: **%s**, Tower: **%s**, Clams: **%s** was stored into Database!",
								splatoonMonthlyResult.getPeriodYear(),
								splatoonMonthlyResult.getPeriodMonth(),
								splatoonMonthlyResult.getId(),
								splatoonMonthlyResult.getZonesPeak(),
								splatoonMonthlyResult.getRainmakerPeak(),
								splatoonMonthlyResult.getTowerPeak(),
								splatoonMonthlyResult.getClamsPeak()));
			}

			year = month < 12 ? year : year + 1;
			month = (month % 12) + 1;
		}

		peaks = monthlyResultRepository.findAll();

		SplatoonMonthlyResult march21result = peaks.stream().filter(p -> p.getPeriodMonth() == 3 && p.getPeriodYear() == 2021).findFirst().orElse(null);
		if (march21result != null && march21result.getRainmakerPeak() < 2251.5) {
			march21result.setRainmakerPeak(2251.5);
			monthlyResultRepository.save(march21result);
		}

		SplatoonMonthlyResult april21result = peaks.stream().filter(p -> p.getPeriodMonth() == 4 && p.getPeriodYear() == 2021).findFirst().orElse(null);
		if (april21result != null && april21result.getZonesPeak() < 2218.6) {
			april21result.setZonesPeak(2218.6);
			monthlyResultRepository.save(april21result);
		}

		SplatoonMonthlyResult august21result = peaks.stream().filter(p -> p.getPeriodMonth() == 8 && p.getPeriodYear() == 2021).findFirst().orElse(null);
		if (august21result != null && august21result.getTowerPeak() < 2124.1) {
			august21result.setTowerPeak(2124.1);
			monthlyResultRepository.save(august21result);
		}

		SplatoonMonthlyResult september21result = peaks.stream().filter(p -> p.getPeriodMonth() == 9 && p.getPeriodYear() == 2021).findFirst().orElse(null);
		if (september21result != null && september21result.getClamsPeak() < 2195.4) {
			september21result.setClamsPeak(2195.4);
			monthlyResultRepository.save(september21result);
		}
	}


	@Scheduled(cron = "0 0 5 1 * *")
//	@Scheduled(cron = "0 * * * * *")
	public void refreshPreviousMonth() {
		ZonedDateTime date = ZonedDateTime.now(ZoneId.systemDefault()).minus(5, ChronoUnit.DAYS);
		int year = date.getYear();
		int month = date.getMonthValue();

		SplatoonMonthlyResult result = monthlyResultRepository.findByPeriodYearAndPeriodMonth(year, month);
		SplatNetXRankLeaderBoard board = getLeaderBoard(year, month);

		boolean changed = false;

		if (board.getSplat_zones().getMy_ranking() != null) {
			result.setZonesCurrent(board.getSplat_zones().getMy_ranking().getX_power());
			result.setZonesWeaponId(weaponExporter.loadWeapon(board.getSplat_zones().getMy_ranking().getWeapon()).getId());
			changed = true;
		}

		if (board.getRainmaker().getMy_ranking() != null) {
			result.setRainmakerCurrent(board.getRainmaker().getMy_ranking().getX_power());
			result.setRainmakerWeaponId(weaponExporter.loadWeapon(board.getRainmaker().getMy_ranking().getWeapon()).getId());
			changed = true;
		}

		if (board.getTower_control().getMy_ranking() != null) {
			result.setTowerCurrent(board.getTower_control().getMy_ranking().getX_power());
			result.setTowerWeaponId(weaponExporter.loadWeapon(board.getTower_control().getMy_ranking().getWeapon()).getId());
			changed = true;
		}

		if (board.getClam_blitz().getMy_ranking() != null) {
			result.setClamsCurrent(board.getClam_blitz().getMy_ranking().getX_power());
			result.setClamsWeaponId(weaponExporter.loadWeapon(board.getClam_blitz().getMy_ranking().getWeapon()).getId());
			changed = true;
		}

		if (changed) {
			result = monthlyResultRepository.save(result);

			discordBot.sendServerMessageWithImages(DiscordChannelDecisionMaker.getDebugChannelName(),
					String.format("New X Rank month **%d-%d** with id **%d** was refreshed to final powers Zones: **%s**, Rainmaker: **%s**, Tower: **%s**, Clams: **%s** and has been stored into Database!",
							result.getPeriodYear(),
							result.getPeriodMonth(),
							result.getId(),
							result.getZonesPeak(),
							result.getRainmakerPeak(),
							result.getTowerPeak(),
							result.getClamsPeak()));
		}
	}

	public SplatNetXRankLeaderBoard getLeaderBoard(int year, int month) {
		int endYear = month < 12 ? year : year + 1;
		int endMonth = (month % 12) + 1;

		if (year == 2018 && month == 5) {
			month = 4;
		}

		String address = String.format("/api/x_power_ranking/%02d%02d01T00_%02d%02d01T00/summary", year - 2000, month, endYear - 2000, endMonth);

		return splatoonPeaksLoader.querySplatoonApi(address, SplatNetXRankLeaderBoard.class);
	}
}
