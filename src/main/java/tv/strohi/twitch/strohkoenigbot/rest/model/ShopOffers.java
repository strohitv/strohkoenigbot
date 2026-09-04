package tv.strohi.twitch.strohkoenigbot.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopOffers {
	private String day;
	private List<String> offers;
}
