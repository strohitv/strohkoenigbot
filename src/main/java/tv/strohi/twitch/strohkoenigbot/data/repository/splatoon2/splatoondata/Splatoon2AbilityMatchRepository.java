package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2AbilityMatch;

import java.util.List;

@Repository
public interface Splatoon2AbilityMatchRepository extends CrudRepository<Splatoon2AbilityMatch, Long> {
	@NotNull List<Splatoon2AbilityMatch> findAll();
	@NotNull List<Splatoon2AbilityMatch> findAllByMatchId(long matchId);
}
