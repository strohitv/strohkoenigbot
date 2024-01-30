package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id;

import lombok.*;

import java.io.Serializable;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultIdWave implements Serializable {
	private long resultId;
	private int waveNumber;
}
