package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonAbility;

import java.util.List;

@Repository
public interface SplatoonAbilityRepository extends CrudRepository<SplatoonAbility, Long> {
	@NotNull List<SplatoonAbility> findAll();

	SplatoonAbility findBySplatoonApiId(String splatoonApiId);
}
