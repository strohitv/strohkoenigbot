//package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;
//
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import javax.persistence.*;
//
//@Entity(name = "splatoon_3_vs_result_award")
//@Cacheable(false)
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//public class Splatoon3VsResultAward {
//	@Id
//	@ManyToOne
//	@JoinColumn(name = "result_id", insertable = false, updatable = false)
//	private Splatoon3VsResult result;
//
//	@Id
//	@ManyToOne
//	@JoinColumn(name = "award_id", insertable = false, updatable = false)
//	private Splatoon3VsAward award;
//}
