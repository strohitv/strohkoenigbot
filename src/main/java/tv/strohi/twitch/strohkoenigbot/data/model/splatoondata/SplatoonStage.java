package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonStage {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String splatoonApiId;

	private String name;

	private String image;
}
