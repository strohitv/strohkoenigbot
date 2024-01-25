package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr;

import lombok.*;

import javax.persistence.*;

@Entity(name = "splatoon_3_sr_grade")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
public class Splatoon3SrGrade {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ToString.Exclude
	private String apiId;

	private String name;
}
