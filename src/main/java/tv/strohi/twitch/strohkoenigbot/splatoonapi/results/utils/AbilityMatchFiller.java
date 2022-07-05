package tv.strohi.twitch.strohkoenigbot.splatoonapi.results.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2AbilityMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2GearType;
import tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata.Splatoon2AbilityMatchRepository;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetGearSkill;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.model.SplatNetMatchResult;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.results.AbilityExporter;

import java.util.ArrayList;
import java.util.List;

@Component
public class AbilityMatchFiller {
	private AbilityExporter abilityExporter;

	@Autowired
	public void setAbilityExporter(AbilityExporter abilityExporter) {
		this.abilityExporter = abilityExporter;
	}

	private Splatoon2AbilityMatchRepository abilityMatchRepository;

	@Autowired
	public void setAbilityMatchRepository(Splatoon2AbilityMatchRepository abilityMatchRepository) {
		this.abilityMatchRepository = abilityMatchRepository;
	}

	public List<Splatoon2AbilityMatch> fill(Splatoon2Match match, SplatNetMatchResult singleResult) {
		List<Splatoon2AbilityMatch> abilitiesUsedInMatch = new ArrayList<>();

		abilitiesUsedInMatch.addAll(parseAbilities(
				singleResult.getPlayer_result().getPlayer().getHead_skills(),
				singleResult.getPlayer_result().getPlayer().getHead().getKind(),
				match.getId()));
		abilitiesUsedInMatch.addAll(parseAbilities(
				singleResult.getPlayer_result().getPlayer().getClothes_skills(),
				singleResult.getPlayer_result().getPlayer().getClothes().getKind(),
				match.getId()));
		abilitiesUsedInMatch.addAll(parseAbilities(
				singleResult.getPlayer_result().getPlayer().getShoes_skills(),
				singleResult.getPlayer_result().getPlayer().getShoes().getKind(),
				match.getId()));

		abilityMatchRepository.saveAll(abilitiesUsedInMatch);

		return abilitiesUsedInMatch;
	}

	private List<Splatoon2AbilityMatch> parseAbilities(SplatNetMatchResult.SplatNetPlayerResult.SplatNetPlayer.SplatNetGearSkills skills, String gearKind, long matchId) {
		List<Splatoon2AbilityMatch> abilitiesUsed = new ArrayList<>();

		abilitiesUsed.add(createAbilityMatch(0, skills.getMain(), gearKind, matchId));

		for (int i = 0; i < skills.getSubs().length; i++) {
			if (skills.getSubs()[i] != null) {
				abilitiesUsed.add(createAbilityMatch(i + 1, skills.getSubs()[i], gearKind, matchId));
			} else {
				System.out.println("this gear does not have 3 sub slots");
			}
		}

		return abilitiesUsed;
	}

	private Splatoon2AbilityMatch createAbilityMatch(int position, SplatNetGearSkill skill, String gearKind, long matchId) {
		Splatoon2AbilityMatch abilityUsed = new Splatoon2AbilityMatch();
		abilityUsed.setMatchId(matchId);
		abilityUsed.setAbilityId(abilityExporter.loadGear(skill).getId());
		abilityUsed.setKind(Splatoon2GearType.getGearTypeByKey(gearKind));
		abilityUsed.setGearPosition(position);

		return abilityUsed;
	}
}
