package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonAbilityMatch;

import java.util.List;

@Repository
public interface SplatoonAbilityMatchRepository extends CrudRepository<SplatoonAbilityMatch, Long> {
	@NotNull List<SplatoonAbilityMatch> findAll();
	@NotNull List<SplatoonAbilityMatch> findAllByMatchId(long matchId);
}
