package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2MonthlyResult;

import java.util.List;

@Repository
public interface Splatoon2MonthlyResultRepository extends CrudRepository<Splatoon2MonthlyResult, Long> {
	@NotNull List<Splatoon2MonthlyResult> findAllByAccountId(long accountId);

	Splatoon2MonthlyResult findByAccountIdAndPeriodYearAndPeriodMonth(long accountId, int year, int month);
}
