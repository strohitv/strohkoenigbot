package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.id;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpecialWeaponIdDay implements Serializable {
	private long specialWeaponId;
	private LocalDate statDay;
}
