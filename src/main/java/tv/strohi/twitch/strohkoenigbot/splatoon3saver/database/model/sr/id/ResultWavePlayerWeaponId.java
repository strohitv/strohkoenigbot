package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.sr.id;

import lombok.*;

import java.io.Serializable;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultWavePlayerWeaponId implements Serializable {
	private long resultId;
	private int waveNumber;
	private long playerId;
	private long weaponId;
}
