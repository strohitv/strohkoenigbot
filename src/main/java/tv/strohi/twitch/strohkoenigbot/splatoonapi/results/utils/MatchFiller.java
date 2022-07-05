package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Rotation;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Weapon;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2MatchResult;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2RotationRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.GearExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.WeaponExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.rotations.StagesExporter;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.RequestSender;

import java.util.Objects;

@Component
public class MatchFiller {
	private RequestSender splatoonResultsLoader;

	@Autowired
	public void setSplatoonResultsLoader(RequestSender splatoonResultsLoader) {
		this.splatoonResultsLoader = splatoonResultsLoader;
	}

	private Splatoon2RotationRepository rotationRepository;

	@Autowired
	public void setRotationRepository(Splatoon2RotationRepository rotationRepository) {
		this.rotationRepository = rotationRepository;
	}

	private WeaponExporter weaponExporter;

	@Autowired
	public void setWeaponExporter(WeaponExporter weaponExporter) {
		this.weaponExporter = weaponExporter;
	}

	private GearExporter gearExporter;

	@Autowired
	public void setGearExporter(GearExporter gearExporter) {
		this.gearExporter = gearExporter;
	}

	private StagesExporter stagesExporter;

	@Autowired
	public void setStagesExporter(StagesExporter stagesExporter) {
		this.stagesExporter = stagesExporter;
	}

	public Splatoon2Match fill(Account account, SplatNetMatchResult singleResult) {
		Splatoon2Weapon weapon = weaponExporter.loadWeapon(singleResult.getPlayer_result().getPlayer().getWeapon());

		Splatoon2Match match = new Splatoon2Match();
		match.setAccountId(account.getId());

		match.setBattleNumber(singleResult.getBattle_number());
		match.setSplatnetBattleNumber(singleResult.getBattleNumberAsInteger());

		match.setStartTime(singleResult.getStart_time());
		match.setElapsedTime(singleResult.getElapsed_time());
		match.setEndTime(singleResult.getStart_time() + singleResult.getElapsed_time());

		match.setStageId(stagesExporter.loadStage(singleResult.getStage()).getId());
		match.setMode(Splatoon2Mode.getModeByName(singleResult.getGame_mode().getKey()));
		match.setRule(Splatoon2Rule.getRuleByName(singleResult.getRule().getKey()));

		Splatoon2Rotation rotation
				= rotationRepository.findByStartTimeLessThanEqualAndEndTimeGreaterThanEqualAndMode(match.getStartTime(), match.getEndTime(), match.getMode());

		if (rotation != null
				&& (Objects.equals(rotation.getStageAId(), match.getStageId()) || Objects.equals(rotation.getStageBId(), match.getStageId()))) {
			match.setRotationId(rotation.getId());
		}

		if (singleResult.getUdemae() != null) {
			match.setRank(singleResult.getUdemae().getName());
		}

		match.setXPower(singleResult.getX_power());
		match.setXPowerEstimate(singleResult.getEstimate_gachi_power());
		match.setXLobbyPower(singleResult.getEstimate_x_power());

		match.setLeagueTag(singleResult.getTag_id());
		match.setLeaguePower(singleResult.getLeague_point());
		match.setLeaguePowerMax(singleResult.getMax_league_point());
		match.setLeaguePowerEstimate(singleResult.getMy_estimate_league_point());
		match.setLeagueEnemyPower(singleResult.getOther_estimate_league_point());

		match.setWeaponId(weapon.getId());
		match.setTurfGain(singleResult.getPlayer_result().getGame_paint_point());
		match.setTurfTotal(singleResult.getWeapon_paint_point());

		match.setKills(singleResult.getPlayer_result().getKill_count());
		match.setAssists(singleResult.getPlayer_result().getAssist_count());
		match.setDeaths(singleResult.getPlayer_result().getDeath_count());
		match.setSpecials(singleResult.getPlayer_result().getSpecial_count());

		match.setOwnScore(singleResult.getMy_team_count());
		match.setEnemyScore(singleResult.getOther_team_count());

		match.setOwnPercentage(singleResult.getMy_team_percentage());
		match.setEnemyPercentage(singleResult.getOther_team_percentage());

		match.setMatchResult(Splatoon2MatchResult.parseResult(singleResult.getMy_team_result().getKey()));
		match.setIsKo(singleResult.getMy_team_count() != null && singleResult.getOther_team_count() != null
				&& (singleResult.getMy_team_count() == 100 || singleResult.getOther_team_count() == 100));

		match.setHeadgearId(gearExporter.loadGear(singleResult.getPlayer_result().getPlayer().getHead()).getId());
		match.setClothesId(gearExporter.loadGear(singleResult.getPlayer_result().getPlayer().getClothes()).getId());
		match.setShoesId(gearExporter.loadGear(singleResult.getPlayer_result().getPlayer().getShoes()).getId());

		// this field is only set for turf war matches, else it's null
		match.setCurrentFlag(singleResult.getWin_meter());

		match.setMatchResultOverview(singleResult);
		match.setMatchResultDetails(null);

		if (account.getIsMainAccount()) {
			// difference: gear and results of other players are included
			SplatNetMatchResult loadedMatch
					= splatoonResultsLoader.querySplatoonApiForAccount(account, String.format("/api/results/%s", singleResult.getBattle_number()), SplatNetMatchResult.class);
			match.setMatchResultDetails(loadedMatch);
		}

		return match;
	}
}
