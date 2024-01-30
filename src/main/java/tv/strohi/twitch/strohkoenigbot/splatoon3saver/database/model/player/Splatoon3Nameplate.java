package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Image;

import javax.persistence.*;

@Entity(name = "splatoon_3_nameplate")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3Nameplate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	@Column(name = "text_color_r")
	private Double textColorR;

	@Column(name = "text_color_g")
	private Double textColorG;

	@Column(name = "text_color_b")
	private Double textColorB;

	@Column(name = "text_color_a")
	private Double textColorA;

	@Builder.Default
	private Boolean owned = false;

	@Builder.Default
	private Boolean posted = false;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "image_id")
	private Image image;
}
