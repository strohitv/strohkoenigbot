package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2MonthlyResult;

import java.util.List;

@Repository
public interface SplatoonMonthlyResultRepository extends CrudRepository<Splatoon2MonthlyResult, Long> {
	@NotNull List<Splatoon2MonthlyResult> findAll();

	Splatoon2MonthlyResult findByPeriodYearAndPeriodMonth(int year, int month);
}
