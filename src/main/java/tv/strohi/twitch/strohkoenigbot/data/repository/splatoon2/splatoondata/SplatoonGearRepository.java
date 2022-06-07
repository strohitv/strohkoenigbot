package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonGear;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.SplatoonGearType;

import java.util.List;

@Repository
public interface SplatoonGearRepository extends CrudRepository<SplatoonGear, Long> {
	@NotNull List<SplatoonGear> findAll();

	SplatoonGear findBySplatoonApiIdAndKind(String splatoonApiId, SplatoonGearType kind);
}
