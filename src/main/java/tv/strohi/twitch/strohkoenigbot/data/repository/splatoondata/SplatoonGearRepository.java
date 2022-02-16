package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonGear;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonGearType;

import java.util.List;

@Repository
public interface SplatoonGearRepository extends CrudRepository<SplatoonGear, Long> {
	@NotNull List<SplatoonGear> findAll();

	SplatoonGear findBySplatoonApiIdAndKind(String splatoonApiId, SplatoonGearType kind);
}
