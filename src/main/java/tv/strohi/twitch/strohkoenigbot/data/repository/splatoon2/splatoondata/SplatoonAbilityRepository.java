package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Ability;

import java.util.List;

@Repository
public interface SplatoonAbilityRepository extends CrudRepository<Splatoon2Ability, Long> {
	@NotNull List<Splatoon2Ability> findAll();

	Splatoon2Ability findBySplatoonApiId(String splatoonApiId);
}
